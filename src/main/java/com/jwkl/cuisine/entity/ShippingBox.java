package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "shipping_boxes")
public class ShippingBox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name; // 箱型名稱（如：黑貓冷凍 S60）

    @Column(nullable = false, length = 20)
    private String carrier; // 'black_cat' | 'seven_eleven'

    @Column(name = "max_points", nullable = false)
    private Integer maxPoints; // 最大可裝點數（容量上限）

    @Column(name = "max_weight_g", nullable = false)
    private Integer maxWeightG; // 最大可裝重量 (g)

    @Column(nullable = false)
    private Integer price; // 運費（元）

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // 是否啟用

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
