package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.service.QuizService;
import com.project.handongjudge.quiz.service.QuizSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/quizzes")
public class QuizController {

    private final QuizService quizService;
    private final QuizSessionService quizSessionService;

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

    /**
     * 퀴즈 성적 조회
     */
    @GetMapping("/{quizId}/grades")
    public ResponseEntity<List<com.project.handongjudge.grade.dto.StudentGradeSummaryDTO>> getQuizGrades(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<com.project.handongjudge.grade.dto.StudentGradeSummaryDTO> grades = 
                quizService.getQuizGrades(quizId, sectionId, userId);
        return ResponseEntity.ok(grades);
    }

    /**
     * 퀴즈 단일 성적 저장/수정
     */
    @PostMapping("/{quizId}/grades")
    public ResponseEntity<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO> saveQuizGrade(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody com.project.handongjudge.quiz.dto.QuizGradeRequestDTO request,
            Authentication authentication
    ) {
        request.setQuizId(quizId);
        Long tutorId = Long.parseLong(authentication.getName());
        com.project.handongjudge.quiz.dto.QuizGradeResponseDTO response = quizService.saveQuizGrade(request, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 일괄 성적 저장
     */
    @PostMapping("/{quizId}/grades/bulk")
    public ResponseEntity<List<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO>> saveBulkQuizGrades(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody com.project.handongjudge.quiz.dto.QuizBulkGradeRequestDTO request,
            Authentication authentication
    ) {
        request.setQuizId(quizId);
        Long tutorId = Long.parseLong(authentication.getName());
        List<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO> responses = quizService.saveBulkQuizGrades(request, tutorId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 퀴즈 문제 배점 설정
     */
    @PutMapping("/{quizId}/grades/problems/{problemId}/points")
    public ResponseEntity<Void> setQuizProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long problemId,
            @RequestBody Map<String, Integer> body,
            Authentication authentication
    ) {
        Integer points = body.get("points");
        if (points == null) {
            return ResponseEntity.badRequest().build();
        }
        Long tutorId = Long.parseLong(authentication.getName());
        quizService.setQuizProblemPoints(quizId, problemId, points);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 문제 배점 일괄 설정
     */
    @PutMapping("/{quizId}/grades/points/bulk")
    public ResponseEntity<Void> setBulkQuizProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<Long, Integer> problemPoints,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        quizService.setBulkQuizProblemPoints(quizId, problemPoints);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 제출 코드 조회 (학생의 accept된 코드)
     */
    @GetMapping("/{quizId}/students/{userId}/problems/{problemId}/accepted-code")
    public ResponseEntity<StudentAcceptedCodeResponse> getStudentAcceptedCode(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long userId,
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        StudentAcceptedCodeResponse response = quizService.getStudentAcceptedCode(
                sectionId, quizId, userId, problemId, instructorId);
        return ResponseEntity.ok(response);
    }

    // ===== 시험 세션 관리 (중복 접속 방지) =====

    /**
     * 시험 페이지 진입 시도
     * 응답: { status: "OK" } or { status: "CONFLICT" }
     */
    @PostMapping("/{quizId}/session/enter")
    public ResponseEntity<Map<String, String>> enterQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        QuizSessionService.EnterResult result = quizSessionService.enter(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("status", result.name()));
    }

    /**
     * 기존 세션 강제 인계 (사용자가 "여기서 계속하기" 선택 시)
     */
    @PostMapping("/{quizId}/session/takeover")
    public ResponseEntity<Map<String, String>> takeoverQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        quizSessionService.takeover(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * Heartbeat - 세션 TTL 연장 및 유효성 확인
     * 응답: { valid: true } or { valid: false } (다른 곳에서 탈취됨)
     */
    @PostMapping("/{quizId}/session/heartbeat")
    public ResponseEntity<Map<String, Boolean>> heartbeatQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        boolean valid = quizSessionService.heartbeat(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /**
     * 시험 페이지 이탈 시 세션 해제
     */
    @PostMapping("/{quizId}/session/exit")
    public ResponseEntity<Void> exitQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        quizSessionService.exit(quizId, userId, clientSessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 비활성화/활성화 토글
     */
    @PatchMapping("/{quizId}/active")
    public ResponseEntity<QuizResponse> toggleQuizActive(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        QuizResponse response = quizService.toggleQuizActive(quizId, active, instructorId);
        return ResponseEntity.ok(response);
    }
}

