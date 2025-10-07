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
    private Long noticeCount;
    private Long studentCount;
    private LocalDateTime createdAt;
}