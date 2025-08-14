package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.dto.AssignmentInfoDto;
import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.service.AssignmentService;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;  
    private final ProblemService problemService;

    @PostMapping
    public ResponseEntity<AssignmentResponse> createAssignment(
            @PathVariable Long sectionId,
            @RequestBody AssignmentRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.createAssignment(sectionId, request, userId)
        );
    }



    @GetMapping("/{assignmentId}/problems")
    public ResponseEntity<List<Problem>> getAssignmentProblems( // 과제 문제 목록 조회                                  
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId
    ) { 
        return ResponseEntity.ok(
                problemService.getProblemsByAssignmentId(assignmentId)
        );
    }

    // AssignmentController.java에 추가
    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentInfoDto> getAssignmentInfo(@PathVariable Long assignmentId) {
        try {
            AssignmentInfoDto assignmentInfo = assignmentService.getAssignmentInfo(assignmentId);
            return ResponseEntity.ok(assignmentInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
