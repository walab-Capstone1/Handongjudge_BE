package com.project.handongjudge.problem.service;

import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final DomjudgeService domjudgeService;

    public Long createProblem(ProblemCreateRequest request) throws IOException {
        // DOMjudge에 문제 zip 업로드
        Long domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(
                request.getTitle(), request.getZipFile()
        );

        // 내부 DB에 저장
        Problem problem = Problem.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .domjudgeProblemId(domjudgeProblemId)
                .build();

        return problemRepository.save(problem).getId();
    }
}

