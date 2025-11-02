package com.project.handongjudge.section.controller;

import com.project.handongjudge.section.dto.SectionInfoDto;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;
import com.project.handongjudge.section.dto.SectionWithCourseRequest;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.service.SectionService;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;  // ← 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;  // ← 추가

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
    // 수업 활성화/비활성화 엔드포인트 추가
    @PatchMapping("/{sectionId}/active")
    public ResponseEntity<Map<String, Object>> toggleSectionActive(
            @PathVariable Long sectionId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        try {
            Boolean active = request.get("active");
            if (active == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "active 필드가 필요합니다.")
                );
            }

            Section section = sectionService.toggleSectionActive(sectionId, active);

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
}