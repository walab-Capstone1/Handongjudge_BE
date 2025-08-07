package com.project.handongjudge.submission.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class SubmissionRequestDTO {
    private Long userId;
    private Long problemId;
    private Long sectionId;
    private String language;
    private MultipartFile code; // 파일 업로드
}
