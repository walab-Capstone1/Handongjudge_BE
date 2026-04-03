package com.project.handongjudge.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 제출 시각과 마감(종료) 시각 비교.
 * <p>
 * {@code submittedAt}은 DB에 KST 벽시계({@code LocalDateTime.now(Asia/Seoul)})로 저장되고,
 * {@code dueAt}은 프론트 {@code toISOString()} → Jackson UTC 파싱으로 저장된 값이므로
 * UTC 벽시계로 해석해 {@link Instant}로 맞춘 뒤 비교한다.
 */
public final class SubmissionDeadlineComparison {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    /** CSV·화면과 동일하게 한국 시간으로 보이도록 */
    private static final DateTimeFormatter CSV_KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** ZIP 경로용 (파일시스템 안전 문자) */
    private static final DateTimeFormatter ZIP_KST = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private SubmissionDeadlineComparison() {}

    /** 제출 시각: DB KST 벽시계 → 그대로 한국 시각 문자열 */
    public static String formatSubmittedAtKstCsv(LocalDateTime kstWallClock) {
        if (kstWallClock == null) {
            return "";
        }
        return CSV_KST.format(kstWallClock.atZone(KST_ZONE).toLocalDateTime());
    }

    /** 마감·종료: DB UTC 벽시계 → 한국 시각으로 변환해 문자열 */
    public static String formatDueKstCsv(LocalDateTime utcWallClock) {
        if (utcWallClock == null) {
            return "";
        }
        return CSV_KST.format(
                utcWallClock.atZone(UTC_ZONE).withZoneSameInstant(KST_ZONE).toLocalDateTime());
    }

    /** 제출 시각 기반 ZIP 안 파일명 조각 (한국 시각) */
    public static String submittedAtKstForZipPath(LocalDateTime kstWallClock) {
        if (kstWallClock == null) {
            return "unknown_time";
        }
        return ZIP_KST.format(kstWallClock.atZone(KST_ZONE).toLocalDateTime());
    }

    public static boolean isSubmittedOnTime(LocalDateTime submittedAt, LocalDateTime dueAt) {
        if (submittedAt == null || dueAt == null) {
            return true;
        }
        Instant submittedInstant = submittedAt.atZone(KST_ZONE).toInstant();
        Instant dueInstant = dueAt.atZone(UTC_ZONE).toInstant();
        return !submittedInstant.isAfter(dueInstant);
    }

    /** 정시·조기 제출이면 빈 문자열, 지각이면 일/시간/분 텍스트 */
    public static String lateDurationText(LocalDateTime submittedAt, LocalDateTime dueAt) {
        if (submittedAt == null || dueAt == null) {
            return "";
        }
        Instant submittedInstant = submittedAt.atZone(KST_ZONE).toInstant();
        Instant dueInstant = dueAt.atZone(UTC_ZONE).toInstant();
        if (!submittedInstant.isAfter(dueInstant)) {
            return "";
        }
        long minutes = ChronoUnit.MINUTES.between(dueInstant, submittedInstant);
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("일 ");
        }
        if (hours > 0) {
            sb.append(hours).append("시간 ");
        }
        if (mins > 0 || sb.length() == 0) {
            sb.append(mins).append("분");
        }
        return sb.toString().trim();
    }

    /** 제시간·조기면 {@code null}, 지각이면 마감 후 경과(분) */
    public static Integer minutesLateIfLate(LocalDateTime submittedAt, LocalDateTime dueAt) {
        if (submittedAt == null || dueAt == null) {
            return null;
        }
        Instant submittedInstant = submittedAt.atZone(KST_ZONE).toInstant();
        Instant dueInstant = dueAt.atZone(UTC_ZONE).toInstant();
        if (!submittedInstant.isAfter(dueInstant)) {
            return null;
        }
        return (int) ChronoUnit.MINUTES.between(dueInstant, submittedInstant);
    }
}
