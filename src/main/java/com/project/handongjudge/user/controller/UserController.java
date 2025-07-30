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
        String email = authentication.getName();
        UserDto user = userService.getUserInfo(email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "사용자 정보 조회 성공");
        response.put("data", user);

        return ResponseEntity.ok(response);
    }
}