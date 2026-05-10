package com.project.handongjudge.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE emitter 생명주기 관리.
 * submissionDbId → SseEmitter 매핑을 ConcurrentHashMap으로 유지하며,
 * timeout / completion / error 시 자동으로 맵에서 제거한다.
 */
@Service
public class SubmissionSseService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionSseService.class);
    private static final long SSE_EMITTER_TIMEOUT_MS = 60_000L;

    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Emitter 관리
    // -------------------------------------------------------------------------

    public SseEmitter register(Long submissionDbId) {
        SseEmitter emitter = new SseEmitter(SSE_EMITTER_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.debug("[SSE] timeout submissionDbId={}", submissionDbId);
            emitters.remove(submissionDbId);
        });
        emitter.onCompletion(() -> {
            log.debug("[SSE] completed submissionDbId={}", submissionDbId);
            emitters.remove(submissionDbId);
        });
        emitter.onError(e -> {
            log.debug("[SSE] error submissionDbId={}: {}", submissionDbId, e.getMessage());
            emitters.remove(submissionDbId);
        });

        emitters.put(submissionDbId, emitter);
        log.debug("[SSE] registered submissionDbId={} total={}", submissionDbId, emitters.size());
        return emitter;
    }

    public void complete(Long submissionDbId) {
        SseEmitter emitter = emitters.remove(submissionDbId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("[SSE] complete() failed submissionDbId={}: {}", submissionDbId, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 이벤트 전송
    // -------------------------------------------------------------------------

    /** testcase: { index, result } */
    public void sendTestcase(Long submissionDbId, int index, String result) {
        send(submissionDbId, "testcase", Map.of("index", index, "result", result));
    }

    /** complete: { result, passedCount, totalCount } */
    public void sendComplete(Long submissionDbId, String result, int passedCount, int totalCount) {
        send(submissionDbId, "complete",
                Map.of("result", result, "passedCount", passedCount, "totalCount", totalCount));
    }

    /** ce: { result: "CE" } */
    public void sendCe(Long submissionDbId) {
        send(submissionDbId, "ce", Map.of("result", "CE"));
    }

    /** error: { message } */
    public void sendError(Long submissionDbId, String message) {
        send(submissionDbId, "error", Map.of("message", message));
    }

    // -------------------------------------------------------------------------
    // 내부 유틸
    // -------------------------------------------------------------------------

    private void send(Long submissionDbId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(submissionDbId);
        if (emitter == null) {
            log.debug("[SSE] no emitter for submissionDbId={} event={}", submissionDbId, eventName);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json, MediaType.APPLICATION_JSON));
            log.debug("[SSE] sent event={} submissionDbId={}", eventName, submissionDbId);
        } catch (Exception e) {
            log.warn("[SSE] send failed event={} submissionDbId={}: {}", eventName, submissionDbId, e.getMessage());
            emitters.remove(submissionDbId);
        }
    }
}
