package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_configs")
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(name = "config_key", unique = true, nullable = false, length = 50)
    private String configKey; // 設定鍵 (如 MIN_ORDER_AMOUNT)

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue; // 設定值 (如 300)

    private String description; // 參數用途說明

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
