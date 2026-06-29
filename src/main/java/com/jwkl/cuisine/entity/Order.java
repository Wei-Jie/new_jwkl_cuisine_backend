package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(name = "order_id", unique = true, nullable = false, length = 20)
    private String orderId; // 訂單編號 (格式: S000001)

    @Column(name = "order_date", nullable = false, length = 10)
    private String orderDate; // 下單日期 (YYYY/MM/DD)

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName; // 顧客姓名

    @Column(nullable = false, length = 20)
    private String phone; // 手機號碼

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // 訂單總金額

    @Column(length = 20)
    private String status = "待確認"; // 訂單狀態 (待確認/已接單/已出貨/已結單)

    @Column(name = "delivery_date", length = 10)
    private String deliveryDate; // 出貨日 / 預計取貨日 (YYYY/MM/DD)

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "未付款"; // 收款狀態 (未付款/已付款)

    @Column(name = "payment_date", length = 10)
    private String paymentDate; // 收款日期 (YYYY/MM/DD)

    @Column(length = 100)
    private String instagram; // Instagram 帳號 (選填)

    @Column(name = "line_id", length = 100)
    private String lineId; // Line ID (選填)

    @Column(length = 100)
    private String facebook; // Facebook 名稱 (選填)

    @Column(length = 100)
    private String email; // 電子郵件 (通知用)

    private String notes; // 顧客備註

    // ===== 配送相關欄位 =====
    @Column(name = "shipping_method", length = 20)
    private String shippingMethod; // 'face_to_face' | 'home_delivery' | 'store_pickup'

    @Column(name = "shipping_carrier", length = 20)
    private String shippingCarrier; // 'black_cat' | 'seven_eleven'

    @Column(name = "shipping_box_id")
    private Integer shippingBoxId; // FK -> shipping_boxes.id

    @Column(name = "shipping_fee")
    private Integer shippingFee; // 最終確認運費（元）

    @Column(name = "recipient_name", length = 50)
    private String recipientName; // 收件人姓名

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone; // 收件人電話

    @Column(name = "recipient_address")
    private String recipientAddress; // 收件地址（宅配用）

    @Column(name = "store_name", length = 100)
    private String storeName; // 門市/面交地點名稱

    @Column(name = "store_id", length = 20)
    private String storeId; // 門市代碼（未來 API 串接用）

    @Column(name = "tracking_number", length = 50)
    private String trackingNumber; // 貨運追蹤號碼

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
