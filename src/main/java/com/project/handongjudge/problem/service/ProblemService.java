package com.project.handongjudge.problem.service;

import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final AssignmentProblemRepository assignmentProblemRepository;

    public List<ProblemResponse> getProblemsByAssignmentId(Long assignmentId) {
        List<AssignmentProblem> aps = assignmentProblemRepository.findByAssignmentIdOrderByProblemOrderAsc(assignmentId);
        return aps.stream()
                .map(ap -> {
                    var p = ap.getProblem();
                    return ProblemResponse.builder()
                            .id(p.getId())
                            .title(p.getTitle())
                            .description(p.getDescription())
                            .difficulty(p.getDifficulty())
                            .createdAt(p.getCreatedAt())
                            .order(ap.getProblemOrder())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
