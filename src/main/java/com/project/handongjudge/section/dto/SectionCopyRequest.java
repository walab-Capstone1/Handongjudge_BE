package com.project.handongjudge.section.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SectionCopyRequest {
    private Integer sectionNumber;
    private Integer year;
    private String semester;
    private String courseTitle;
    private String description;
    private Boolean copyNotices;
    private Boolean copyAssignments;
    private List<Long> selectedNoticeIds;
    private List<Long> selectedAssignmentIds;  // 추가: 선택된 과제 ID 목록
    private Map<Long, List<Long>> assignmentProblems;  // 추가: 과제별 선택된 문제 ID 목록
}