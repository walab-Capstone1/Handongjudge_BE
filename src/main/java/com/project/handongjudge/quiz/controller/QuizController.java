package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;
import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.service.QuizService;
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
     * 퀴즈 학생 진행 현황 일괄 조회
     */
    @GetMapping("/{quizId}/student-progress")
    public ResponseEntity<List<StudentProgressResponse>> getQuizStudentProgress(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<StudentProgressResponse> progress = quizService.getQuizStudentProgress(quizId, sectionId, userId);
        return ResponseEntity.ok(progress);
    }

    /**
     * 퀴즈 문제별 제출 통계 조회
     */
    @GetMapping("/{quizId}/submission-stats")
    public ResponseEntity<QuizSubmissionStatsResponse> getQuizSubmissionStats(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizSubmissionStatsResponse stats = quizService.getQuizSubmissionStats(quizId, sectionId, userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 퀴즈에서 문제 제거
     */
    @DeleteMapping("/{quizId}/problems/{problemId}")
    public ResponseEntity<Void> removeProblemFromQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        quizService.removeProblemFromQuiz(quizId, problemId, instructorId);
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

    /**
     * 퀴즈 상태 수동 변경 (시작/정지/종료)
     */
    @PatchMapping("/{quizId}/status")
    public ResponseEntity<QuizResponse> updateQuizStatus(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        String statusStr = request.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Quiz.QuizStatus status;
        try {
            status = Quiz.QuizStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        QuizResponse response = quizService.updateQuizStatus(quizId, status, instructorId);
        return ResponseEntity.ok(response);
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

