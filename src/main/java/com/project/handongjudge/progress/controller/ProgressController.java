package com.project.handongjudge.progress.controller;

import com.project.handongjudge.progress.dto.ProgressSaveRequest;
import com.project.handongjudge.progress.service.ProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class ProgressController {

    private final ProgressService progressService;

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveProgress(
            Authentication authentication,
            @RequestBody ProgressSaveRequest request) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            progressService.saveProgress(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "진행 상황이 저장되었습니다");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("진행 상황 저장 실패", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "진행 상황 저장에 실패했습니다: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/load")
    public ResponseEntity<Map<String, Object>> loadProgress(
            Authentication authentication,
            @RequestParam Long problemId,
            @RequestParam Long sectionId,
            @RequestParam String language) {
        try {
            Long userId = Long.parseLong(authentication.getName());
            String code = progressService.loadProgress(userId, problemId, sectionId, language);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("codeString", code);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("진행 상황 불러오기 실패", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "진행 상황 불러오기에 실패했습니다: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
