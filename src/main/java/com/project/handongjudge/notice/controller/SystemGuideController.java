package com.project.handongjudge.notice.controller;

import com.project.handongjudge.notice.dto.SystemGuideRequestDto;
import com.project.handongjudge.notice.dto.SystemGuideResponseDto;
import com.project.handongjudge.notice.service.SystemGuideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-guides")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class SystemGuideController {

    private final SystemGuideService systemGuideService;

    /**
     * 시스템 이용안내 생성 (SUPER_ADMIN만)
     */
    @PostMapping
    public ResponseEntity<SystemGuideResponseDto> createSystemGuide(
            @RequestBody SystemGuideRequestDto requestDto,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        SystemGuideResponseDto response = systemGuideService.createSystemGuide(requestDto, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 활성화된 시스템 이용안내 목록 조회 (모든 사용자)
     */
    @GetMapping("/active")
    public ResponseEntity<List<SystemGuideResponseDto>> getActiveSystemGuides(
            @RequestParam(required = false) String category) {
        List<SystemGuideResponseDto> guides;
        if (category != null && !category.isEmpty()) {
            guides = systemGuideService.getActiveSystemGuidesByCategory(category);
        } else {
            guides = systemGuideService.getActiveSystemGuides();
        }
        return ResponseEntity.ok(guides);
    }

    /**
     * 모든 시스템 이용안내 목록 조회 (SUPER_ADMIN만)
     */
    @GetMapping
    public ResponseEntity<List<SystemGuideResponseDto>> getAllSystemGuides(
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<SystemGuideResponseDto> guides = systemGuideService.getAllSystemGuides(userId);
        return ResponseEntity.ok(guides);
    }

    /**
     * 고유한 카테고리 목록 조회
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        List<String> categories = systemGuideService.getCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * 시스템 이용안내 수정 (SUPER_ADMIN만)
     */
    @PutMapping("/{guideId}")
    public ResponseEntity<SystemGuideResponseDto> updateSystemGuide(
            @PathVariable Long guideId,
            @RequestBody SystemGuideRequestDto requestDto,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        SystemGuideResponseDto response = systemGuideService.updateSystemGuide(guideId, requestDto, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 시스템 이용안내 삭제 (SUPER_ADMIN만)
     */
    @DeleteMapping("/{guideId}")
    public ResponseEntity<Void> deleteSystemGuide(
            @PathVariable Long guideId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        systemGuideService.deleteSystemGuide(guideId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 시스템 이용안내 활성화/비활성화 (SUPER_ADMIN만)
     */
    @PatchMapping("/{guideId}/active")
    public ResponseEntity<SystemGuideResponseDto> toggleSystemGuideActive(
            @PathVariable Long guideId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        SystemGuideResponseDto response = systemGuideService.toggleSystemGuideActive(guideId, active, userId);
        return ResponseEntity.ok(response);
    }
}

