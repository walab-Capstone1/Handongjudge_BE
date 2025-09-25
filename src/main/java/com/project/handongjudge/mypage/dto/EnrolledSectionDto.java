package com.project.handongjudge.mypage.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrolledSectionDto {
    private Long sectionId;
    private String courseTitle;
    private String sectionNumber;
    private String instructorName;
    private int assignmentCount;
    private int completedAssignments;
    private int noticeCount;
    private int unreadNotices;

    public static EnrolledSectionDto from(com.project.handongjudge.user.entity.Enrollment enrollment) {
        return EnrolledSectionDto.builder()
                .sectionId(enrollment.getSection().getId())
                .courseTitle(enrollment.getSection().getCourse().getTitle())
                .sectionNumber(enrollment.getSection().getSectionNumber().toString())
                .instructorName(enrollment.getSection().getInstructor().getName())
                .assignmentCount(0) // TODO: 과제 수 계산
                .completedAssignments(0) // TODO: 완료된 과제 수 계산
                .noticeCount(0) // TODO: 공지사항 수 계산
                .unreadNotices(0) // TODO: 읽지 않은 공지사항 수 계산
                .build();
    }
}