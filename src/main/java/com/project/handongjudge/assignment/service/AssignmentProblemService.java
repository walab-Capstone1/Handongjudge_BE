package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.section.service.SectionRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AssignmentProblemService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final ProblemRepository problemRepository;
    private final DomjudgeService domjudgeService;
    private final SectionRoleService sectionRoleService;

    public void addProblemToAssignment(Long assignmentId, Long problemId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (!sectionRoleService.isAdmin(userId, assignment.getSection().getId())) {
            throw new IllegalArgumentException("과제에 문제를 추가할 권한이 없습니다(교수만 가능)");
        }
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));

        // 중복 체크
        Optional<AssignmentProblem> existing = assignmentProblemRepository
                .findByAssignmentIdAndProblemId(assignmentId, problemId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Problem already exists in this assignment");
        }

        // 관계 저장 (기본 배점 1점)
        AssignmentProblem relation = AssignmentProblem.builder()
                .assignment(assignment)
                .problem(problem)
                .points(1) // 기본 배점 1점
                .build();
        assignmentProblemRepository.save(relation);

        // DOMjudge Contest에 문제 등록
        Long contestId = assignment.getSection().getId(); // sectionId == contestId
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        // A~Z 중 label 자동 생성도 가능 (예시: A, B, C...)
        String label = "A";
        domjudgeService.addProblemToContest(contestId, domjudgeProblemId);
    }
    public void removeProblemFromAssignment(Long assignmentId, Long problemId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (!sectionRoleService.isAdmin(userId, assignment.getSection().getId())) {
            throw new IllegalArgumentException("과제에서 문제를 제거할 권한이 없습니다(교수만 가능)");
        }
        // 여러 개의 관계가 있을 수 있으므로 List로 조회
        List<AssignmentProblem> relations = assignmentProblemRepository
                .findAllByAssignmentIdAndProblemId(assignmentId, problemId);
        
        if (relations.isEmpty()) {
            throw new IllegalArgumentException("Assignment-Problem relation not found");
        }
        
        // 모든 중복 관계 삭제
        assignmentProblemRepository.deleteAll(relations);
        
        // DOMjudge Contest에서도 문제 제거
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));
        
        Long contestId = assignment.getSection().getId();
        String domjudgeProblemId = problem.getDomjudgeProblemId();
        
        domjudgeService.removeProblemFromContest(contestId, domjudgeProblemId);
    }
}
