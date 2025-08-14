package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardCourseDto {
    private Long courseId;
    private String courseTitle;
    private Long sectionId;
    private String instructorName;
    private Long newNoticeCount;
    private Long newAssignmentCount; // 새로운 과제 카운트 추가
}
