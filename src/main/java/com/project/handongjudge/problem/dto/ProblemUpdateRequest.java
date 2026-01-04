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
    private MultipartFile newZipFile;  // 선택적: 완전히 새로운 ZIP 파일
}