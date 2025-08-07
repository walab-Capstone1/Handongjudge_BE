package com.project.handongjudge.submission.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Getter
@Setter
@Builder
public class SubmissionResponseDTO {
    private Long id;
    private Long problemId;
    private Long userId;
    private Long sectionId;
    private String language;
    private String submissionId;
    private String result;      // 여기에 judgement_type_id ("AC", "WA", ...) 들어가야 함
    private String code;
    private LocalDateTime submittedAt;
}
