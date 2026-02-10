package com.project.handongjudge.community.controller;

import com.project.handongjudge.community.dto.NotificationResponseDto;
import com.project.handongjudge.community.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community/notifications")
@RequiredArgsConstructor
@Tag(name = "Community - Notifications", description = "커뮤니티 알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "알림 목록 조회")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestParam(required = false) Long sectionId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        Page<NotificationResponseDto> notifications = notificationService.getNotifications(userId, sectionId, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "알림 목록 조회 성공");
        result.put("data", notifications);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread")
    @Operation(summary = "읽지 않은 알림 목록 조회")
    public ResponseEntity<Map<String, Object>> getUnreadNotifications(
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        List<NotificationResponseDto> notifications = notificationService.getUnreadNotifications(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "읽지 않은 알림 조회 성공");
        result.put("data", notifications);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "읽지 않은 알림 수 조회")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        Long count = notificationService.getUnreadNotificationCount(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of("count", count));

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        notificationService.markAsRead(notificationId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "알림을 읽음 처리했습니다");

        return ResponseEntity.ok(result);
    }

    @PutMapping("/read-all")
    @Operation(summary = "모든 알림 읽음 처리")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        notificationService.markAllAsRead(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "모든 알림을 읽음 처리했습니다");

        return ResponseEntity.ok(result);
    }
}

