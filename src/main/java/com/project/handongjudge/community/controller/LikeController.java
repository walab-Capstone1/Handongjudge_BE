package com.project.handongjudge.community.controller;

import com.project.handongjudge.community.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/community/likes")
@RequiredArgsConstructor
@Tag(name = "Community - Likes", description = "커뮤니티 추천 API")
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/questions/{questionId}")
    @Operation(summary = "질문 추천/취소 토글")
    public ResponseEntity<Map<String, Object>> toggleQuestionLike(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        boolean isLiked = likeService.toggleQuestionLike(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", isLiked ? "추천했습니다" : "추천을 취소했습니다");
        result.put("data", Map.of("isLiked", isLiked));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/comments/{commentId}")
    @Operation(summary = "댓글 추천/취소 토글")
    public ResponseEntity<Map<String, Object>> toggleCommentLike(
            @PathVariable Long commentId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        boolean isLiked = likeService.toggleCommentLike(commentId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", isLiked ? "추천했습니다" : "추천을 취소했습니다");
        result.put("data", Map.of("isLiked", isLiked));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/questions/{questionId}")
    @Operation(summary = "질문 추천 여부 확인")
    public ResponseEntity<Map<String, Object>> isQuestionLiked(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        boolean isLiked = likeService.isQuestionLikedByUser(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of("isLiked", isLiked));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/comments/{commentId}")
    @Operation(summary = "댓글 추천 여부 확인")
    public ResponseEntity<Map<String, Object>> isCommentLiked(
            @PathVariable Long commentId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        boolean isLiked = likeService.isCommentLikedByUser(commentId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of("isLiked", isLiked));

        return ResponseEntity.ok(result);
    }
}


