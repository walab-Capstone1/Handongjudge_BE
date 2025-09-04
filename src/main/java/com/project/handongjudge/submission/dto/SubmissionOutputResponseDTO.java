package com.project.handongjudge.submission.dto;


import com.project.handongjudge.submission.entity.Output;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class SubmissionOutputResponseDTO {

    private Long problemId;

    private Long sectionId;
    private String language;
    private String submissionId;
    private String result;      // 여기에 judgement_type_id ("AC", "WA", ...) 들어가야 함
    private List<Output> outputList;
    private LocalDateTime submittedAt;
}
