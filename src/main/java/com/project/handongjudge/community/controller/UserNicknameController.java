package com.project.handongjudge.community.controller;

import com.project.handongjudge.community.dto.UserNicknameDto;
import com.project.handongjudge.community.service.UserNicknameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/community/nicknames")
@RequiredArgsConstructor
@Tag(name = "Community - Nicknames", description = "커뮤니티 별명 API")
public class UserNicknameController {

    private final UserNicknameService userNicknameService;

    @GetMapping
    @Operation(summary = "내 별명 조회")
    public ResponseEntity<Map<String, Object>> getNickname(
            @RequestParam Long sectionId,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        String nickname = userNicknameService.getNickname(userId, sectionId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of("nickname", nickname != null ? nickname : ""));

        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Operation(summary = "별명 설정/수정")
    public ResponseEntity<Map<String, Object>> setNickname(
            @Valid @RequestBody UserNicknameDto dto,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        String nickname = userNicknameService.setNickname(dto, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "별명이 설정되었습니다");
        result.put("data", Map.of("nickname", nickname));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/check")
    @Operation(summary = "별명 중복 확인")
    public ResponseEntity<Map<String, Object>> checkNickname(
            @RequestParam Long sectionId,
            @RequestParam String nickname,
            Authentication authentication) {
        
        Long userId = Long.parseLong(authentication.getName());
        boolean isAvailable = userNicknameService.isNicknameAvailable(sectionId, nickname, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Map.of("isAvailable", isAvailable));

        return ResponseEntity.ok(result);
    }
}

