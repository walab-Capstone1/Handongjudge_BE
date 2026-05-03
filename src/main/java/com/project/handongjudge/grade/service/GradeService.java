package com.project.handongjudge.grade.service;

import com.project.handongjudge.grade.dto.*;

import java.util.List;
import java.util.Map;

public interface GradeService {
    // 학생별 문제 성적 입력/수정
    GradeResponseDTO saveGrade(GradeRequestDTO request, Long tutorId);
    
    // 일괄 성적 입력
    List<GradeResponseDTO> saveBulkGrades(BulkGradeRequestDTO request, Long tutorId);
    
    // 과제별 모든 학생 성적 조회
    List<StudentGradeSummaryDTO> getAssignmentGrades(Long assignmentId, Long sectionId);
    
    // 학생별 과제 성적 조회
    StudentGradeSummaryDTO getStudentGrade(Long assignmentId, Long userId);
    
    // 문제별 학생 성적 조회
    GradeResponseDTO getProblemGrade(Long assignmentId, Long problemId, Long userId);
    
    // 성적 삭제
    void deleteGrade(Long gradeId);
    
    // 과제의 문제 배점 설정
    void setProblemPoints(Long assignmentId, Long problemId, Integer points);
    
    // 과제의 모든 문제 배점 일괄 설정
    void setBulkProblemPoints(Long assignmentId, Map<Long, Integer> problemPoints);

    /**
     * 과제 제출이 AC로 확정된 뒤, 해당 문제에 반려(Grade)가 있으면 해제하고 수동 0점을 풀어 자동 채점 반영.
     */
    void clearRejectedOnAcForAssignmentProblem(Long userId, Long sectionId, Long problemId);
}

