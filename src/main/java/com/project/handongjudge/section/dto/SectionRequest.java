package com.project.handongjudge.section.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectionRequest {
    private Long courseId;
    private Long instructorId;
    private Integer sectionNumber;
}
