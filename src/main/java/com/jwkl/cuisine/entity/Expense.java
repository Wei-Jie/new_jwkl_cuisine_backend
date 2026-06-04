package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(nullable = false, length = 10)
    private String date; // 支出日期 (YYYY/MM/DD)

    @Column(nullable = false, length = 50)
    private String category; // 支出分類

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName; // 項目名稱

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // 金額

    @Column(nullable = false, length = 50)
    private String payer; // 付款人 / 經手人

    private String note; // 備註

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
