package com.project.handongjudge.problem.service;

import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final DomjudgeService domjudgeService;

    public Long createProblem(ProblemCreateRequest request) throws IOException {
        String title = request.getTitle();
        String description = request.getDescription();
        MultipartFile zipFile = request.getZipFile();

        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFile);

        Problem problem = Problem.builder()
                .title(title)
                .description(description)
                .domjudgeProblemId(domjudgeProblemId)
                .createdAt(LocalDateTime.now())
                .build();   

        problemRepository.save(problem);
        return problem.getId();
    }

    public List<Problem> getProblemsByAssignmentId(Long assignmentId) {
        List<Problem> problems = problemRepository.findByAssignmentId(assignmentId);
        return problems;
    }

    public ProblemResponse getProblem(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .createdAt(problem.getCreatedAt())
                .build();
    }

}

