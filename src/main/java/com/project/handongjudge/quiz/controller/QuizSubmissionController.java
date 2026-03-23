package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.submission.dto.SubmissionAuthDTO;
import com.project.handongjudge.submission.dto.SubmissionQuizResponseDTO;
import com.project.handongjudge.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/quiz")
public class QuizSubmissionController {

    private final SubmissionService submissionService;

    /**
     * 퀴즈 전용 제출 - 테스트케이스별 scoring 적용
     */
    @PostMapping("/submitAndGetResult")
    public SubmissionQuizResponseDTO submitQuizCode(
            Authentication authentication,
            @RequestBody SubmissionAuthDTO request
    ) {
        return submissionService.submitQuizCode(authentication, request);
    }
}
