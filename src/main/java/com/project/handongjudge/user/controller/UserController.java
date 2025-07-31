package com.project.handongjudge.user.controller;

import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class UserController {

    private final UserService userService;

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
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "사용자를 찾을 수 없습니다.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: {}", identifier, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "사용자를 찾을 수 없습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "사용자 정보 조회 성공");
        response.put("data", user);

        return ResponseEntity.ok(response);
    }
}