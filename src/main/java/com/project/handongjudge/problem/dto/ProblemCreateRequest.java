// Handongjudge_BE/src/main/java/com/project/handongjudge/problem/dto/ProblemCreateRequest.java
package com.project.handongjudge.problem.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Getter
@Setter
public class ProblemCreateRequest {
    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    private String tags;  // JSON string
    private String difficulty;  // "1", "2", "3"
    private String timeLimit;
    private String memoryLimit;
    private String sampleInputs;  // JSON string
    private MultipartFile zipFile;  // 선택적
    private List<MultipartFile> testcaseFiles;  // 테스트케이스 파일들
}