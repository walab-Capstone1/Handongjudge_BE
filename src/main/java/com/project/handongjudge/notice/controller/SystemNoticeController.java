package com.project.handongjudge.notice.controller;

import com.project.handongjudge.notice.dto.SystemNoticeRequestDto;
import com.project.handongjudge.notice.dto.SystemNoticeResponseDto;
import com.project.handongjudge.notice.service.SystemNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-notices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class SystemNoticeController {

    private final SystemNoticeService systemNoticeService;

    /**
     * 시스템 전체 공지사항 생성 (SUPER_ADMIN만)
     */
    @PostMapping
    public ResponseEntity<SystemNoticeResponseDto> createSystemNotice(
            @RequestBody SystemNoticeRequestDto requestDto,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        SystemNoticeResponseDto response = systemNoticeService.createSystemNotice(requestDto, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 활성화된 시스템 전체 공지사항 목록 조회 (모든 사용자)
     */
    @GetMapping("/active")
    public ResponseEntity<List<SystemNoticeResponseDto>> getActiveSystemNotices() {
        List<SystemNoticeResponseDto> notices = systemNoticeService.getActiveSystemNotices();
        return ResponseEntity.ok(notices);
    }

    /**
     * 모든 시스템 전체 공지사항 목록 조회 (SUPER_ADMIN만)
     */
    @GetMapping
    public ResponseEntity<List<SystemNoticeResponseDto>> getAllSystemNotices(
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<SystemNoticeResponseDto> notices = systemNoticeService.getAllSystemNotices(userId);
        return ResponseEntity.ok(notices);
    }

    /**
     * 시스템 전체 공지사항 수정 (SUPER_ADMIN만)
     */
    @PutMapping("/{noticeId}")
    public ResponseEntity<SystemNoticeResponseDto> updateSystemNotice(
            @PathVariable Long noticeId,
            @RequestBody SystemNoticeRequestDto requestDto,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        SystemNoticeResponseDto response = systemNoticeService.updateSystemNotice(noticeId, requestDto, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 시스템 전체 공지사항 삭제 (SUPER_ADMIN만)
     */
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteSystemNotice(
            @PathVariable Long noticeId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        systemNoticeService.deleteSystemNotice(noticeId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 시스템 전체 공지사항 활성화/비활성화 (SUPER_ADMIN만)
     */
    @PatchMapping("/{noticeId}/active")
    public ResponseEntity<SystemNoticeResponseDto> toggleSystemNoticeActive(
            @PathVariable Long noticeId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        SystemNoticeResponseDto response = systemNoticeService.toggleSystemNoticeActive(noticeId, active, userId);
        return ResponseEntity.ok(response);
    }
}

