package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 200)
    private String title; // 文章標題

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // 心情/日誌內文 (支援 Markdown 與 Emoji)

    @Column(nullable = false, length = 50)
    private String category; // 'ANNOUNCEMENT', 'EVENT', 'STORY', 'SERIAL'

    @Column(name = "cover_image_url")
    private String coverImageUrl; // 封面圖網址 (Supabase Storage)

    // 對於 PostgreSQL TEXT[] 欄位，為了 100% 避免驅動在 Hibernate/JPA 對接時報錯，
    // 我們使用普通的逗號區隔字串來保存，並在前後端溝通時轉換。
    @Column(name = "tags")
    private String tags; // 標籤，例如 "牛腱,端午節,連載中"

    @Column(name = "chapter_num")
    private Integer chapterNum; // 連載小說章節編號 (例如 5)

    @Column(length = 20)
    private String status = "DRAFT"; // 'DRAFT' (草稿), 'PUBLISHED' (已發布)

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
