package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

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

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsBySection(
            @PathVariable Long sectionId
    ) {
        return ResponseEntity.ok(
                assignmentService.getAssignmentsBySection(sectionId)
        );
    }
}
