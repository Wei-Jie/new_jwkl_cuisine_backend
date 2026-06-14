package com.jwkl.cuisine.service;

import com.jwkl.cuisine.entity.Order;
import com.jwkl.cuisine.entity.OrderItem;
import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.entity.SystemConfig;
import com.jwkl.cuisine.entity.SystemLog;
import com.jwkl.cuisine.repository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private LineNotifyService lineNotifyService;

    @Autowired
    private EmailService emailService;

    /**
     * DTO 用於接收顧客前端提交的訂單資料
     */
    @Data
    public static class OrderRequest {
        private String customer_name;
        private String phone;
        private String instagram;
        private String line_id;
        private String facebook;
        private String email;
        private String delivery_date;
        private String notes;
        private BigDecimal amount;
        private List<OrderItemRequest> items;
    }

    @Data
    public static class OrderItemRequest {
        private String product_id;
        private Integer qty;
        private BigDecimal product_amt;
    }

    /**
     * 顧客線上提交預約訂單 (併發安全與事務強一致性保障)
     */
    @Transactional
    public Order submitOrder(OrderRequest request, String ipAddress) {
        // 起訂金額防呆已應管理員要求完全取消

        // 2. 【核心高階方案】使用 Supabase 原子 Sequence 併發安全地生成順編流水號 S000001
        Long seqVal = orderRepository.getNextOrderIdSeqValue();
        String orderId = String.format("S%06d", seqVal); // 生成 S000001, S000002...

        // 取得當天日期
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // 3. 儲存訂單主檔 (預約單狀態預設為 '待確認')
        Order order = new Order();
        order.setOrderId(orderId);
        order.setOrderDate(today);
        order.setCustomerName(request.getCustomer_name());
        order.setPhone(request.getPhone());
        order.setInstagram(request.getInstagram());
        order.setLineId(request.getLine_id());
        order.setFacebook(request.getFacebook());
        order.setAmount(request.getAmount());
        order.setStatus("待確認");
        order.setDeliveryDate(request.getDelivery_date());
        order.setNotes(request.getNotes());
        order.setEmail(request.getEmail());
        
        Order savedOrder = orderRepository.save(order);

        // 4. 儲存訂單明細品項 (JPA 關聯寫入)
        java.util.List<OrderItem> savedDetails = new java.util.ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            OrderItem detail = new OrderItem();
            detail.setOrderId(orderId);
            detail.setProductId(item.getProduct_id());
            detail.setQty(item.getQty());
            detail.setProductAmt(item.getProduct_amt());
            // 小計 = 數量 * 當下單價
            detail.setProductTotalAmt(item.getProduct_amt().multiply(BigDecimal.valueOf(item.getQty())));
            detail.setItemStatus("待製作");
            
            OrderItem savedDetail = orderItemRepository.save(detail);
            savedDetails.add(savedDetail);
        }

        // 5. 自動寫入系統操作日誌表 (Audit Log)
        SystemLog log = new SystemLog();
        log.setAction("ORDER_SUBMIT");
        log.setIpAddress(ipAddress);
        log.setLogLevel("INFO");
        log.setMessage("新預約單成立！訂單號: " + orderId + "，顧客: " + request.getCustomer_name() + "，手機: " + request.getPhone());
        systemLogRepository.save(log);

        // 6. 【獨立線程異步推播】發送 LINE 訂單成立推播通知，確保不拖慢顧客下單毫秒級回應
        new Thread(() -> {
            try {
                lineNotifyService.sendOrderNotification(savedOrder, savedDetails);
            } catch (Exception e) {
                System.err.println("[LINE 推播] 異步發送通知異常: " + e.getMessage());
            }
        }).start();

        // 7. 【非同步發送 Email 預約確認信】
        try {
            emailService.sendOrderNotificationEmail(savedOrder, savedDetails, "CREATED");
        } catch (Exception e) {
            System.err.println("[Email 寄送] 異步發送訂單確認信異常: " + e.getMessage());
        }

        return savedOrder;
    }

    /**
     * 訂單追蹤功能 (依據電話與訂單號)
     */
    public Optional<Order> trackOrder(String phone, String orderId) {
        return orderRepository.findByPhoneAndOrderId(phone, orderId);
    }

    /**
     * 後台管理：批次更新排單項目狀態 (從 Controller 移轉，獲得 100% 交易 rollback 保障)
     */
    @Transactional
    public void batchUpdateItemStatus(List<Integer> ids, String newStatus) {
        List<OrderItem> items = orderItemRepository.findAllById(ids);
        
        // 用於本批次更新中累計新增已完成品項數量，防止同商品多筆明細併發防爆防禦失效
        java.util.Map<String, Integer> newlyCompletedQtyMap = new java.util.HashMap<>();
        
        for (OrderItem item : items) {
            String oldStatus = item.getItemStatus();
            if (newStatus.equals(oldStatus)) {
                continue; // 狀態未變，直接略過庫存計算
            }
            
            // 跳過折扣與非受管商品的庫存計算
            if (!"PROD_DISCOUNT".equals(item.getProductId())) {
                Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(item.getProductId()); // 悲觀寫入鎖
                if (menuOpt.isPresent()) {
                    Menu menu = menuOpt.get();
                    if (menu.getIsStockManaged()) {
                        boolean isOldCompleted = "已完成".equals(oldStatus);
                        boolean isNewCompleted = "已完成".equals(newStatus);
                        
                        if (!isOldCompleted && isNewCompleted) {
                            // 待製作/製作中 -> 已完成：檢查「可用自由庫存」是否充足
                            int currentResStock = orderItemRepository.getReservedStockByProductId(item.getProductId());
                            int newlyCompleted = newlyCompletedQtyMap.getOrDefault(item.getProductId(), 0);
                            int currentFreeStock = menu.getStock() - (currentResStock + newlyCompleted);
                            
                            if (currentFreeStock - item.getQty() < 0) {
                                throw new IllegalArgumentException("儲存失敗！品項「" + menu.getName() + "」自由可用庫存不足（目前僅剩 " + currentFreeStock + "，欲完成 " + item.getQty() + "）。請先補足庫存！");
                            }
                            newlyCompletedQtyMap.put(item.getProductId(), newlyCompleted + item.getQty());
                        }
                    }
                }
            }
            item.setItemStatus(newStatus);
        }
        orderItemRepository.saveAll(items);
    }

    /**
     * 後台管理：批量保存/更新訂單的品項明細 (從 Controller 移轉，獲得 100% 交易 rollback 保障)
     */
    @Transactional
    public void updateOrderItems(String orderId, List<OrderItem> items) {
        // 1. 【Undo 撤銷步驟】：先讀取資料庫中該訂單現存的明細
        List<OrderItem> existingItems = orderItemRepository.findByOrderId(orderId);
        
        // 查找母訂單原本的狀態。若原本已出貨或已結單，代表實體庫存早已扣除，無需再進行可用庫存校驗
        Optional<Order> orderOpt = orderRepository.findByOrderId(orderId);
        String orderStatus = orderOpt.isPresent() ? orderOpt.get().getStatus() : "待確認";
        boolean skipStockCheck = "已出貨".equals(orderStatus) || "已結單".equals(orderStatus);
        
        // 安全刪除該訂單原明細，並立即 Flush 同步至資料庫以讓後續 native query 不受舊明細干擾
        orderItemRepository.deleteAll(existingItems);
        orderItemRepository.flush();
        
        // 用於本訂單新明細中累計新增已完成品項數量，防止同商品多筆明細併發防爆防禦失效
        java.util.Map<String, Integer> newlyCompletedMap = new java.util.HashMap<>();
        
        // 2. 【Redo 套用與防呆】：重新保存傳入的明細，並依據新狀態與「可用自由庫存」進行防護
        for (OrderItem item : items) {
            item.setOrderId(orderId);
            item.setId(null); // 重設 ID 交由資料庫自增
            
            // 如果新明細狀態是已完成，且非折扣商品，啟用庫存管理，且非已出貨/已結單狀態，則進行防呆
            if ("已完成".equals(item.getItemStatus()) && !"PROD_DISCOUNT".equals(item.getProductId()) && !skipStockCheck) {
                Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(item.getProductId()); // 悲觀鎖
                if (menuOpt.isPresent()) {
                    Menu menu = menuOpt.get();
                    if (menu.getIsStockManaged()) {
                        int otherResStock = orderItemRepository.getReservedStockByProductId(item.getProductId());
                        int newlyCompleted = newlyCompletedMap.getOrDefault(item.getProductId(), 0);
                        int currentFreeStock = menu.getStock() - (otherResStock + newlyCompleted);
                        
                        if (currentFreeStock - item.getQty() < 0) {
                            throw new IllegalArgumentException("儲存失敗！品項「" + menu.getName() + "」自由可用庫存不足（目前僅剩 " + currentFreeStock + "，欲完成 " + item.getQty() + "）。請先補足庫存！");
                        }
                        newlyCompletedMap.put(item.getProductId(), newlyCompleted + item.getQty());
                    }
                }
            }
        }
        orderItemRepository.saveAll(items);
    }

    /**
     * 後台管理：更新整筆訂單主檔資訊 (從 Controller 移轉，獲得 100% 交易 rollback 保障)
     */
    @Transactional
    public Order updateOrder(String orderId, Order updatedOrder) {
        Optional<Order> orderOpt = orderRepository.findByOrderId(orderId);
        if (orderOpt.isPresent()) {
            Order dbOrder = orderOpt.get();
            String oldStatus = dbOrder.getStatus();
            String newStatus = updatedOrder.getStatus();
            
            // 狀態變更連動實體總庫存 (all_stock) 增減
            adjustPhysicalStockOnStatusChange(orderId, oldStatus, newStatus);
            
            dbOrder.setStatus(newStatus);
            dbOrder.setPaymentStatus(updatedOrder.getPaymentStatus());
            dbOrder.setDeliveryDate(updatedOrder.getDeliveryDate());
            dbOrder.setPaymentDate(updatedOrder.getPaymentDate());
            dbOrder.setCustomerName(updatedOrder.getCustomerName());
            dbOrder.setPhone(updatedOrder.getPhone());
            dbOrder.setNotes(updatedOrder.getNotes());
            dbOrder.setInstagram(updatedOrder.getInstagram());
            dbOrder.setLineId(updatedOrder.getLineId());
            dbOrder.setFacebook(updatedOrder.getFacebook());
            dbOrder.setEmail(updatedOrder.getEmail());
            
            if (updatedOrder.getAmount() != null) {
                dbOrder.setAmount(updatedOrder.getAmount());
            }
            Order saved = orderRepository.save(dbOrder);
            
            // 檢查是否從未出貨轉換為已出貨/已結單
            boolean isOldDelivered = "已出貨".equals(oldStatus) || "已結單".equals(oldStatus);
            boolean isNewDelivered = "已出貨".equals(newStatus) || "已結單".equals(newStatus);
            if (!isOldDelivered && isNewDelivered) {
                try {
                    List<OrderItem> details = orderItemRepository.findByOrderId(orderId);
                    emailService.sendOrderNotificationEmail(saved, details, "COMPLETED");
                } catch (Exception e) {
                    System.err.println("[Email 寄送] 異步發送出貨通知信異常: " + e.getMessage());
                }
            }
            return saved;
        }
        throw new IllegalArgumentException("找不到此訂單: " + orderId);
    }

    /**
     * 後台管理：更新單一訂單狀態 (從 Controller 移轉，獲得 100% 交易 rollback 保障)
     */
    @Transactional
    public void updateOrderStatus(String orderId, String status) {
        Optional<Order> orderOpt = orderRepository.findByOrderId(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            String oldStatus = order.getStatus();
            
            adjustPhysicalStockOnStatusChange(orderId, oldStatus, status);
            
            order.setStatus(status);
            Order saved = orderRepository.save(order);
            
            boolean isOldDelivered = "已出貨".equals(oldStatus) || "已結單".equals(oldStatus);
            boolean isNewDelivered = "已出貨".equals(status) || "已結單".equals(status);
            if (!isOldDelivered && isNewDelivered) {
                try {
                    List<OrderItem> details = orderItemRepository.findByOrderId(orderId);
                    emailService.sendOrderNotificationEmail(saved, details, "COMPLETED");
                } catch (Exception e) {
                    System.err.println("[Email 寄送] 異步發送出貨通知信異常: " + e.getMessage());
                }
            }
        } else {
            throw new IllegalArgumentException("找不到此訂單: " + orderId);
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
        // 用於累計本訂單內扣除的同商品數量，防止併發扣庫存防爆防禦失效
        java.util.Map<String, Integer> newlyReducedMap = new java.util.HashMap<>();
        
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
                        // 1. 取得該商品的總預約保留數量 (所有未出貨且已完成的明細之和)
                        int totalReserved = orderItemRepository.getReservedStockByProductId(item.getProductId());
                        
                        // 2. 計算本訂單中該商品已完成的數量 (應從總預約保留中排除，以防重複計算本訂單自身)
                        int thisOrderCompleted = 0;
                        for (OrderItem existingItem : items) {
                            if (existingItem.getProductId().equals(item.getProductId()) 
                                    && "已完成".equals(existingItem.getItemStatus())) {
                                thisOrderCompleted += existingItem.getQty();
                            }
                        }
                        
                        // 3. 計算其他訂單佔用的預約保留庫存
                        int otherReserved = Math.max(0, totalReserved - thisOrderCompleted);
                        
                        // 4. 計算本訂單出貨時可動用的自由庫存
                        int alreadyReduced = newlyReducedMap.getOrDefault(item.getProductId(), 0);
                        int availableFreeStock = menu.getStock() - otherReserved - alreadyReduced;
                        
                        if (availableFreeStock - item.getQty() < 0) {
                            throw new IllegalArgumentException("儲存失敗！品項「" + menu.getName() 
                                    + "」可用自由庫存不足（總庫存 " + menu.getStock() 
                                    + "，需保留給其他訂單 " + otherReserved 
                                    + "，本次欲出貨 " + item.getQty() 
                                    + "，剩餘可用僅 " + (menu.getStock() - otherReserved) + "）。請先補足庫存！");
                        }
                        
                        menu.setStock(menu.getStock() - item.getQty());
                        newlyReducedMap.put(item.getProductId(), alreadyReduced + item.getQty());
                    } else {
                        menu.setStock(menu.getStock() + item.getQty());
                    }
                    menuRepository.save(menu);
                }
            }
        }
    }
}
