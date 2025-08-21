package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.service.AssignmentProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assignments/{assignmentId}")
public class AssignmentProblemController {

   private final AssignmentProblemService assignmentProblemService;

   @PostMapping("/{problemId}")
   public ResponseEntity<Void> addProblemToAssignment(
           @PathVariable Long assignmentId,
           @PathVariable Long problemId
   ) {
       assignmentProblemService.addProblemToAssignment(assignmentId, problemId);
       return ResponseEntity.ok().build();
   }
    // AssignmentProblemController.java에 추가 (문제 제거 API)
    @DeleteMapping("/{problemId}")
    public ResponseEntity<Void> removeProblemFromAssignment(
            @PathVariable Long assignmentId,
            @PathVariable Long problemId
    ) {
        assignmentProblemService.removeProblemFromAssignment(assignmentId, problemId);
        return ResponseEntity.ok().build();
    }
}
