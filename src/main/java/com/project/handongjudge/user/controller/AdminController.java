package com.project.handongjudge.user.controller;

import com.project.handongjudge.user.dto.AdminDashboardStatsDto;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;

    /**
     * 관리자 대시보드 통계 조회
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getAdminDashboardStats(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ADMIN 또는 SUPER_ADMIN 권한 확인
            if (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("관리자 권한이 없는 사용자의 통계 조회 시도 - userId: {}, role: {}", userId, user.getRole());
                return buildErrorResponse("관리자 권한이 필요합니다.");
            }

            AdminDashboardStatsDto stats = userService.getAdminDashboardStats(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "대시보드 통계 조회 성공");
            response.put("data", stats);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("대시보드 통계 조회 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("대시보드 통계를 가져오지 못했습니다: " + e.getMessage());
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
}

