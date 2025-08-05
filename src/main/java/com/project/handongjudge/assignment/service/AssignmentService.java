package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final ProblemRepository problemRepository;

    public AssignmentResponse createAssignment(Long sectionId, AssignmentRequest request, Long userId) {
        // 1. 과제 엔티티 생성
        Assignment assignment = Assignment.builder()
                .sectionId(sectionId)
                .assignmentNumber(request.getAssignmentNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        // 2. 과제 저장
        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 3. 연결된 문제 리스트가 있다면 AssignmentProblem으로 연결
        if (request.getProblemIds() != null && !request.getProblemIds().isEmpty()) {
            List<AssignmentProblem> assignmentProblems = new ArrayList<>();
            int order = 1;

            for (Long problemId : request.getProblemIds()) {
                Problem problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new IllegalArgumentException("문제 ID 없음: " + problemId));

                AssignmentProblem ap = AssignmentProblem.builder()
                        .assignment(savedAssignment)
                        .problem(problem)
                        .problemOrder(order++)
                        .build();

                assignmentProblems.add(ap);
            }

            assignmentProblemRepository.saveAll(assignmentProblems);
        }

        return toResponse(savedAssignment);
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .assignmentNumber(assignment.getAssignmentNumber())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .startDate(assignment.getStartDate())
                .endDate(assignment.getEndDate())
                .build();
    }

    public List<AssignmentResponse> getAssignmentsBySection(Long sectionId) {
        return assignmentRepository.findBySectionId(sectionId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AssignmentResponse getAssignment(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        return toResponse(assignment);
    }
}
