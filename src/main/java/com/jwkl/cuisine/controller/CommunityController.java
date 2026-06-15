package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Post;
import com.jwkl.cuisine.entity.PostComment;
import com.jwkl.cuisine.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

    // ==========================================
    // 1. 文章相關 API (Posts)
    // ==========================================

    /**
     * 前台：依據分類獲取已發布文章列表 (GET /api/v1/posts)
     */
    @GetMapping("/posts")
    public ResponseEntity<?> getPublishedPosts(@RequestParam(required = false) String category) {
        try {
            List<Post> posts = communityService.getPublishedPosts(category);
            return ResponseEntity.ok(posts);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 前台：獲取單一文章詳情 (GET /api/v1/posts/{id})
     */
    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPostById(@PathVariable Integer id) {
        Optional<Post> postOpt = communityService.getPostById(id);
        if (postOpt.isPresent()) {
            return ResponseEntity.ok(postOpt.get());
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 後台：獲取所有文章列表 (GET /api/v1/posts/all) - 需 ApiKey
     */
    @GetMapping("/posts/all")
    public ResponseEntity<?> getAllPosts() {
        try {
            List<Post> posts = communityService.getAllPosts();
            return ResponseEntity.ok(posts);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台：發布新文章 (POST /api/v1/posts) - 需 ApiKey
     */
    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody Post post) {
        try {
            Post created = communityService.createPost(post);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台：更新文章 (PUT /api/v1/posts/{id}) - 需 ApiKey
     */
    @PutMapping("/posts/{id}")
    public ResponseEntity<?> updatePost(@PathVariable Integer id, @RequestBody Post post) {
        try {
            Post updated = communityService.updatePost(id, post);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台：刪除文章 (DELETE /api/v1/posts/{id}) - 需 ApiKey
     */
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Integer id) {
        try {
            communityService.deletePost(id);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"文章刪除成功！\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // ==========================================
    // 2. 留言相關 API (Comments)
    // ==========================================

    /**
     * 前台：獲取文章之通過審查留言列表 (GET /api/v1/comments/post/{postId})
     */
    @GetMapping("/comments/post/{postId}")
    public ResponseEntity<?> getComments(@PathVariable Integer postId) {
        try {
            List<PostComment> comments = communityService.getCommentsByPostId(postId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台：獲取文章之所有留言列表 (GET /api/v1/comments/post/{postId}/all) - 需 ApiKey
     */
    @GetMapping("/comments/post/{postId}/all")
    public ResponseEntity<?> getAllComments(@PathVariable Integer postId) {
        try {
            List<PostComment> comments = communityService.getAllCommentsByPostId(postId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 前台：對文章發表新留言 (POST /api/v1/comments/post/{postId}) - 自動擷取 IP 防騷擾
     */
    @PostMapping("/comments/post/{postId}")
    public ResponseEntity<?> addComment(
            @PathVariable Integer postId, 
            @RequestBody PostComment comment,
            HttpServletRequest request) {
        try {
            // 自動記錄留言者真實 IP 供危機時防範惡意洗版
            String ip = getClientIp(request);
            comment.setIpAddress(ip);
            
            PostComment created = communityService.addComment(postId, comment);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 後台：變更/審查留言狀態 (PUT /api/v1/comments/{commentId}/status) - 需 ApiKey
     * status 可為 'APPROVED' (顯示) 或 'HIDDEN' (隱藏)
     */
    @PutMapping("/comments/{commentId}/status")
    public ResponseEntity<?> updateCommentStatus(
            @PathVariable Integer commentId, 
            @RequestParam String status) {
        try {
            PostComment updated = communityService.updateCommentStatus(commentId, status);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 前台：增加文章瀏覽數 (POST /api/v1/posts/{id}/view)
     */
    @PostMapping("/posts/{id}/view")
    public ResponseEntity<?> incrementPostViews(@PathVariable Integer id) {
        try {
            communityService.incrementViews(id);
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "瀏覽數已累加");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
