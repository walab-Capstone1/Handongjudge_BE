package com.project.handongjudge.systemadmin.service;

import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.systemadmin.dto.SubmissionDetailDto;
import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.dto.UserDto;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SystemAdminService {

    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ProblemRepository problemRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;

    /**
     * 시스템 관리자 대시보드 통계 조회
     */
    public Map<String, Long> getSuperAdminStats() {
        Map<String, Long> stats = new HashMap<>();
        
        try {
            // 전체 사용자 수
            long totalUsers = userRepository.count();
            stats.put("totalUsers", totalUsers);
            log.debug("전체 사용자 수: {}", totalUsers);
            
            // 전체 수업(Section) 수
            long totalSections = sectionRepository.count();
            stats.put("totalSections", totalSections);
            log.debug("전체 수업 수: {}", totalSections);
            
            // 전체 과제 수
            long totalAssignments = assignmentRepository.count();
            stats.put("totalAssignments", totalAssignments);
            log.debug("전체 과제 수: {}", totalAssignments);
            
            // 전체 문제 수
            long totalProblems = problemRepository.count();
            stats.put("totalProblems", totalProblems);
            log.debug("전체 문제 수: {}", totalProblems);
            
            // 시스템 공지사항 수 (아직 구현되지 않았으므로 0)
            stats.put("totalSystemNotices", 0L);
            
            // 시스템 이용안내 수 (아직 구현되지 않았으므로 0)
            stats.put("totalSystemGuides", 0L);
            
            log.info("시스템 관리자 통계 조회 완료 - 사용자: {}, 수업: {}, 과제: {}, 문제: {}", 
                    totalUsers, totalSections, totalAssignments, totalProblems);
            
        } catch (Exception e) {
            log.error("통계 조회 중 오류 발생", e);
            throw new RuntimeException("통계 조회 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
        
        return stats;
    }

    /**
     * 시스템 관리자용 모든 수업 조회
     */
    public List<DashboardCourseDto> getAllSections() {
        // EnrollmentRepository의 쿼리를 수정하여 모든 수업을 조회
        // SUPER_ADMIN은 모든 수업을 볼 수 있어야 하므로 instructorId 조건 없이 조회
        return enrollmentRepository.findAllDashboardCourses();
    }

    /**
     * 시스템 관리자용 모든 사용자 조회
     */
    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(UserDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 시스템 관리자용 모든 제출 레코드 조회
     */
    public List<SubmissionDetailDto> getAllSubmissions(int page, int size) {
        // 페이징을 위해 전체 조회 후 수동으로 페이징 처리
        // (JOIN FETCH와 Pageable을 함께 사용하기 어려우므로)
        List<Submission> allSubmissions = submissionRepository.findAllWithDetails();
        
        int start = page * size;
        int end = Math.min(start + size, allSubmissions.size());
        
        if (start >= allSubmissions.size()) {
            return List.of();
        }
        
        List<Submission> pagedSubmissions = allSubmissions.subList(start, end);
        
        return pagedSubmissions.stream()
                .map(SubmissionDetailDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 시스템 관리자용 전체 제출 수 조회
     */
    public long getTotalSubmissionsCount() {
        return submissionRepository.count();
    }
}

