package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Order;
import com.jwkl.cuisine.entity.OrderItem;
import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.repository.OrderItemRepository;
import com.jwkl.cuisine.repository.OrderRepository;
import com.jwkl.cuisine.repository.MenuRepository;
import com.jwkl.cuisine.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuRepository menuRepository;

    /**
     * 顧客線上提交預約訂單 (極速購物車下單)
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderService.OrderRequest request, HttpServletRequest servletRequest) {
        try {
            String ip = getClientIp(servletRequest);
            Order savedOrder = orderService.submitOrder(request, ip);
            
            // 下單成功，回傳訂單號供前端展示
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("order_id", savedOrder.getOrderId());
            response.put("amount", savedOrder.getAmount());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            // 起訂金額等防呆校驗攔截
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"系統繁忙，請稍後再試！\"}");
        }
    }

    /**
     * 顧客訂單進度追蹤
     */
    @GetMapping("/track")
    public ResponseEntity<?> trackOrder(@RequestParam String phone, @RequestParam String orderId) {
        Optional<Order> orderOpt = orderService.trackOrder(phone.trim(), orderId.trim());
        
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            List<OrderItem> details = orderItemRepository.findByOrderId(order.getOrderId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("order", order);
            response.put("details", details);
            return ResponseEntity.ok(response);
        }
        
        return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"查無此訂單，請確認手機與訂單編號是否正確！\"}");
    }

    /**
     * 後台管理：獲取所有訂單
     */
    @GetMapping("/all")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    /**
     * 後台管理：條件過濾獲取訂單列表 (起迄日期與狀態篩選)
     */
    @GetMapping("/search")
    public ResponseEntity<List<Order>> searchOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        String queryStatus = (status == null || status.trim().isEmpty() || "全部".equals(status)) ? null : status.trim();
        String queryStart = (startDate == null || startDate.trim().isEmpty()) ? null : startDate.trim().replace("-", "/");
        String queryEnd = (endDate == null || endDate.trim().isEmpty()) ? null : endDate.trim().replace("-", "/");
        
        List<Order> filtered = orderRepository.findOrdersByFilters(queryStatus, queryStart, queryEnd);
        return ResponseEntity.ok(filtered);
    }

    /**
     * 後台管理：更新整筆訂單主檔資訊 (供編輯 Modal 對接使用)
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<?> updateOrder(@PathVariable String orderId, @RequestBody Order updatedOrder) {
        try {
            Order saved = orderService.updateOrder(orderId, updatedOrder);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台管理：更新訂單狀態 (待確認 -> 已接單 -> 已出貨 -> 已完成)
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderId, @RequestParam String status) {
        try {
            orderService.updateOrderStatus(orderId, status);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"訂單狀態更新成功！\"}");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台管理：更新收款狀態 (未付款 -> 已付款)
     */
    @PutMapping("/{orderId}/payment")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable String orderId, 
            @RequestParam String paymentStatus,
            @RequestParam(required = false) String paymentDate) {
        
        Optional<Order> orderOpt = orderRepository.findByOrderId(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setPaymentStatus(paymentStatus);
            if (paymentDate != null) {
                order.setPaymentDate(paymentDate);
            }
            orderRepository.save(order);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"收款狀態更新成功！\"}");
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 後台管理：品項排單查詢 (三表聯合 Native Query)
     */
    @GetMapping("/items/by-product")
    public ResponseEntity<?> getItemsByProduct(@RequestParam String productName) {
        try {
            List<java.util.Map<String, Object>> list = orderItemRepository.findScheduleMgmtByItem(productName.trim());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台管理：批次更新排單項目製作狀態 (待製作 -> 製作中 -> 已完成)
     * 支援悲觀鎖高併發控制與庫存智慧扣減/退回
     */
    @PutMapping("/items/batch-status")
    public ResponseEntity<?> batchUpdateItemStatus(@RequestBody BatchStatusUpdateRequest request) {
        try {
            if (request.getIds() == null || request.getIds().isEmpty() || request.getStatus() == null) {
                return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"參數無效\"}");
            }
            orderService.batchUpdateItemStatus(request.getIds(), request.getStatus());
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"批次狀態更新成功！\"}");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @lombok.Data
    public static class BatchStatusUpdateRequest {
        private List<Integer> ids;
        private String status;
    }

    /**
     * 後台管理：獲取所有訂單明細 (供營收利潤與熱門商品排行榜分析使用)
     */
    @GetMapping("/items/all")
    public ResponseEntity<List<OrderItem>> getAllOrderItems() {
        return ResponseEntity.ok(orderItemRepository.findAll());
    }

    /**
     * 後台管理：批量保存/更新訂單的品項明細 (包含新增折抵項目與調整單項狀態)
     * 支援 Undo-Redo 自動化庫存配銷校正與悲觀鎖硬限制防呆
     */
    @PutMapping("/{orderId}/items")
    public ResponseEntity<?> updateOrderItems(@PathVariable String orderId, @RequestBody List<OrderItem> items) {
        try {
            orderService.updateOrderItems(orderId, items);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"訂單明細更新成功！\"}");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 輔助方法：當主訂單出貨/退回狀態時，連動實體總庫存 (all_stock)
     */
    private void adjustPhysicalStockOnStatusChange(String orderId, String oldStatus, String newStatus) {
        boolean isOldDelivered = "已出貨".equals(oldStatus) || "已完成".equals(oldStatus);
        boolean isNewDelivered = "已出貨".equals(newStatus) || "已完成".equals(newStatus);
        
        if (isOldDelivered == isNewDelivered) {
            return; // 狀態未發生「出貨與否」的跨邊界變更，不異動總庫存
        }
        
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            // 💡 關鍵修正：跳過折扣品項即可，不論明細狀態為何，只要出貨就必須扣減實體總庫存
            if ("PROD_DISCOUNT".equals(item.getProductId())) {
                continue;
            }
            
            Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(item.getProductId()); // 悲觀寫入鎖
            if (menuOpt.isPresent()) {
                Menu menu = menuOpt.get();
                if (menu.getIsStockManaged()) {
                    if (isNewDelivered) {
                        // 出貨：從實體總庫存 (all_stock) 扣除
                        menu.setStock(menu.getStock() - item.getQty());
                    } else {
                        // 退回出貨：將數量加回實體總庫存 (all_stock)
                        menu.setStock(menu.getStock() + item.getQty());
                    }
                    menuRepository.save(menu);
                }
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
