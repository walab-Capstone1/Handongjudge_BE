package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.dto.AssignmentSubmissionStatsResponse;
import com.project.handongjudge.assignment.dto.UpcomingAssignmentResponse;
import com.project.handongjudge.assignment.dto.UserSubmissionStatusResponse;
import com.project.handongjudge.assignment.service.AssignmentService;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;

import java.util.List;
import java.util.Map;

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

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsBySection(
            @PathVariable Long sectionId,
            Authentication authentication) {  // Authentication 추가
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getAssignmentsBySection(sectionId, userId)  // userId 전달
        );
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentResponse> getAssignmentInfo(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication  // 추가
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getAssignmentInfo(assignmentId, userId)  // userId 전달
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

    // 기존 코드에 추가
    @GetMapping("/{assignmentId}/submission-stats")
    public ResponseEntity<AssignmentSubmissionStatsResponse> getAssignmentSubmissionStats(
            @PathVariable Long assignmentId,
            @PathVariable Long sectionId) {  // @RequestParam → @PathVariable로 변경

        AssignmentSubmissionStatsResponse stats = assignmentService.getAssignmentSubmissionStats(assignmentId, sectionId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/instructor/{instructorId}/all-submission-stats")
    public ResponseEntity<List<AssignmentSubmissionStatsResponse>> getAllAssignmentsSubmissionStats(
            @PathVariable Long instructorId) {

        List<AssignmentSubmissionStatsResponse> allStats = assignmentService.getAllAssignmentsSubmissionStats(instructorId);
        return ResponseEntity.ok(allStats);
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody AssignmentRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.updateAssignment(sectionId, assignmentId, request, userId)
        );
    }
    @GetMapping("/{assignmentId}/user-submission-status")
    public ResponseEntity<UserSubmissionStatusResponse> getUserSubmissionStatus(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getUserSubmissionStatus(sectionId, assignmentId, userId)
        );
    }
    // 과제별 학생 진행 현황 조회
    @GetMapping("/{assignmentId}/student-progress")
    public ResponseEntity<List<StudentProgressResponse>> getAssignmentStudentProgress(
            @PathVariable Long assignmentId,
            @PathVariable Long sectionId) {

        List<StudentProgressResponse> progress = assignmentService.getAssignmentStudentProgress(assignmentId, sectionId);
        return ResponseEntity.ok(progress);
    }
    @PatchMapping("/{assignmentId}/active")
    public ResponseEntity<AssignmentResponse> toggleAssignmentActive(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        AssignmentResponse response = assignmentService.toggleAssignmentActive(assignmentId, active, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 과제 삭제
     */
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        assignmentService.deleteAssignment(sectionId, assignmentId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 마감 직전 과제 조회
     * @param sectionId 분반 ID
     * @param days 마감일까지 남은 일수 (기본값: 3일)
     * @return 마감 직전 과제 목록 (제출률 포함)
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingAssignmentResponse>> getUpcomingAssignments(
            @PathVariable Long sectionId,
            @RequestParam(required = false, defaultValue = "3") Integer days) {
        List<UpcomingAssignmentResponse> upcomingAssignments =
                assignmentService.getUpcomingAssignments(sectionId, days);
        return ResponseEntity.ok(upcomingAssignments);
    }
}
