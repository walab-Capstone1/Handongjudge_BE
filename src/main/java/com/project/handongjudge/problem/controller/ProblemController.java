package com.project.handongjudge.problem.controller;

import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createProblem(@ModelAttribute ProblemCreateRequest request) throws IOException {
        Long problemId = problemService.createProblem(request);
        return ResponseEntity.ok(problemId);
    }

    @GetMapping("/{problemId}")
    public ResponseEntity<ProblemResponse> getProblem(@PathVariable Long problemId) {
        return ResponseEntity.ok(problemService.getProblem(problemId));
    }
    // ProblemController.java에 추가
    @GetMapping
    public ResponseEntity<List<ProblemResponse>> getAllProblems() {
        return ResponseEntity.ok(problemService.getAllProblems());
    }
}
