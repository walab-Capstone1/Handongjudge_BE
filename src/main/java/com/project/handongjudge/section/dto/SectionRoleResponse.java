package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class SectionRoleResponse {
    private Long sectionId;
    private String role; // "ADMIN", "TUTOR", "STUDENT" 또는 null
}

