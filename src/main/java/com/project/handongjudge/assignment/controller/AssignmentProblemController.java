package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.service.AssignmentProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assignments/{assignmentId}")
public class AssignmentProblemController {

   private final AssignmentProblemService assignmentProblemService;

   @PostMapping("/{problemId}")
   public ResponseEntity<Void> addProblemToAssignment(
           @PathVariable Long assignmentId,
           @PathVariable Long problemId,
           Authentication authentication
   ) {
       Long userId = Long.parseLong(authentication.getName());
       assignmentProblemService.addProblemToAssignment(assignmentId, problemId, userId);
       return ResponseEntity.ok().build();
   }

    @DeleteMapping("/{problemId}")
    public ResponseEntity<Void> removeProblemFromAssignment(
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        assignmentProblemService.removeProblemFromAssignment(assignmentId, problemId, userId);
        return ResponseEntity.ok().build();
    }
}
