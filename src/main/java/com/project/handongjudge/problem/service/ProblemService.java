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
import com.project.handongjudge.problem.util.ProblemFileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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

        // Description 우선순위 처리
        String description = "";

        // 1. 별도 업로드한 description 파일이 최우선
        if (descriptionFile != null && !descriptionFile.isEmpty()) {
            description = new String(descriptionFile.getBytes(), "UTF-8");
            log.info("별도 업로드 description 파일 사용: {} ({} bytes)",
                    descriptionFile.getOriginalFilename(), description.length());
        }
        // 2. ZIP 파일 내부에서 description 추출 시도
        else if (zipFile != null && !zipFile.isEmpty()) {
            try {
                description = ProblemFileUtil.extractDescriptionFromZip(zipFile);
                if (!description.isEmpty()) {
                    log.info("ZIP 파일에서 description 추출 성공 ({} bytes)", description.length());
                } else {
                    log.info("ZIP 파일에 description 파일이 없습니다.");
                }
            } catch (IOException e) {
                log.error("ZIP 파일에서 description 추출 실패: {}", e.getMessage());
                // ZIP에서 추출 실패해도 문제 생성은 계속 진행
                description = "";
            }
        } else {
            log.info("description 파일과 ZIP 파일이 모두 제공되지 않았습니다.");
        }

        // *** 새로 추가: ZIP 파일에서 제한 설정 추출 ***
        Double timeLimit = null;
        Integer memoryLimit = null;
        String specialRun = null;
        String specialCompare = null;

        if (zipFile != null && !zipFile.isEmpty()) {
            try {
                Map<String, Object> limits = ProblemFileUtil.extractProblemLimits(zipFile);

                timeLimit = (Double) limits.get("timeLimit");
                memoryLimit = (Integer) limits.get("memoryLimit");
                specialRun = (String) limits.get("specialRun");
                specialCompare = (String) limits.get("specialCompare");

                log.info("ZIP에서 추출된 제한 설정 - Time: {}초, Memory: {}MB, SpecialRun: {}, SpecialCompare: {}",
                        timeLimit, memoryLimit, specialRun, specialCompare);

            } catch (IOException e) {
                log.error("ZIP 파일에서 제한 설정 추출 실패: {}", e.getMessage());
                // 제한 설정 추출 실패해도 문제 생성은 계속 진행
            }
        }

        // DOMjudge에 문제 업로드
        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFile);

        Problem problem = Problem.builder()
                .title(title)
                .description(description)
                .domjudgeProblemId(domjudgeProblemId)
                .timeLimit(timeLimit)           // 새로 추가
                .memoryLimit(memoryLimit)       // 새로 추가
                .createdAt(LocalDateTime.now())
                .build();

        problemRepository.save(problem);
        log.info("문제 생성 완료: ID={}, Title={}, Description Length={}, Time Limit={}초, Memory Limit={}MB",
                problem.getId(), title, description.length(), timeLimit, memoryLimit);

        return problem.getId();
    }

    // convertToProblemResponse 메소드도 수정
    private ProblemResponse convertToProblemResponse(Problem problem) {
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .memoryLimit(problem.getMemoryLimit())     // 새로 추가
                .timeLimit(problem.getTimeLimit())         // 새로 추가

                .createdAt(problem.getCreatedAt())
                .build();
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
                .difficulty(problem.getDifficulty())
                .timeLimit(problem.getTimeLimit())         // 추가
                .memoryLimit(problem.getMemoryLimit())     // 추가
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


}

