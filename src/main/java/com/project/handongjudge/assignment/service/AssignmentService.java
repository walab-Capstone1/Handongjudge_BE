package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.problem.service.DomjudgeService;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.handongjudge.assignment.dto.AssignmentProblemsResponse;
import com.project.handongjudge.problem.dto.ProblemDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;
    private final DomjudgeService domjudgeService;

    public AssignmentResponse createAssignment(Long sectionId, AssignmentRequest request, Long userId) {
        // 1. Section 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // 2. Assignment 엔티티 생성 및 저장
        Assignment assignment = Assignment.builder()
                .section(section)
                .assignmentNumber(request.getAssignmentNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 3. Problem 연결 및 DOMjudge 등록
        if (request.getProblemIds() != null && !request.getProblemIds().isEmpty()) {
            List<AssignmentProblem> assignmentProblems = new ArrayList<>();
            int order = 1;

            for (Long problemId : request.getProblemIds()) {
                Problem problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new IllegalArgumentException("문제 ID 없음: " + problemId));

                // AssignmentProblem 생성
                AssignmentProblem ap = AssignmentProblem.builder()
                        .assignment(savedAssignment)
                        .problem(problem)
                        .problemOrder(order++)
                        .build();
                assignmentProblems.add(ap);

                // DOMjudge 등록
                Long contestId = section.getId(); // sectionId == contestId
                String domjudgeProblemId = problem.getDomjudgeProblemId();
                domjudgeService.addProblemToContest(contestId, domjudgeProblemId); // label 제거됨
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

    public AssignmentProblemsResponse getAssignmentProblems(Long assignmentId) {
        List<Problem> problems = problemRepository.findByAssignmentId(assignmentId);

        // Entity를 DTO로 변환
        List<ProblemDto> problemDtos = problems.stream()
                .map(this::convertToProblemDto)
                .collect(Collectors.toList());

        return AssignmentProblemsResponse.builder()
                .id(assignmentId)
                .problems(problemDtos)
                .build();
    }

    private ProblemDto convertToProblemDto(Problem problem) {
        return ProblemDto.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .domjudgeProblemId(problem.getDomjudgeProblemId())
                .createdAt(problem.getCreatedAt())
                .build();
    }

    public AssignmentResponse getAssignmentInfo(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        return toResponse(assignment);
    }
}
