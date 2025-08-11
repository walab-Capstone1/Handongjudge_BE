package com.project.handongjudge.user.dto;

import lombok.Data;

@Data
public class EnrollmentResponseDTO {
    private Long id;
    private String teamId;
    private String sectionId;
    private String roleInCourse;
}

