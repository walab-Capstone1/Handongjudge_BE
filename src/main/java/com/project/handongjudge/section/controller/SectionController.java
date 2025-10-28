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

    @PostMapping
    public ResponseEntity<SectionResponse> createSection(@RequestBody SectionRequest request) {
        SectionResponse response = sectionService.createSection(request);
        return ResponseEntity.ok(response);
    }
    // SectionController.java에 추가
    @GetMapping("/{sectionId}")
    public ResponseEntity<SectionInfoDto> getSectionInfo(@PathVariable Long sectionId) {
        try {
            SectionInfoDto sectionInfo = sectionService.getSectionInfo(sectionId);
            return ResponseEntity.ok(sectionInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    // 5. 수업 참가 엔드포인트 추가
// Handongjudge_BE/src/main/java/com/project/handongjudge/section/controller/SectionController.java

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

            // Enrollment 생성
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Enrollment enrollment = Enrollment.builder()
                    .user(user)
                    .section(section)
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
    // SectionController.java에 추가
    @PostMapping("/with-course")
    public ResponseEntity<SectionResponse> createSectionWithCourse(@RequestBody SectionWithCourseRequest request) {
        SectionResponse response = sectionService.createSectionWithCourse(request);
        return ResponseEntity.ok(response);
    }
}
