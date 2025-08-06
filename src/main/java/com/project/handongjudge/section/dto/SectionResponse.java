package com.project.handongjudge.section.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SectionResponse {
    private Long id;
    private Long courseId;
    private Long instructorId;
    private Integer sectionNumber;
}
