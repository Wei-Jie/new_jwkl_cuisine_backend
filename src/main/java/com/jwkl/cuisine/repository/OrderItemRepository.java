package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    
    // 依據訂單編號 (S000001) 查詢該訂單所有的明細品項
    List<OrderItem> findByOrderId(String orderId);

    /**
     * 【新專案三表聯防查詢】為「品項排單管理by品項」量身打造的極速 native query 查詢。
     * 直接在 PostgreSQL 中完成 order_items、orders、menus 三表 JOIN。
     */
    @org.springframework.data.jpa.repository.Query(value = 
        "SELECT oi.id AS id, o.order_id AS \"orderId\", o.order_date AS \"orderDate\", " +
        "o.customer_name AS \"customerName\", oi.product_name AS \"itemName\", oi.qty AS qty, " +
        "oi.product_amt AS \"unitPrice\", oi.product_total_amt AS subtotal, oi.item_status AS status " +
        "FROM order_items oi " +
        "JOIN orders o ON oi.order_id = o.order_id " +
        "WHERE oi.product_name = :menuName AND o.status <> '待確認' AND o.status <> '已出貨' AND o.status <> '已結單' AND o.status <> '已取消' AND o.status <> '已退回' AND (oi.item_status = '待製作' OR oi.item_status = '已完成')", 
        nativeQuery = true)
    List<java.util.Map<String, Object>> findScheduleMgmtByItem(@org.springframework.data.repository.query.Param("menuName") String menuName);

    /**
     * 實時動態計算某商品的預約保留庫存 (res_stock)
     * 條件：明細狀態為「已完成」，且訂單狀態非已出貨、非已結單、非已取消、非已退回
     */
    @org.springframework.data.jpa.repository.Query(value = 
        "SELECT COALESCE(SUM(oi.qty), 0) FROM order_items oi " +
        "JOIN orders o ON oi.order_id = o.order_id " +
        "WHERE oi.product_id = :productId AND oi.item_status = '已完成' " +
        "AND o.status <> '已出貨' AND o.status <> '已結單' AND o.status <> '已取消' AND o.status <> '已退回'", 
        nativeQuery = true)
    int getReservedStockByProductId(@org.springframework.data.repository.query.Param("productId") String productId);
}
