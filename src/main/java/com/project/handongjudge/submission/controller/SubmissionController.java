package com.project.handongjudge.submission.controller;

import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
