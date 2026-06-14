package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Integer> {
    
    // 前台：依文章ID與狀態（APPROVED）查詢留言列表（時間升序，先留言的排上面）
    List<PostComment> findByPostIdAndStatusOrderByCreatedAtAsc(Integer postId, String status);
    
    // 後台：依文章ID查詢所有留言（時間降序）
    List<PostComment> findByPostIdOrderByCreatedAtDesc(Integer postId);
}
