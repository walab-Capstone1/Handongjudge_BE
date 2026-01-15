package com.project.handongjudge.user.controller;

import com.project.handongjudge.user.dto.*;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    /**
     * 현재 로그인한 사용자 정보 반환
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        String identifier = authentication.getName();
        UserDto user;

        try {
            // 먼저 ID로 시도
            Long userId = Long.parseLong(identifier);
            user = userService.getUserInfoById(userId);
        } catch (NumberFormatException e) {
            // ID가 숫자가 아닌 경우 이메일로 시도
            try {
                user = userService.getUserInfo(identifier);
            } catch (Exception ex) {
                log.error("사용자 정보 조회 실패: {}", identifier, ex);
                return buildErrorResponse("사용자를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: {}", identifier, e);
            return buildErrorResponse("사용자를 찾을 수 없습니다.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "사용자 정보 조회 성공");
        response.put("data", user);

        return ResponseEntity.ok(response);
    }

    /**
     * 로그인한 사용자의 대시보드에 표시할 과목 목록 반환
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardCourses(Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            List<DashboardCourseDto> courses = userService.getDashboardCourses(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "대시보드 과목 조회 성공");
            response.put("data", courses);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("대시보드 조회 실패: {}", authentication.getName(), e);
            return buildErrorResponse("대시보드 정보를 가져오지 못했습니다.");
        }
    }

    /**
     * 공통 에러 응답 생성
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }

    // enroll course
    @PostMapping("/enroll")
    public ResponseEntity<EnrollmentResponseDTO> enrollCourse(@RequestBody EnrollmentRequestDTO request) {
        EnrollmentResponseDTO response = userService.enrollCourse(request);
        return ResponseEntity.ok(response);
    }

    // UserController.java에 추가
    /**
     * 교수가 담당하는 모든 분반의 학생 목록 조회
     */
    @GetMapping("/instructor/students")
    public ResponseEntity<Map<String, Object>> getInstructorStudents(Authentication authentication) {
        try {
            Long instructorId = Long.parseLong(authentication.getName());
            List<StudentDto> students = userService.getStudentsByInstructor(instructorId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "담당 학생 목록 조회 성공");
            response.put("data", students);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("담당 학생 목록 조회 실패: {}", authentication.getName(), e);
            return buildErrorResponse("학생 목록을 가져오지 못했습니다.");
        }
    }

    /**
     * 특정 분반의 학생 목록 조회
     */
    @GetMapping("/sections/{sectionId}/students")
    public ResponseEntity<Map<String, Object>> getSectionStudents(
            @PathVariable Long sectionId,
            Authentication authentication) {
        try {
            List<StudentDto> students = userService.getStudentsBySection(sectionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "분반 학생 목록 조회 성공");
            response.put("data", students);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("분반 학생 목록 조회 실패: sectionId={}", sectionId, e);
            return buildErrorResponse("학생 목록을 가져오지 못했습니다.");
        }
    }

    // 공지사항 읽음 처리
    @PostMapping("/read/notice/{noticeId}")
    public ResponseEntity<Map<String, Object>> markNoticeAsRead(
            @PathVariable Long noticeId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            userService.markNoticeAsRead(userId, noticeId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "공지사항을 읽음 처리했습니다.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error marking notice as read", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // 과제 읽음 처리
    @PostMapping("/read/assignment/{assignmentId}")
    public ResponseEntity<Map<String, Object>> markAssignmentAsRead(
            @PathVariable Long assignmentId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            userService.markAssignmentAsRead(userId, assignmentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "과제를 읽음 처리했습니다.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error marking assignment as read", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    /**
     * 특정 학생의 특정 분반 과제 진도율 조회
     */
    @GetMapping("/studen    sts/{userId}/sections/{sectionId}/assignments-progress")
    public ResponseEntity<Map<String, Object>> getStudentAssignmentsProgress(
            @PathVariable Long userId,
            @PathVariable Long sectionId,
            Authentication authentication) {
        try {
            Long requestUserId = Long.parseLong(authentication.getName());

            // 교수는 자신이 담당하는 분반의 학생만, 학생은 자기 자신의 정보만 조회 가능
            User requestUser = userRepository.findById(requestUserId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 권한 검증 로직: 교수이거나 시스템 관리자이거나 자기 자신인 경우만 조회 가능
            boolean isAuthorized = requestUser.getRole() == User.Role.ADMIN ||
                    requestUser.getRole() == User.Role.SUPER_ADMIN ||
                    requestUserId.equals(userId);
            
            if (!isAuthorized) {
                return buildErrorResponse("권한이 없습니다.");
            }

            List<StudentAssignmentProgressDto> progressList = userService.getStudentAssignmentsProgress(userId, sectionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "학생 과제 진도율 조회 성공");
            response.put("data", progressList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("학생 과제 진도율 조회 실패: userId={}, sectionId={}", userId, sectionId, e);
            return buildErrorResponse("과제 진도율을 가져오지 못했습니다.");
        }
    }
    /**
     * 특정 학생의 특정 과제의 문제별 상태 조회
     */
    @GetMapping("/students/{userId}/sections/{sectionId}/assignments/{assignmentId}/problems-status")
    public ResponseEntity<Map<String, Object>> getStudentAssignmentProblemsStatus(
            @PathVariable Long userId,
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication) {
        try {
            Long requestUserId = Long.parseLong(authentication.getName());

            // 권한 검증
            User requestUser = userRepository.findById(requestUserId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 권한 검증: 교수이거나 시스템 관리자이거나 자기 자신인 경우만 조회 가능
            boolean isAuthorized = requestUser.getRole() == User.Role.ADMIN ||
                    requestUser.getRole() == User.Role.SUPER_ADMIN ||
                    requestUserId.equals(userId);
            
            if (!isAuthorized) {
                return buildErrorResponse("권한이 없습니다.");
            }

            List<StudentProblemStatusDto> problemStatusList =
                    userService.getStudentAssignmentProblemsStatus(userId, sectionId, assignmentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "학생 문제 상태 조회 성공");
            response.put("data", problemStatusList);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("학생 문제 상태 조회 실패: userId={}, assignmentId={}", userId, assignmentId, e);
            return buildErrorResponse("문제 상태를 가져오지 못했습니다.");
        }
    }
}
