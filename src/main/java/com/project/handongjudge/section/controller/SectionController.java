package com.project.handongjudge.section.controller;

import com.project.handongjudge.section.dto.SectionInfoDto;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.entity.SectionUserRole;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.service.SectionService;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;  // ← 추가
import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.project.handongjudge.section.dto.SectionCopyRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;
    private final SectionRoleService sectionRoleService;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;  // ← 추가
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<SectionResponse> createSection(@RequestBody SectionRequest request) {
        SectionResponse response = sectionService.createSection(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sectionId}")
    public ResponseEntity<SectionInfoDto> getSectionInfo(@PathVariable Long sectionId) {
        try {
            SectionInfoDto sectionInfo = sectionService.getSectionInfo(sectionId);
            return ResponseEntity.ok(sectionInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/enroll/{enrollmentCode}")
    public ResponseEntity<Map<String, Object>> enrollByCode(
            @PathVariable String enrollmentCode,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());

            // enrollmentCode로 수업 찾기
            Section section = sectionRepository.findByEnrollmentCode(enrollmentCode)
                    .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 참가 코드입니다."));

            // 이미 등록되었는지 확인
            if (enrollmentRepository.existsByUserIdAndSectionId(userId, section.getId())) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "이미 수강 신청한 분반입니다.")
                );
            }

            // User 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ===== DomJudge Team 생성 (추가) =====
            String teamId = domjudgeService.createTeam(
                    userId,
                    section.getId(),
                    user.getName()
            );

            // Enrollment 생성 (teamId 포함)
            Enrollment enrollment = Enrollment.builder()
                    .user(user)
                    .section(section)
                    .teamId(teamId)  // ← 추가
                    .joinedAt(LocalDateTime.now())
                    .build();
            enrollmentRepository.save(enrollment);

            // SectionUserRole에 STUDENT 역할 부여
            sectionRoleService.assignStudentRole(section.getId(), userId);

            // 교수에게 학생 추가 알림 발송
            notificationService.notifyStudentEnrolled(enrollment, section);
            notificationService.notifyEnrolledStudentCatchUp(userId, section.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "수강 신청이 완료되었습니다.",
                    "sectionId", section.getId(),
                    "courseTitle", section.getCourse().getTitle()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    /**
     * 수업 관리자: 수강 중인 학생 전원에 대해, 기존 활성 공지·활성 과제 알림이 없으면 생성합니다.
     * (이미 수강 중이던 학생에게 수강 전 콘텐츠 알림을 채울 때 사용)
     */
    @PostMapping("/{sectionId}/notifications/catch-up")
    public ResponseEntity<Map<String, Object>> backfillNotificationCatchUp(
            @PathVariable Long sectionId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            Map<String, Object> result = notificationService.backfillCatchUpForSection(userId, sectionId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "알림 catch-up이 완료되었습니다.",
                    "data", result
            ));
        } catch (CustomException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    // 수업 활성화/비활성화 엔드포인트 추가 (ADMIN만 가능)
    @PatchMapping("/{sectionId}/active")
    public ResponseEntity<Map<String, Object>> toggleSectionActive(
            @PathVariable Long sectionId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            Boolean active = request.get("active");
            if (active == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "active 필드가 필요합니다.")
                );
            }

            Section section = sectionService.toggleSectionActive(sectionId, active, userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", active ? "수업이 활성화되었습니다." : "수업이 비활성화되었습니다.",
                    "sectionId", section.getId(),
                    "active", section.getActive()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "서버 오류가 발생했습니다.")
            );
        }
    }
    /**
     * Section 복사 (권한 체크 포함)
     * 원본 Section을 만든 instructor만 복사 가능
     * Assignment, Problem, Notice 모두 복사
     */
    @PostMapping("/{sectionId}/copy")
    public ResponseEntity<Map<String, Object>> copySection(
            @PathVariable Long sectionId,
            @RequestBody SectionCopyRequest request,
            Authentication authentication) {
        try {
            Long instructorId = Long.parseLong(authentication.getName());
            Long newSectionId = sectionService.copySection(
                    sectionId,
                    request.getSectionNumber(),
                    request.getYear(),
                    request.getSemester(),
                    request.getCourseTitle(),
                    request.getDescription(),
                    request.getCopyNotices(),
                    request.getCopyAssignments(),
                    request.getSelectedNoticeIds(),
                    request.getSelectedAssignmentIds(),  // 추가
                    request.getAssignmentProblems(),     // 추가
                    instructorId,
                    request.getNoticeEdits(),            // 수정된 공지사항 데이터
                    request.getAssignmentEdits(),        // 수정된 과제 데이터
                    request.getProblemEdits()           // 수정된 문제 데이터
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "수업이 성공적으로 복사되었습니다.",
                    "newSectionId", newSectionId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "서버 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    // 분반 삭제
    @DeleteMapping("/{sectionId}")
    public ResponseEntity<Void> deleteSection(
            @PathVariable Long sectionId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        sectionService.deleteSection(sectionId, instructorId);
        return ResponseEntity.ok().build();
    }

    /**
     * 특정 수업에서 현재 사용자의 역할 조회
     */
    @GetMapping("/{sectionId}/my-role")
    public ResponseEntity<Map<String, Object>> getMyRoleInSection(
            @PathVariable Long sectionId,
            Authentication authentication) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            
            java.util.Optional<SectionUserRole.SectionRole> role = sectionRoleService
                    .getUserRoleInSection(userId, sectionId);
            
            String roleString = role.map(Enum::name).orElse(null);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("sectionId", sectionId);
            response.put("role", roleString);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    /**
     * 수업에 튜터 추가
     */
    @PostMapping("/{sectionId}/tutors")
    public ResponseEntity<Map<String, Object>> addTutor(
            @PathVariable Long sectionId,
            @RequestBody Map<String, Long> request,
            Authentication authentication) {
        try {
            Long adminUserId = Long.parseLong(authentication.getName());
            Long tutorUserId = request.get("userId");
            
            if (tutorUserId == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "userId가 필요합니다.")
                );
            }
            
            sectionRoleService.assignTutorRole(sectionId, tutorUserId, adminUserId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "튜터가 추가되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "서버 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 수업의 관리자 목록 조회
     */
    @GetMapping("/{sectionId}/admins")
    public ResponseEntity<Map<String, Object>> getAdmins(
            @PathVariable Long sectionId) {
        try {
            List<SectionUserRole> admins = sectionRoleService.getAdmins(sectionId);
            
            List<Map<String, Object>> adminList = admins.stream()
                    .map(admin -> {
                        Map<String, Object> adminInfo = new java.util.HashMap<>();
                        adminInfo.put("userId", admin.getUser().getId());
                        adminInfo.put("name", admin.getUser().getName());
                        adminInfo.put("email", admin.getUser().getEmail());
                        return adminInfo;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("sectionId", sectionId);
            response.put("admins", adminList);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }

    /**
     * 수업에서 튜터 제거 (역할 제거)
     */
    @DeleteMapping("/{sectionId}/tutors/{userId}")
    public ResponseEntity<Map<String, Object>> removeTutor(
            @PathVariable Long sectionId,
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            Long adminUserId = Long.parseLong(authentication.getName());
            
            sectionRoleService.removeRole(sectionId, userId, adminUserId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "튜터가 제거되었습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("success", false, "message", "서버 오류가 발생했습니다.")
            );
        }
    }

}