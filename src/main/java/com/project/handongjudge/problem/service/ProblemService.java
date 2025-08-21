package com.project.handongjudge.problem.service;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.service.AssignmentProblemService;
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
    private final AssignmentProblemService assignmentProblemService;
    private final AssignmentRepository assignmentRepository;

    public Long createProblem(ProblemCreateRequest request) throws IOException {
        String title = request.getTitle();
        MultipartFile descriptionFile = request.getDescriptionFile();
        MultipartFile zipFile = request.getZipFile();

        // Read description from MD file
        String description = "";
        if (descriptionFile != null && !descriptionFile.isEmpty()) {
            description = new String(descriptionFile.getBytes(), "UTF-8");
        }

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

    public void addProblem(Long problemId, Long assignmentId) {
        assignmentProblemService.addProblemToAssignment(assignmentId, problemId);
    }
    // ProblemService.java에 추가
    public List<ProblemResponse> getAllProblems() {
        List<Problem> problems = problemRepository.findAll();
        return problems.stream()
                .map(this::convertToProblemResponse)
                .collect(Collectors.toList());
    }

    private ProblemResponse convertToProblemResponse(Problem problem) {
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .createdAt(problem.getCreatedAt())
                .build();
    }
}

