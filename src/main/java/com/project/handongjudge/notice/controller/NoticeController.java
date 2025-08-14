package com.project.handongjudge.notice.controller;

import com.project.handongjudge.notice.dto.NoticeRequestDto;
import com.project.handongjudge.notice.dto.NoticeResponseDto;
import com.project.handongjudge.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class NoticeController {

    private final NoticeService noticeService;

    // 공지사항 생성
    @PostMapping
    public ResponseEntity<NoticeResponseDto> createNotice(
            @RequestBody NoticeRequestDto requestDto,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        NoticeResponseDto response = noticeService.createNotice(requestDto, instructorId);
        return ResponseEntity.ok(response);
    }

    // 특정 분반의 공지사항 목록 조회
    @GetMapping
    public ResponseEntity<List<NoticeResponseDto>> getSectionNotices(
            @RequestParam Long sectionId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<NoticeResponseDto> notices = noticeService.getSectionNotices(sectionId, userId);
        return ResponseEntity.ok(notices);
    }

    // 공지사항 수정
    @PutMapping("/{noticeId}")
    public ResponseEntity<NoticeResponseDto> updateNotice(
            @PathVariable Long noticeId,
            @RequestBody NoticeRequestDto requestDto,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        NoticeResponseDto response = noticeService.updateNotice(noticeId, requestDto, instructorId);
        return ResponseEntity.ok(response);
    }

    // 공지사항 삭제
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(
            @PathVariable Long noticeId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        noticeService.deleteNotice(noticeId, instructorId);
        return ResponseEntity.ok().build();
    }

    // 교수가 담당하는 모든 분반의 공지사항 조회
    @GetMapping("/instructor/my")
    public ResponseEntity<List<NoticeResponseDto>> getInstructorNotices(
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        List<NoticeResponseDto> notices = noticeService.getInstructorNotices(instructorId);
        return ResponseEntity.ok(notices);
    }
}