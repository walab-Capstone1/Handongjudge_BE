// 2. DashboardCourseDto 수정 - year와 semester 필드 추가
// Handongjudge_BE/src/main/java/com/project/handongjudge/user/dto/DashboardCourseDto.java

package com.project.handongjudge.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

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

    /** 이 수업에서의 내 역할: INSTRUCTOR(강사, 전체 관리) / ADMIN / TUTOR(조회·일부만 가능) */
    @Setter
    private String roleInSection;

    /** 17개 필드만 받는 생성자 (roleInSection은 null, 쿼리 결과용) */
    public DashboardCourseDto(Long courseId, String courseTitle, Long sectionId, Integer sectionNumber,
            String instructorName, Long newNoticeCount, Long newAssignmentCount, Long assignmentCount,
            Long noticeCount, Long studentCount, LocalDateTime createdAt, Integer year, String semester,
            String enrollmentCode, Boolean active) {
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.sectionId = sectionId;
        this.sectionNumber = sectionNumber;
        this.instructorName = instructorName;
        this.newNoticeCount = newNoticeCount;
        this.newAssignmentCount = newAssignmentCount;
        this.assignmentCount = assignmentCount;
        this.noticeCount = noticeCount;
        this.studentCount = studentCount;
        this.createdAt = createdAt;
        this.year = year;
        this.semester = semester;
        this.enrollmentCode = enrollmentCode;
        this.active = active;
        this.roleInSection = null;
    }
}