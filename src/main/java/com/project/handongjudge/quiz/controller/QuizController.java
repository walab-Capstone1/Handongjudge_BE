package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/quizzes")
public class QuizController {

    private final QuizService quizService;

    /**
     * 코딩 테스트 생성
     */
    @PostMapping
    public ResponseEntity<QuizResponse> createQuiz(
            @PathVariable Long sectionId,
            @RequestBody QuizRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.createQuiz(sectionId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 섹션별 코딩 테스트 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<QuizListDto>> getQuizzesBySection(
            @PathVariable Long sectionId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<QuizListDto> quizzes = quizService.getQuizzesBySection(sectionId, userId);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * 코딩 테스트 상세 정보 조회
     */
    @GetMapping("/{quizId}")
    public ResponseEntity<QuizResponse> getQuizInfo(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.getQuizInfo(quizId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코딩 테스트 문제 목록 조회
     */
    @GetMapping("/{quizId}/problems")
    public ResponseEntity<List<QuizProblemDto>> getQuizProblems(
            @PathVariable Long sectionId,
            @PathVariable Long quizId
    ) {
        List<QuizProblemDto> problems = quizService.getQuizProblems(quizId);
        return ResponseEntity.ok(problems);
    }

    /**
     * 코딩 테스트 수정
     */
    @PutMapping("/{quizId}")
    public ResponseEntity<QuizResponse> updateQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody QuizRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.updateQuiz(sectionId, quizId, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코딩 테스트 삭제
     */
    @DeleteMapping("/{quizId}")
    public ResponseEntity<Void> deleteQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        quizService.deleteQuiz(quizId, userId);
        return ResponseEntity.noContent().build();
    }
}

