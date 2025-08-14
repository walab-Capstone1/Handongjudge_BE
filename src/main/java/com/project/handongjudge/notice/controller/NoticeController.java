package com.project.handongjudge.notice.controller;

import com.project.handongjudge.notice.dto.NoticeRequestDto;
import com.project.handongjudge.notice.dto.NoticeResponseDto;
import com.project.handongjudge.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class NoticeController {

    private final NoticeService noticeService;

    // 공지사항 생성
    @PostMapping
    public ResponseEntity<NoticeResponseDto> createNotice(
            @Valid @RequestBody NoticeRequestDto request,
            Authentication authentication) {

        Long instructorId = Long.valueOf(authentication.getName());
        NoticeResponseDto response = noticeService.createNotice(request, instructorId);
        return ResponseEntity.ok(response);
    }

    // 교수의 모든 분반 공지사항 조회
    @GetMapping("/instructor/my")
    public ResponseEntity<List<NoticeResponseDto>> getMyNotices(Authentication authentication) {
        Long instructorId = Long.valueOf(authentication.getName());
        List<NoticeResponseDto> notices = noticeService.getInstructorNotices(instructorId);
        return ResponseEntity.ok(notices);
    }

    // 특정 분반의 공지사항 조회 (교수 및 수강생 모두 접근 가능)
    @GetMapping("/section/{sectionId}")
    public ResponseEntity<List<NoticeResponseDto>> getSectionNotices(
            @PathVariable Long sectionId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        List<NoticeResponseDto> notices = noticeService.getSectionNotices(sectionId, userId);
        return ResponseEntity.ok(notices);
    }

    // 공지사항 수정
    @PutMapping("/{noticeId}")
    public ResponseEntity<NoticeResponseDto> updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeRequestDto request,
            Authentication authentication) {

        Long instructorId = Long.valueOf(authentication.getName());
        NoticeResponseDto response = noticeService.updateNotice(noticeId, request, instructorId);
        return ResponseEntity.ok(response);
    }

    // 공지사항 삭제
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(
            @PathVariable Long noticeId,
            Authentication authentication) {

        Long instructorId = Long.valueOf(authentication.getName());
        noticeService.deleteNotice(noticeId, instructorId);
        return ResponseEntity.ok().build();
    }

    // 공지사항 읽음 처리
    @PatchMapping("/{noticeId}/read")
    public ResponseEntity<Void> markNoticeAsRead(@PathVariable Long noticeId) {
        noticeService.markNoticeAsRead(noticeId);
        return ResponseEntity.ok().build();
    }
}