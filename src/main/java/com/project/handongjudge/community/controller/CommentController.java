package com.project.handongjudge.community.controller;

import com.project.handongjudge.community.dto.CommentCreateDto;
import com.project.handongjudge.community.dto.CommentResponseDto;
import com.project.handongjudge.community.dto.CommentUpdateDto;
import com.project.handongjudge.community.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community/comments")
@RequiredArgsConstructor
@Tag(name = "Community - Comments", description = "커뮤니티 댓글 API")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "댓글 작성")
    public ResponseEntity<Map<String, Object>> createComment(
            @Valid @RequestBody CommentCreateDto dto,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        CommentResponseDto response = commentService.createComment(dto, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "댓글이 작성되었습니다");
        result.put("data", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @Operation(summary = "질문별 댓글 목록 조회")
    public ResponseEntity<Map<String, Object>> getComments(
            @RequestParam Long questionId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        List<CommentResponseDto> comments = commentService.getCommentsByQuestion(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "댓글 목록 조회 성공");
        result.put("data", comments);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "댓글 수정")
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateDto dto,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        CommentResponseDto response = commentService.updateComment(commentId, dto, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "댓글이 수정되었습니다");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        commentService.deleteComment(commentId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "댓글이 삭제되었습니다");

        return ResponseEntity.ok(result);
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("인증 정보가 없습니다");
        }
        String principal = authentication.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equals(principal)) {
            throw new InsufficientAuthenticationException("인증 정보가 없습니다");
        }
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            throw new InsufficientAuthenticationException("유효하지 않은 인증 정보입니다");
        }
    }
}


