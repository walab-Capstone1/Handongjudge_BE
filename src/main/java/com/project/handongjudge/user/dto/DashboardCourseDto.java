// 2. DashboardCourseDto 수정 - year와 semester 필드 추가
// Handongjudge_BE/src/main/java/com/project/handongjudge/user/dto/DashboardCourseDto.java

package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DashboardCourseDto {
    private Long courseId;
    private String courseTitle;
    private Long sectionId;
    private Integer sectionNumber;
    private String instructorName;
    private Long newNoticeCount;
    private Long newAssignmentCount;
    private Long assignmentCount;
    private Long noticeCount;
    private Long studentCount;
    private LocalDateTime createdAt;
    private Integer year;        // 추가
    private String semester;     // 추가
    private String enrollmentCode;  // 추가
    private Boolean active;  // 추가


}