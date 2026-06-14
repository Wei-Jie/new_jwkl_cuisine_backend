package com.jwkl.cuisine.service;

import com.jwkl.cuisine.entity.Post;
import com.jwkl.cuisine.entity.PostComment;
import com.jwkl.cuisine.repository.PostRepository;
import com.jwkl.cuisine.repository.PostCommentRepository;
import com.jwkl.cuisine.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class CommunityService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    private boolean isConfigEnabled(String key, boolean defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(config -> "true".equalsIgnoreCase(config.getConfigValue().trim()))
                .orElse(defaultValue);
    }

    /**
     * 前台：依據分類獲取已發布文章 (支援安全總開關)
     */
    public List<Post> getPublishedPosts(String category) {
        // 🔒 第一重防線：專區總開關。如果關閉，直接不返回任何資料
        if (!isConfigEnabled("ENABLE_COMMUNITY_ZONE", true)) {
            return Collections.emptyList();
        }

        if (category == null || category.trim().isEmpty() || "全部".equals(category)) {
            return postRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED");
        }

        String queryCategory = category.trim().toUpperCase();
        // 如果是小說連載，則按照章節編號正序（升序）排列，方便讀者從第一章看起
        if ("SERIAL".equals(queryCategory)) {
            return postRepository.findByCategoryAndStatusOrderByChapterNumAsc("SERIAL", "PUBLISHED");
        }

        return postRepository.findByCategoryAndStatusOrderByCreatedAtDesc(queryCategory, "PUBLISHED");
    }

    /**
     * 前台：獲取單一文章詳情
     */
    public Optional<Post> getPostById(Integer id) {
        if (!isConfigEnabled("ENABLE_COMMUNITY_ZONE", true)) {
            return Optional.empty();
        }
        return postRepository.findById(id).filter(p -> "PUBLISHED".equals(p.getStatus()));
    }

    /**
     * 後台：獲取所有文章列表（降序）
     */
    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 後台：發布/儲存新文章
     */
    @Transactional
    public Post createPost(Post post) {
        if (post.getCategory() != null) {
            post.setCategory(post.getCategory().toUpperCase());
        }
        return postRepository.save(post);
    }

    /**
     * 後台：更新文章
     */
    @Transactional
    public Post updatePost(Integer id, Post updatedPost) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isPresent()) {
            Post dbPost = postOpt.get();
            dbPost.setTitle(updatedPost.getTitle());
            dbPost.setContent(updatedPost.getContent());
            if (updatedPost.getCategory() != null) {
                dbPost.setCategory(updatedPost.getCategory().toUpperCase());
            }
            dbPost.setCoverImageUrl(updatedPost.getCoverImageUrl());
            dbPost.setTags(updatedPost.getTags());
            dbPost.setChapterNum(updatedPost.getChapterNum());
            dbPost.setStatus(updatedPost.getStatus());
            return postRepository.save(dbPost);
        }
        throw new IllegalArgumentException("找不到此文章，ID: " + id);
    }

    /**
     * 後台：刪除文章 (連動刪除留言由資料庫層級 CASCADE 或者是 Repository 連動刪除)
     */
    @Transactional
    public void deletePost(Integer id) {
        postRepository.deleteById(id);
    }

    /**
     * 前台：獲取某文章通過審核之留言列表
     */
    public List<PostComment> getCommentsByPostId(Integer postId) {
        if (!isConfigEnabled("ENABLE_COMMUNITY_ZONE", true)) {
            return Collections.emptyList();
        }
        return postCommentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, "APPROVED");
    }

    /**
     * 後台：獲取某文章所有留言 (供審查使用)
     */
    public List<PostComment> getAllCommentsByPostId(Integer postId) {
        return postCommentRepository.findByPostIdOrderByCreatedAtDesc(postId);
    }

    /**
     * 前台：對文章發表新留言 (支援留言開關危機卡控)
     */
    @Transactional
    public PostComment addComment(Integer postId, PostComment comment) {
        // 🔒 第二道防線：留言緊急防護開關
        if (!isConfigEnabled("ENABLE_COMMUNITY_COMMENTS", true)) {
            throw new IllegalArgumentException("【防範騷擾】留言功能維護中，目前暫不開放發表新留言！");
        }
        
        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty() || !"PUBLISHED".equals(postOpt.get().getStatus())) {
            throw new IllegalArgumentException("無法對未發布或不存在的文章發表留言！");
        }

        comment.setPostId(postId);
        comment.setStatus("APPROVED"); // 預設審核通過
        return postCommentRepository.save(comment);
    }

    /**
     * 後台：一鍵隱藏或審核留言
     */
    @Transactional
    public PostComment updateCommentStatus(Integer commentId, String status) {
        Optional<PostComment> commentOpt = postCommentRepository.findById(commentId);
        if (commentOpt.isPresent()) {
            PostComment comment = commentOpt.get();
            comment.setStatus(status);
            return postCommentRepository.save(comment);
        }
        throw new IllegalArgumentException("找不到此留言，ID: " + commentId);
    }
}
