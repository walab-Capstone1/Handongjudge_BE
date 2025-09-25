package com.project.handongjudge.mypage.service;

import com.project.handongjudge.mypage.dto.*;
import com.project.handongjudge.mypage.repository.MypageRepository;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MypageService {

    private final MypageRepository mypageRepository;
    private final UserRepository userRepository;

    // ==================== 1단계: 기본 프로필 정보 ====================

    /**
     * GitHub 정보를 포함한 사용자 프로필 조회
     */
    public UserProfileDto getUserProfileWithGitHubInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        return UserProfileDto.from(user);
    }

    // ==================== 2단계: 학습 통계 대시보드 ====================

    /**
     * 사용자 학습 통계 조회
     */
    public UserStatsDto getUserStats(Long userId) {
        // 총 제출 수
        int totalSubmissions = mypageRepository.countTotalSubmissionsByUserId(userId);

        // 정답 제출 수
        int correctSubmissions = mypageRepository.countCorrectSubmissionsByUserId(userId);

        // 정답률 계산
        double accuracyRate = totalSubmissions > 0 ? (double) correctSubmissions / totalSubmissions * 100 : 0;

        // 해결한 문제 수 (중복 제거)
        int solvedProblems = mypageRepository.countSolvedProblemsByUserId(userId);

        // 수강 중인 분반 수
        int enrolledSections = mypageRepository.countEnrolledSectionsByUserId(userId);

        // 언어별 통계 - null 값 안전 처리
        Map<String, Integer> languageStats = new HashMap<>();
        try {
            Map<String, Integer> rawLanguageStats = mypageRepository.getLanguageStatsByUserId(userId);
            if (rawLanguageStats != null) {
                for (Map.Entry<String, Integer> entry : rawLanguageStats.entrySet()) {
                    String language = entry.getKey();
                    Integer count = entry.getValue();

                    // null 키와 빈 문자열 키 제거
                    if (language != null && !language.trim().isEmpty() && count != null && count > 0) {
                        languageStats.put(language, count);
                    }
                }
            }
        } catch (Exception e) {
            log.error("언어별 통계 조회 실패: userId={}", userId, e);
            // 실패 시 빈 Map 사용
            languageStats = new HashMap<>();
        }

        // 최근 활동 (최근 5개)
        List<UserStatsDto.RecentActivityDto> recentActivities = getRecentActivities(userId, 5);

        return UserStatsDto.builder()
                .totalSubmissions(totalSubmissions)
                .correctSubmissions(correctSubmissions)
                .accuracyRate(Math.round(accuracyRate * 100.0) / 100.0) // 소수점 2자리
                .solvedProblems(solvedProblems)
                .enrolledSections(enrolledSections)
                .languageStats(languageStats) // 안전하게 처리된 Map
                .recentActivities(recentActivities)
                .build();
    }

    /**
     * 최근 제출 기록 조회
     */
    public List<SubmissionDto> getRecentSubmissions(Long userId, int limit) {
        List<Submission> submissions = mypageRepository.getRecentSubmissionsByUserId(userId);

        // Service에서 limit 적용
        return submissions.stream()
                .limit(limit)
                .map(SubmissionDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 수강 중인 과목 현황 조회
     */
    public List<EnrolledSectionDto> getEnrolledSections(Long userId) {
        return mypageRepository.getEnrolledSectionsByUserId(userId)
                .stream()
                .map(EnrolledSectionDto::from)
                .collect(Collectors.toList());
    }

    // ==================== 3단계: GitHub 연동 정보 ====================

    /**
     * GitHub 연동 상태 조회
     */
    public GitHubStatusDto getGitHubStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        // provider가 null인 경우도 처리
        boolean isConnected = user.getProvider() != null && user.getProvider() == User.AuthProvider.GITHUB;
        String githubUsername = isConnected ? user.getName() : null;
        String githubProfileUrl = isConnected ? "https://github.com/" + user.getName() : null;

        return GitHubStatusDto.builder()
                .isConnected(isConnected)
                .githubUsername(githubUsername)
                .githubProfileUrl(githubProfileUrl)
                .autoCommitEnabled(false) // 기본값
                .build();
    }

    /**
     * GitHub 저장소 설정
     */
    @Transactional
    public void setGitHubRepository(Long userId, GitHubRepositoryDto repositoryDto) {
        // TODO: GitHub 저장소 설정 로직 구현
        // 사용자별 GitHub 저장소 정보 저장
        log.info("GitHub 저장소 설정: userId={}, repository={}", userId, repositoryDto.getRepositoryName());
    }

    // ==================== 4단계: 자동 커밋 기능 ====================

    /**
     * 자동 커밋 설정 토글
     */
    @Transactional
    public void setAutoCommitEnabled(Long userId, Boolean enabled) {
        // TODO: 자동 커밋 설정 저장 로직 구현
        log.info("자동 커밋 설정: userId={}, enabled={}", userId, enabled);
    }

    /**
     * 문제 해결 시 자동 커밋 실행
     */
    @Transactional
    public AutoCommitResultDto executeAutoCommit(Long userId, AutoCommitRequestDto request) {
        // TODO: GitHub API를 통한 자동 커밋 로직 구현
        log.info("자동 커밋 실행: userId={}, problemTitle={}", userId, request.getProblemTitle());

        return AutoCommitResultDto.builder()
                .success(true)
                .commitMessage("Solve: " + request.getProblemTitle() + " - HandongJudge")
                .commitUrl("https://github.com/example/repo/commit/abc123")
                .build();
    }

    /**
     * 커밋 히스토리 조회
     */
    public List<CommitHistoryDto> getCommitHistory(Long userId, int limit) {
        // TODO: GitHub API를 통한 커밋 히스토리 조회 로직 구현
        return Arrays.asList(
                CommitHistoryDto.builder()
                        .commitMessage("Solve: 문제1 - HandongJudge")
                        .commitDate(LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                        .problemTitle("문제1")
                        .build()
        );
    }

    // ==================== 5단계: 고급 통계 및 시각화 ====================

    /**
     * 상세 학습 통계 조회 (차트용 데이터)
     */
    public DetailedStatsDto getDetailedStats(Long userId) {
        // 최근 30일 전 날짜 계산
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);

        // 일별 제출 통계 (원시 데이터로 받아서 처리)
        List<Object[]> dailySubmissionsRaw = mypageRepository.getDailySubmissionStatsRaw(userId, startDate);
        Map<String, Integer> dailySubmissions = new HashMap<>();
        for (Object[] row : dailySubmissionsRaw) {
            LocalDateTime date = (LocalDateTime) row[0];
            Integer count = ((Number) row[1]).intValue();
            String dateStr = date.toLocalDate().toString();
            dailySubmissions.put(dateStr, count);
        }

        // 언어별 사용 통계
        Map<String, Integer> languageStats = mypageRepository.getLanguageStatsByUserId(userId);

        // 정답률 트렌드 (원시 데이터로 받아서 처리)
        List<Object[]> accuracyTrendRaw = mypageRepository.getAccuracyTrendRaw(userId, startDate);
        Map<String, Double> accuracyTrend = new HashMap<>();
        for (Object[] row : accuracyTrendRaw) {
            LocalDateTime date = (LocalDateTime) row[0];
            Double accuracy = ((Number) row[1]).doubleValue();
            String dateStr = date.toLocalDate().toString();
            accuracyTrend.put(dateStr, accuracy);
        }

        return DetailedStatsDto.builder()
                .dailySubmissions(dailySubmissions)
                .languageStats(languageStats)
                .accuracyTrend(accuracyTrend)
                .build();
    }

    /**
     * 학습 진행도 조회
     */
    public LearningProgressDto getLearningProgress(Long userId) {
        // 전체 과제 수
        int totalAssignments = mypageRepository.countTotalAssignmentsByUserId(userId);

        // 완료된 과제 수
        int completedAssignments = mypageRepository.countCompletedAssignmentsByUserId(userId);

        // 진행률 계산
        double progressRate = totalAssignments > 0 ? (double) completedAssignments / totalAssignments * 100 : 0;

        return LearningProgressDto.builder()
                .totalAssignments(totalAssignments)
                .completedAssignments(completedAssignments)
                .progressRate(Math.round(progressRate * 100.0) / 100.0)
                .build();
    }

    // ===== 헬퍼 메서드들 =====

    /**
     * 최근 활동 조회 (별도 메서드)
     */
    private List<UserStatsDto.RecentActivityDto> getRecentActivities(Long userId, int limit) {
        List<Submission> submissions = mypageRepository.getRecentSubmissionsByUserId(userId);

        // Service에서 limit 적용
        return submissions.stream()
                .limit(limit)
                .map(submission -> UserStatsDto.RecentActivityDto.builder()
                        .problemTitle(submission.getProblem().getTitle())
                        .sectionName(submission.getSection().getCourse().getTitle() + " - " + submission.getSection().getSectionNumber() + "분반")
                        .result(submission.getResult())
                        .language(submission.getLanguage())
                        .submittedAt(submission.getSubmittedAt().toString())
                        .build())
                .collect(Collectors.toList());
    }
}