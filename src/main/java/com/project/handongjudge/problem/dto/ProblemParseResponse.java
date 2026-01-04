// Handongjudge_BE/src/main/java/com/project/handongjudge/problem/dto/ProblemParseResponse.java
package com.project.handongjudge.problem.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemParseResponse {
    private String title;           // 문제 제목 (ZIP 파일명 기반)
    private String description;     // 문제 설명 (markdown)
    private Double timeLimit;       // 시간 제한 (초)
    private Integer memoryLimit;    // 메모리 제한 (MB)
    private String author;          // 작성자
    private String source;          // 출처
    private String difficulty;      // 난이도
}