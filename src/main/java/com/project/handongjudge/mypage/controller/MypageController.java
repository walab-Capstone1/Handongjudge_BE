package com.project.handongjudge.mypage.controller;

import com.project.handongjudge.mypage.dto.*;
import com.project.handongjudge.mypage.service.MypageService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
@Slf4j
public class MypageController {

    private final MypageService mypageService;
    private final UserRepository userRepository;

    // ==================== 1단계: 기본 프로필 정보 ====================

    /**
     * 마이페이지용 사용자 정보 조회 (GitHub 정보 포함)
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            UserProfileDto profileDto = mypageService.getUserProfileWithGitHubInfo(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "프로필 정보 조회 성공");
            response.put("data", profileDto);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("프로필 정보 조회 실패", e);
            return buildErrorResponse("프로필 정보를 불러오는데 실패했습니다.");
        }
    }

    // ==================== 2단계: 학습 통계 대시보드 ====================

    /**
     * 사용자 학습 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            UserStatsDto statsDto = mypageService.getUserStats(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "학습 통계 조회 성공");
            response.put("data", statsDto);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("학습 통계 조회 실패", e);
            return buildErrorResponse("학습 통계를 불러오는데 실패했습니다.");
        }
    }

    /**
     * 사용자 제출 기록 조회 (최근 10개)
     */
    @GetMapping("/recent-submissions")
    public ResponseEntity<Map<String, Object>> getRecentSubmissions(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            List<SubmissionDto> submissions = mypageService.getRecentSubmissions(userId, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "최근 제출 기록 조회 성공");
            response.put("data", submissions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("최근 제출 기록 조회 실패", e);
            return buildErrorResponse("최근 제출 기록을 불러오는데 실패했습니다.");
        }
    }

    /**
     * 사용자 수강 과목 현황 조회
     */
    @GetMapping("/enrolled-sections")
    public ResponseEntity<Map<String, Object>> getEnrolledSections(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            List<EnrolledSectionDto> sections = mypageService.getEnrolledSections(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "수강 과목 현황 조회 성공");
            response.put("data", sections);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("수강 과목 현황 조회 실패", e);
            return buildErrorResponse("수강 과목 현황을 불러오는데 실패했습니다.");
        }
    }

    // ==================== 3단계: GitHub 연동 정보 ====================

    /**
     * GitHub 연동 상태 조회
     */
    @GetMapping("/github-status")
    public ResponseEntity<Map<String, Object>> getGitHubStatus(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            GitHubStatusDto githubStatus = mypageService.getGitHubStatus(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "GitHub 연동 상태 조회 성공");
            response.put("data", githubStatus);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("GitHub 연동 상태 조회 실패", e);
            return buildErrorResponse("GitHub 연동 상태를 불러오는데 실패했습니다.");
        }
    }

    /**
     * GitHub 저장소 설정
     */
    @PostMapping("/github/repository")
    public ResponseEntity<Map<String, Object>> setGitHubRepository(
            Authentication authentication,
            @RequestBody GitHubRepositoryDto repositoryDto) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            mypageService.setGitHubRepository(userId, repositoryDto);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "GitHub 저장소 설정 성공");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("GitHub 저장소 설정 실패", e);
            return buildErrorResponse("GitHub 저장소 설정에 실패했습니다.");
        }
    }

    // ==================== 4단계: 자동 커밋 기능 ====================

    /**
     * 자동 커밋 설정 토글
     */
    @PostMapping("/github/auto-commit")
    public ResponseEntity<Map<String, Object>> toggleAutoCommit(
            Authentication authentication,
            @RequestBody Map<String, Boolean> request) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);
            Boolean enabled = request.get("enabled");

            mypageService.setAutoCommitEnabled(userId, enabled);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", enabled ? "자동 커밋이 활성화되었습니다" : "자동 커밋이 비활성화되었습니다");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("자동 커밋 설정 실패", e);
            return buildErrorResponse("자동 커밋 설정에 실패했습니다.");
        }
    }

    /**
     * 문제 해결 시 자동 커밋 실행
     */
    @PostMapping("/github/auto-commit/submit")
    public ResponseEntity<Map<String, Object>> executeAutoCommit(
            Authentication authentication,
            @RequestBody AutoCommitRequestDto request) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            AutoCommitResultDto result = mypageService.executeAutoCommit(userId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "자동 커밋 실행 성공");
            response.put("data", result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("자동 커밋 실행 실패", e);
            return buildErrorResponse("자동 커밋 실행에 실패했습니다.");
        }
    }

    /**
     * 커밋 히스토리 조회
     */
    @GetMapping("/github/commits")
    public ResponseEntity<Map<String, Object>> getCommitHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            List<CommitHistoryDto> commits = mypageService.getCommitHistory(userId, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "커밋 히스토리 조회 성공");
            response.put("data", commits);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("커밋 히스토리 조회 실패", e);
            return buildErrorResponse("커밋 히스토리를 불러오는데 실패했습니다.");
        }
    }

    // ==================== 5단계: 고급 통계 및 시각화 ====================

    /**
     * 상세 학습 통계 조회 (차트용 데이터)
     */
    @GetMapping("/stats/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedStats(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            DetailedStatsDto detailedStats = mypageService.getDetailedStats(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "상세 학습 통계 조회 성공");
            response.put("data", detailedStats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("상세 학습 통계 조회 실패", e);
            return buildErrorResponse("상세 학습 통계를 불러오는데 실패했습니다.");
        }
    }

    /**
     * 학습 진행도 조회
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getLearningProgress(Authentication authentication) {
        try {
            String identifier = authentication.getName();
            Long userId = getUserIdFromAuthentication(identifier);

            LearningProgressDto progress = mypageService.getLearningProgress(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "학습 진행도 조회 성공");
            response.put("data", progress);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("학습 진행도 조회 실패", e);
            return buildErrorResponse("학습 진행도를 불러오는데 실패했습니다.");
        }
    }

    // ===== 헬퍼 메서드들 =====

    private Long getUserIdFromAuthentication(String identifier) {
        try {
            return Long.parseLong(identifier);
        } catch (NumberFormatException e) {
            // ID가 숫자가 아닌 경우 이메일로 조회
            User user = userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + identifier));
            return user.getId();
        }
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.badRequest().body(response);
    }
}