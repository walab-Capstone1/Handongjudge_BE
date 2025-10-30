package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionWithCourseRequest {
    private String courseTitle;
    private Long instructorId;
    private Integer sectionNumber;
    private Integer year;
    private String semester;
}