package com.project.handongjudge.submission.dto;

import lombok.Getter;   
import lombok.Setter;
import java.time.LocalDateTime;
@Getter
@Setter
public class SubmissionResponseDTO {
    private Long id;
    private Long problemId;
    private Long userId;
    private String language;
    private String submissionId;
    private String result;
    private LocalDateTime submittedAt;
}
