package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(name = "order_id", nullable = false, length = 20)
    private String orderId; // 關聯之訂單編號

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId; // 關聯之商品料號

    @Column(nullable = false)
    private Integer qty; // 訂購數量

    @Column(name = "product_amt", nullable = false, precision = 10, scale = 2)
    private BigDecimal productAmt; // 當下訂購單價 (防範調價出錯)

    @Column(name = "product_total_amt", nullable = false, precision = 10, scale = 2)
    private BigDecimal productTotalAmt; // 當下品項小計 (qty * productAmt)

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName; // 當下商品品名快照

    @Column(name = "item_status", length = 20)
    private String itemStatus = "待製作"; // 單一品項製作狀態 (待製作/製作中/已完成)

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
