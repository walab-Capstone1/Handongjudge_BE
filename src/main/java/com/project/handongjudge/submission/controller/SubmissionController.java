package com.project.handongjudge.submission.controller;

import com.project.handongjudge.submission.dto.AsyncSubmitResponseDTO;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.dto.TestSubmitResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionAuthDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;
import com.project.handongjudge.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final SubmissionService submissionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionResponseDTO> submitCode(@ModelAttribute SubmissionRequestDTO request) {
        return ResponseEntity.ok(submissionService.submitCode(request));
    }
    @GetMapping("/result")
    public ResponseEntity<String> getSubmissionResult(
            @RequestParam("sectionId") Long sectionId,
            @RequestParam("submissionId") String submissionId) {

        String result = submissionService.getResult(sectionId, submissionId);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/submitAndGetResult")
    public SubmissionResponseDTO submitAndGetResult(Authentication authentication, @RequestBody SubmissionAuthDTO request) {
        SubmissionResponseDTO result = submissionService.submitAndGetResult(authentication, request);
        return result;
    }

    /** 과제 테스트하기: output 상세 반환만 하고 submissions 테이블에는 저장하지 않음. */
    @PostMapping(value = "/submitAndGetResult/output")
    public SubmissionOutputResponseDTO submitAndGetResultOutput(Authentication authentication, @RequestBody SubmissionAuthDTO request) {
        SubmissionOutputResponseDTO result = submissionService.submitAndGetResultOutput(authentication, request);
        return result;
    }


    @GetMapping("/result/output")
    public ResponseEntity<SubmissionOutputResponseDTO> getSubmissionOutputResult(
            @RequestParam("sectionId") Long sectionId,
            @RequestParam("submissionId") String submissionId) {

        SubmissionOutputResponseDTO result = submissionService.getResultOutput(sectionId, submissionId);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // 과제 비동기 제출 — Submit / Result 분리 API
    // =========================================================================

    /**
     * 과제 비동기 제출: DOMjudge에 코드 제출 후 submissionDbId를 즉시 반환.
     * 클라이언트는 GET /api/submissions/result/{submissionDbId} 를 폴링해 결과를 확인.
     */
    @PostMapping("/submit")
    public ResponseEntity<AsyncSubmitResponseDTO> submitAssignmentAsync(
            Authentication authentication,
            @RequestBody SubmissionAuthDTO request) {
        AsyncSubmitResponseDTO result = submissionService.submitAssignmentAsync(authentication, request);
        return ResponseEntity.ok(result);
    }

    /**
     * 과제 채점 결과 조회.
     * 채점 완료 → 200 OK + SubmissionResponseDTO
     * 채점 중   → 204 No Content
     */
    @GetMapping("/result/{submissionDbId}")
    public ResponseEntity<SubmissionResponseDTO> getAssignmentResult(
            Authentication authentication,
            @PathVariable Long submissionDbId) {
        SubmissionResponseDTO result = submissionService.getAssignmentResult(authentication, submissionDbId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 과제 채점 결과 SSE 스트리밍.
     * POST /submit으로 얻은 submissionDbId를 사용해 연결하면,
     * 테스트케이스 결과가 실시간으로 이벤트 스트림으로 전달된다.
     *
     * 이벤트 타입:
     *   testcase — { index, result }
     *   complete — { result, passedCount, totalCount }
     *   ce       — { result: "CE", output_compile?: string }
     *   error    — { message }
     */
    @GetMapping(value = "/stream/{submissionDbId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAssignmentResult(
            Authentication authentication,
            @PathVariable Long submissionDbId) {
        return submissionService.createSseStream(authentication, submissionDbId);
    }

    // =========================================================================
    // 테스트하기 SSE 스트리밍 — DB 저장 없는 일회성 테스트 제출
    // =========================================================================

    /**
     * 테스트하기 비동기 제출: DOMjudge에만 제출하고 sessionKey를 즉시 반환.
     * 반환된 sessionKey로 GET /test/stream/{sessionKey} SSE 연결 후 output 결과 수신.
     */
    @PostMapping("/test/submit")
    public ResponseEntity<TestSubmitResponseDTO> testSubmitAsync(
            Authentication authentication,
            @RequestBody SubmissionAuthDTO request) {
        TestSubmitResponseDTO result = submissionService.submitCodeForTestAsync(authentication, request);
        return ResponseEntity.ok(result);
    }

    /**
     * 테스트하기 output 결과 SSE 스트리밍.
     * 이벤트 타입:
     *   testcase — { index, result, output, outputError, outputDiff, testcaseInput, expectedOutput, runtime, memoryUsed }
     *   complete — { result }
     *   ce       — { result: "CE", output_compile?: string }
     *   error    — { message }
     */
    @GetMapping(value = "/test/stream/{sessionKey}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTestOutput(
            Authentication authentication,
            @PathVariable String sessionKey,
            @RequestParam Long sectionId) {
        return submissionService.createTestOutputStream(authentication, sessionKey, sectionId);
    }

    @GetMapping("/lastSubmitCode")
    public ResponseEntity<String> getUserSubmissionStatus(
            Authentication authentication,
            @RequestParam("problemId") Long problemId,
            @RequestParam("sectionId") Long sectionId,
            @RequestParam("language") String language
    ){
        String result = submissionService.getUserLatestSubmission(authentication,problemId,sectionId,language);
        return ResponseEntity.ok(result);
    }

}
