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
    /** 담당 교수 user id */
    private Long instructorId;
    /** 현재 로그인 사용자가 이 분반 교수/TA(매니저)인지 */
    private Boolean isCurrentUserSectionStaff;
    private String enrollmentCode;  // 추가
    private Boolean active;  // 추가

}