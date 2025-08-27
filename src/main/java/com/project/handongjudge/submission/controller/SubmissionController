package com.project.handongjudge.submission.controller;

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

}
