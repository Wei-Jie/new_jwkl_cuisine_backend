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
     * 後台管理：更新訂單狀態 (待確認 -> 已接單 -> 已出貨 -> 已結單)
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
     * 公開免驗證：訂單對帳單查詢 (安全遮蔽顧客敏感資訊)
     */
    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<?> getReceiptDetail(@PathVariable String orderId) {
        try {
            Optional<Order> orderOpt = orderRepository.findByOrderId(orderId.trim());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                List<OrderItem> details = orderItemRepository.findByOrderId(order.getOrderId());
                
                // 安全遮蔽敏感個資，防止洩漏
                Map<String, Object> safeOrder = new HashMap<>();
                safeOrder.put("orderId", order.getOrderId());
                safeOrder.put("orderDate", order.getOrderDate());
                safeOrder.put("customerName", order.getCustomerName());
                safeOrder.put("amount", order.getAmount());
                safeOrder.put("status", order.getStatus());
                safeOrder.put("deliveryDate", order.getDeliveryDate());
                safeOrder.put("paymentStatus", order.getPaymentStatus());
                safeOrder.put("paymentDate", order.getPaymentDate());
                safeOrder.put("notes", order.getNotes());
                
                // 手機遮蔽中間三碼
                String rawPhone = order.getPhone();
                if (rawPhone != null && rawPhone.length() >= 7) {
                    safeOrder.put("phone", rawPhone.substring(0, 4) + "***" + rawPhone.substring(rawPhone.length() - 3));
                } else {
                    safeOrder.put("phone", "未留");
                }
                
                // 社交平台與Email遮蔽
                safeOrder.put("instagram", maskSocial(order.getInstagram()));
                safeOrder.put("lineId", maskSocial(order.getLineId()));
                safeOrder.put("facebook", maskSocial(order.getFacebook()));
                safeOrder.put("email", maskEmail(order.getEmail()));
                
                Map<String, Object> response = new HashMap<>();
                response.put("order", safeOrder);
                response.put("details", details);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private String maskSocial(String val) {
        if (val == null || val.trim().isEmpty()) return "";
        String s = val.trim();
        if (s.length() <= 3) return "***";
        return s.substring(0, 2) + "***" + s.substring(s.length() - 1);
    }

    private String maskEmail(String val) {
        if (val == null || val.trim().isEmpty()) return "";
        String email = val.trim();
        int atIdx = email.indexOf("@");
        if (atIdx <= 1) return "***";
        return email.substring(0, 2) + "***" + email.substring(atIdx);
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
        boolean isOldDelivered = "已出貨".equals(oldStatus) || "已結單".equals(oldStatus);
        boolean isNewDelivered = "已出貨".equals(newStatus) || "已結單".equals(newStatus);
        
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

    @GetMapping("/debug-network")
    public ResponseEntity<?> debugNetwork() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 取得外網 IP
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(4))
                .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://ifconfig.me/ip"))
                .timeout(java.time.Duration.ofSeconds(4))
                .build();
            try {
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                result.put("outbound_ip", response.body().trim());
            } catch (Exception e) {
                result.put("outbound_ip_error", e.getClass().getName() + ": " + e.getMessage());
            }

            // 2. 測試 TCP 連線與 SSL 握手到 smtp.gmail.com:465
            try {
                javax.net.ssl.SSLSocketFactory sslsocketfactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                try (javax.net.ssl.SSLSocket sslsocket = (javax.net.ssl.SSLSocket) sslsocketfactory.createSocket()) {
                    sslsocket.connect(new java.net.InetSocketAddress("smtp.gmail.com", 465), 4000);
                    sslsocket.setSoTimeout(4000);
                    sslsocket.startHandshake();
                    result.put("smtp_gmail_465_ssl", "SUCCESS");
                }
            } catch (Exception e) {
                result.put("smtp_gmail_465_ssl_error", e.getClass().getName() + ": " + e.getMessage());
            }

            // 3. 測試 HTTPS GET 連線到 api.line.me:443
            try {
                java.net.http.HttpRequest lineRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.line.me/"))
                    .timeout(java.time.Duration.ofSeconds(4))
                    .build();
                java.net.http.HttpResponse<String> response = client.send(lineRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                result.put("api_line_https", "SUCCESS (" + response.statusCode() + ")");
            } catch (Exception e) {
                result.put("api_line_https_error", e.getClass().getName() + ": " + e.getMessage());
            }
            
        } catch (Exception e) {
            result.put("global_error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
