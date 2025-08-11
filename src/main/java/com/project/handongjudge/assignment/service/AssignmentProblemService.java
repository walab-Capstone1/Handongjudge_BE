package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentProblemService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final ProblemRepository problemRepository;
    private final DomjudgeService domjudgeService;

    public void addProblemToAssignment(Long assignmentId, Long problemId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));

        // 관계 저장
        AssignmentProblem relation = AssignmentProblem.builder()
                .assignment(assignment)
                .problem(problem)
                .build();
        assignmentProblemRepository.save(relation);

        // DOMjudge Contest에 문제 등록
        Long contestId = assignment.getSection().getId(); // sectionId == contestId
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        // A~Z 중 label 자동 생성도 가능 (예시: A, B, C...)
        String label = "A";
        domjudgeService.addProblemToContest(contestId, domjudgeProblemId);
    }
}
