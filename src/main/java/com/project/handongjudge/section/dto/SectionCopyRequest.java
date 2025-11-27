package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionCopyRequest {
    private Integer sectionNumber;
    private Integer year;
    private String semester;
}