package com.project.handongjudge.progress.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProgressSaveRequest {
    private Long problemId;
    private Long sectionId;
    private String language;
    private String codeString;
    private String savedAt;
}
