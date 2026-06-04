package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_logs")
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(name = "timestamp", insertable = false, updatable = false)
    private LocalDateTime timestamp; // 記錄發生時間

    @Column(nullable = false, length = 50)
    private String action; // 操作項目 (如 LOGIN_FAIL)

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // 操作者 IP 位址

    @Column(name = "log_level", length = 10)
    private String logLevel = "INFO"; // 日誌層級 (INFO/WARN/ERROR)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message; // 日誌詳細內容

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
