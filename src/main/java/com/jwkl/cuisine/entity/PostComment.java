package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "post_comments")
public class PostComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "post_id", nullable = false)
    private Integer postId; // 關聯的文章ID

    @Column(name = "nick_name", nullable = false, length = 50)
    private String nickName; // 留言者暱稱

    @Column(name = "comment_text", nullable = false, columnDefinition = "TEXT")
    private String commentText; // 留言內容

    @Column(length = 20)
    private String status = "APPROVED"; // 'APPROVED' (通過), 'HIDDEN' (隱藏)

    @Column(name = "ip_address", length = 50)
    private String ipAddress; // 留言者 IP 位址 (防範騷擾)

    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @Column(name = "created_at")
    private java.time.Instant createdAt;
}
