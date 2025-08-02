package com.project.handongjudge.user.controller;

import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import com.project.handongjudge.user.dto.DashboardCourseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
