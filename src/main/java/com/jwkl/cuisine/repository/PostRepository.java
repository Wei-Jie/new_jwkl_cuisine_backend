package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {
    
    // 依發布狀態查詢（降序：最新消息、公告、日常心情）
    List<Post> findByStatusOrderByCreatedAtDesc(String status);
    
    // 依分類與發布狀態查詢（降序：灶下公告、限時活動、日常隨筆）
    List<Post> findByCategoryAndStatusOrderByCreatedAtDesc(String category, String status);
    
    // 依分類與發布狀態查詢（升序：小說/故事連載，從第一章開始排）
    List<Post> findByCategoryAndStatusOrderByChapterNumAsc(String category, String status);

    // 後台管理：查詢所有文章（不論草稿或發布，降序排列）
    List<Post> findAllByOrderByCreatedAtDesc();
}
