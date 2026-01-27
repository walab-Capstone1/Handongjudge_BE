package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class SectionRoleListResponse {
    private Long sectionId;
    private String role;
    private SectionInfoDto sectionInfo;
}

