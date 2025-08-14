package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// dto/SectionInfoDto.java
@Getter
@AllArgsConstructor
@Builder
public class SectionInfoDto {
    private Long sectionId;
    private Integer sectionNumber;
    private String courseTitle;
    private String instructorName;
}