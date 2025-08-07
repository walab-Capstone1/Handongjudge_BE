package com.project.handongjudge.assignment.dto;

import java.util.List;

import com.project.handongjudge.problem.dto.ProblemDto;

import lombok.Builder;  
import lombok.Data;

@Data
@Builder
public class AssignmentProblemsResponse {       
    private Long id;
    private String assignmentNumber;
    private String title;
    private String description;
    private List<ProblemDto> problems;
}
