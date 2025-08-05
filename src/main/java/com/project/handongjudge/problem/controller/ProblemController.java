package com.project.handongjudge.problem.controller;

import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assignments/{assignmentId}/problems")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public ResponseEntity<List<ProblemResponse>> getProblemsByAssignment(
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(problemService.getProblemsByAssignmentId(assignmentId));
    }
}
