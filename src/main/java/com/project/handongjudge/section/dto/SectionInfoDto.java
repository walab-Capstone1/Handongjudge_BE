// SectionInfoDto에 enrollmentCode 필드 추가
// Handongjudge_BE/src/main/java/com/project/handongjudge/section/dto/SectionInfoDto.java

package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class SectionInfoDto {
    private Long sectionId;
    private Integer sectionNumber;
    private String courseTitle;
    private String instructorName;
    private String enrollmentCode;  // 추가
}