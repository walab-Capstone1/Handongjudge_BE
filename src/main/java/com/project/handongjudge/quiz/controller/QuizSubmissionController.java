package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.submission.dto.AsyncSubmitResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionAuthDTO;
import com.project.handongjudge.submission.dto.SubmissionQuizResponseDTO;
import com.project.handongjudge.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/quiz")
public class QuizSubmissionController {

    private final SubmissionService submissionService;

    /**
     * 기존 API (하위 호환 유지): 서버 측 폴링으로 채점 결과까지 한 번에 반환.
     */
    @PostMapping("/submitAndGetResult")
    public SubmissionQuizResponseDTO submitQuizCode(
            Authentication authentication,
            @RequestBody SubmissionAuthDTO request
    ) {
        return submissionService.submitQuizCode(authentication, request);
    }

    // =========================================================================
    // Phase 2: Submit / Result 분리 API (클라이언트 폴링)
    // =========================================================================

    /**
     * Phase 2 — 제출: DOMjudge에 코드 제출 후 submissionDbId를 즉시 반환.
     * 서블릿 스레드 점유 없이 ~1초 내에 응답.
     */
    @PostMapping("/submit")
    public AsyncSubmitResponseDTO submitQuizCodeAsync(
            Authentication authentication,
            @RequestBody SubmissionAuthDTO request
    ) {
        return submissionService.submitQuizCodeAsync(authentication, request);
    }

    /**
     * Phase 2 — 결과 조회: 클라이언트가 1~2초 간격으로 폴링.
     * 채점 완료 → 200 OK + SubmissionQuizResponseDTO
     * 채점 중   → 204 No Content
     */
    @GetMapping("/result/{submissionDbId}")
    public ResponseEntity<SubmissionQuizResponseDTO> getQuizResult(
            Authentication authentication,
            @PathVariable Long submissionDbId
    ) {
        SubmissionQuizResponseDTO result = submissionService.getQuizResult(authentication, submissionDbId);
        if (result == null) {
            return ResponseEntity.noContent().build(); // 204: 아직 채점 중
        }
        return ResponseEntity.ok(result);
    }
}
