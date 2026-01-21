package com.project.handongjudge.section.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
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
    
    // 수정된 제목/내용을 받기 위한 필드 - 별도 클래스 사용
    private Map<Long, NoticeEditData> noticeEdits;  // 공지사항 ID -> 수정 데이터
    private Map<Long, AssignmentEditData> assignmentEdits;  // 과제 ID -> 수정 데이터
    private Map<Long, ProblemEditData> problemEdits;  // 문제 ID -> 수정 데이터
}