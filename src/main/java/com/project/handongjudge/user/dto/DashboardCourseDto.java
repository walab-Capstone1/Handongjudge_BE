package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
}