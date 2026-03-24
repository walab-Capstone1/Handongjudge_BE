package com.project.handongjudge.quiz.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 코딩 퀴즈 시험 세션 관리 (Redis 기반)
 *
 * Redis key: quiz:session:{quizId}:{userId} = clientSessionId
 * TTL: 90초 (heartbeat 30초 주기로 연장)
 *
 * 정책:
 * - 유저당 quizId별 1개 세션만 점유 가능
 * - 다른 환경에서 이미 진행 중이면 CONFLICT 반환
 * - 사용자가 명시적으로 takeover 요청 시 기존 세션 교체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizSessionService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration SESSION_TTL = Duration.ofSeconds(90);
    private static final String KEY_PREFIX = "quiz:session:";

    public enum EnterResult {
        OK,           // 정상 입장
        CONFLICT      // 다른 환경에서 이미 진행 중
    }

    /**
     * 시험 페이지 진입 시도
     * @return OK(입장 성공) or CONFLICT(다른 세션 존재)
     */
    public EnterResult enter(Long quizId, Long userId, String clientSessionId) {
        String key = buildKey(quizId, userId);
        String existing = redisTemplate.opsForValue().get(key);

        if (existing == null || existing.equals(clientSessionId)) {
            // 빈 자리 or 자기 자신 → 입장
            redisTemplate.opsForValue().set(key, clientSessionId, SESSION_TTL);
            log.debug("Quiz session entered: quizId={}, userId={}", quizId, userId);
            return EnterResult.OK;
        }

        log.info("Quiz session conflict: quizId={}, userId={}, existing={}, new={}",
                quizId, userId, existing, clientSessionId);
        return EnterResult.CONFLICT;
    }

    /**
     * 기존 세션을 강제로 이어받기 (사용자가 "여기서 계속하기" 선택 시)
     */
    public void takeover(Long quizId, Long userId, String clientSessionId) {
        String key = buildKey(quizId, userId);
        redisTemplate.opsForValue().set(key, clientSessionId, SESSION_TTL);
        log.info("Quiz session taken over: quizId={}, userId={}", quizId, userId);
    }

    /**
     * Heartbeat - TTL 연장. 현재 세션이 유효한지도 확인
     * @return true=유효, false=다른 세션에 탈취됨
     */
    public boolean heartbeat(Long quizId, Long userId, String clientSessionId) {
        String key = buildKey(quizId, userId);
        String current = redisTemplate.opsForValue().get(key);

        if (clientSessionId.equals(current)) {
            redisTemplate.expire(key, SESSION_TTL);
            return true;
        }

        log.warn("Quiz session heartbeat failed (taken over): quizId={}, userId={}", quizId, userId);
        return false;
    }

    /**
     * 시험 종료/페이지 이탈 시 세션 해제
     */
    public void exit(Long quizId, Long userId, String clientSessionId) {
        String key = buildKey(quizId, userId);
        String current = redisTemplate.opsForValue().get(key);

        if (clientSessionId.equals(current)) {
            redisTemplate.delete(key);
            log.debug("Quiz session released: quizId={}, userId={}", quizId, userId);
        }
    }

    private String buildKey(Long quizId, Long userId) {
        return KEY_PREFIX + quizId + ":" + userId;
    }
}
