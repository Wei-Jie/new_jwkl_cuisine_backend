package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "faqs")
public class Faq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 流水號

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question; // FAQ 問題

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer; // FAQ 回答內容

    @Column(name = "image_url")
    private String imageUrl; // FAQ 說明圖網址 (Supabase Storage)

    @Column(name = "sort_order")
    private Integer sortOrder = 0; // 排序權重

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
