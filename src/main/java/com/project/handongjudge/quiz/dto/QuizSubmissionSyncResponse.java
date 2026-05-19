package com.project.handongjudge.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 퀴즈 제출 DomJudge → DB 일괄 동기화 결과
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmissionSyncResponse {
    /** result가 비어 있어 동기화 대상이었던 제출 수 */
    private int totalCandidates;
    /** DB에 result(및 필요 시 TC)가 반영된 건수 */
    private int synced;
    /** DomJudge에 아직 판정이 없거나 채점 미완료로 건너뛴 건수 */
    private int stillPending;
    /** DomJudge 조회/저장 중 오류가 난 건수 */
    private int failed;
}
