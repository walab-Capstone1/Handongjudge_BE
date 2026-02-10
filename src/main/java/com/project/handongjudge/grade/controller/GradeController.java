package com.project.handongjudge.grade.controller;

import com.project.handongjudge.grade.dto.*;
import com.project.handongjudge.grade.service.GradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/assignments/{assignmentId}/grades")
public class GradeController {

    private final GradeService gradeService;

    /**
     * 학생별 문제 성적 입력/수정
     */
    @PostMapping
    public ResponseEntity<GradeResponseDTO> saveGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody GradeRequestDTO request,
            Authentication authentication) {
        
        Long tutorId = Long.parseLong(authentication.getName());
        request.setAssignmentId(assignmentId);
        
        GradeResponseDTO response = gradeService.saveGrade(request, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 일괄 성적 입력
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<GradeResponseDTO>> saveBulkGrades(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody BulkGradeRequestDTO request,
            Authentication authentication) {
        
        Long tutorId = Long.parseLong(authentication.getName());
        request.setAssignmentId(assignmentId);
        
        List<GradeResponseDTO> responses = gradeService.saveBulkGrades(request, tutorId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 과제별 모든 학생 성적 조회
     */
    @GetMapping
    public ResponseEntity<List<StudentGradeSummaryDTO>> getAssignmentGrades(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId) {
        
        List<StudentGradeSummaryDTO> grades = 
                gradeService.getAssignmentGrades(assignmentId, sectionId);
        return ResponseEntity.ok(grades);
    }

    /**
     * 학생별 과제 성적 조회
     */
    @GetMapping("/students/{userId}")
    public ResponseEntity<StudentGradeSummaryDTO> getStudentGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long userId) {
        
        StudentGradeSummaryDTO grade = gradeService.getStudentGrade(assignmentId, userId);
        return ResponseEntity.ok(grade);
    }

    /**
     * 문제별 학생 성적 조회
     */
    @GetMapping("/problems/{problemId}/students/{userId}")
    public ResponseEntity<GradeResponseDTO> getProblemGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            @PathVariable Long userId) {
        
        GradeResponseDTO grade = gradeService.getProblemGrade(assignmentId, problemId, userId);
        return ResponseEntity.ok(grade);
    }

    /**
     * 성적 삭제
     */
    @DeleteMapping("/{gradeId}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long gradeId) {
        gradeService.deleteGrade(gradeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 과제의 문제 배점 설정
     */
    @PutMapping("/problems/{problemId}/points")
    public ResponseEntity<Void> setProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            @RequestBody Map<String, Integer> request,
            Authentication authentication) {
        
        // 권한 확인은 Service에서 처리
        Integer points = request.get("points");
        
        if (points == null) {
            return ResponseEntity.badRequest().build();
        }
        
        gradeService.setProblemPoints(assignmentId, problemId, points);
        return ResponseEntity.ok().build();
    }

    /**
     * 과제의 모든 문제 배점 일괄 설정
     */
    @PutMapping("/points/bulk")
    public ResponseEntity<Void> setBulkProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody Map<Long, Integer> problemPoints,
            Authentication authentication) {
        
        // 권한 확인은 Service에서 처리
        gradeService.setBulkProblemPoints(assignmentId, problemPoints);
        return ResponseEntity.ok().build();
    }
}

