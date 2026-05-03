package com.project.handongjudge.community.controller;

import com.project.handongjudge.community.dto.*;
import com.project.handongjudge.community.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/community/questions")
@RequiredArgsConstructor
@Tag(name = "Community - Questions", description = "커뮤니티 질문 API")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    @Operation(summary = "질문 작성")
    public ResponseEntity<Map<String, Object>> createQuestion(
            @Valid @RequestBody QuestionCreateDto dto,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        QuestionResponseDto response = questionService.createQuestion(dto, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "질문이 작성되었습니다");
        result.put("data", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @Operation(summary = "질문 목록 조회 (섹션별)")
    public ResponseEntity<Map<String, Object>> getQuestions(
            @RequestParam Long sectionId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        Page<QuestionListDto> questions = questionService.getQuestionsBySection(
                sectionId, status, userId, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "질문 목록 조회 성공");
        result.put("data", questions);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{questionId}")
    @Operation(summary = "질문 상세 조회")
    public ResponseEntity<Map<String, Object>> getQuestion(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        QuestionResponseDto response = questionService.getQuestionById(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "질문 조회 성공");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{questionId}")
    @Operation(summary = "질문 수정")
    public ResponseEntity<Map<String, Object>> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionUpdateDto dto,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        QuestionResponseDto response = questionService.updateQuestion(questionId, dto, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "질문이 수정되었습니다");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{questionId}")
    @Operation(summary = "질문 삭제")
    public ResponseEntity<Map<String, Object>> deleteQuestion(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        questionService.deleteQuestion(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "질문이 삭제되었습니다");

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{questionId}/pin")
    @Operation(summary = "질문 핀 설정/해제 (교수만)")
    public ResponseEntity<Map<String, Object>> togglePin(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        QuestionResponseDto response = questionService.togglePin(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", response.getIsPinned() ? "질문이 고정되었습니다" : "질문 고정이 해제되었습니다");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{questionId}/resolve")
    @Operation(summary = "질문 해결 상태 변경 (작성자만)")
    public ResponseEntity<Map<String, Object>> toggleResolve(
            @PathVariable Long questionId,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        QuestionResponseDto response = questionService.toggleResolve(questionId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", response.getStatus().equals("RESOLVED") ? 
                "질문이 해결되었습니다" : "질문이 미해결 상태로 변경되었습니다");
        result.put("data", response);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @Operation(summary = "질문 검색")
    public ResponseEntity<Map<String, Object>> searchQuestions(
            @RequestParam Long sectionId,
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        Long userId = extractUserId(authentication);
        Page<QuestionListDto> questions = questionService.searchQuestions(
                sectionId, keyword, userId, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "검색 성공");
        result.put("data", questions);

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


