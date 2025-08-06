package com.project.handongjudge.problem.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;


@Data
public class ProblemCreateRequest {
    private String title;
    private String description;
    private MultipartFile zipFile;
}
