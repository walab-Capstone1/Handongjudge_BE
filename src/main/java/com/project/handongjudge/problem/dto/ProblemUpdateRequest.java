// Handongjudge_BE/src/main/java/com/project/handongjudge/problem/dto/ProblemUpdateRequest.java
package com.project.handongjudge.problem.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProblemUpdateRequest {
    private String title;
    private String description;     // 수정된 markdown 내용
    private Double timeLimit;
    private Integer memoryLimit;
    private String difficulty;     // 난이도 ("1", "2", "3", etc.)
    private String tags;            // 태그 (JSON string)
    private MultipartFile newZipFile;  // 선택적: 완전히 새로운 ZIP 파일
    private String metadataUpdated;  // "true" 또는 "false" 문자열: 메타데이터만 업데이트 여부
}