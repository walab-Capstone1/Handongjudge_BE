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
    default List<StudentGradeSummaryDTO> getAssignmentGrades(Long assignmentId, Long sectionId) {
        return getAssignmentGrades(assignmentId, sectionId, false);
    }

    List<StudentGradeSummaryDTO> getAssignmentGrades(Long assignmentId, Long sectionId, boolean includeTestCaseResults);

    // 학생별 과제 성적 조회
    default StudentGradeSummaryDTO getStudentGrade(Long assignmentId, Long userId) {
        return getStudentGrade(assignmentId, userId, false);
    }

    StudentGradeSummaryDTO getStudentGrade(Long assignmentId, Long userId, boolean includeTestCaseResults);
    
    // 문제별 학생 성적 조회
    GradeResponseDTO getProblemGrade(Long assignmentId, Long problemId, Long userId);
    
    // 성적 삭제
    void deleteGrade(Long gradeId);
    
    // 과제의 문제 배점 설정
    void setProblemPoints(Long assignmentId, Long problemId, Integer points);
    
    // 과제의 모든 문제 배점 일괄 설정
    void setBulkProblemPoints(Long assignmentId, Map<Long, Integer> problemPoints);
}

