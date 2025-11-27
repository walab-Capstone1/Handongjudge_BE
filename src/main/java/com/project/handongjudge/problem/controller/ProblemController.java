package com.project.handongjudge.problem.controller;

import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.project.handongjudge.problem.dto.ProblemCopyRequest;
import org.springframework.security.core.Authentication;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createProblem(
            @ModelAttribute ProblemCreateRequest request,
            Authentication authentication) throws IOException {
        Long instructorId = Long.parseLong(authentication.getName());
        Long problemId = problemService.createProblem(request, instructorId);
        return ResponseEntity.ok(problemId);
    }

    @GetMapping("/{problemId}")
    public ResponseEntity<ProblemResponse> getProblem(@PathVariable Long problemId) {
        return ResponseEntity.ok(problemService.getProblem(problemId));
    }
    // ProblemController.java에 추가
    @GetMapping
    public ResponseEntity<List<ProblemResponse>> getAllProblems(Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(problemService.getAllProblems(instructorId));
    }
    @PostMapping("/{problemId}/copy")
    public ResponseEntity<Long> copyProblem(
            @PathVariable Long problemId,
            @RequestBody(required = false) ProblemCopyRequest request,
            Authentication authentication) throws IOException {
        Long instructorId = Long.parseLong(authentication.getName());
        String newTitle = (request != null) ? request.getNewTitle() : null;
        Long newProblemId = problemService.copyProblem(problemId, newTitle, instructorId);
        return ResponseEntity.ok(newProblemId);
    }
}
