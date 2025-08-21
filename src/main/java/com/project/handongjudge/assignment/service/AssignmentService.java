package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.dto.*;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.handongjudge.submission.repository.SubmissionRepository;

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
    private final SubmissionRepository submissionRepository;

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
    // 기존 코드에 추가
    public AssignmentSubmissionStatsResponse getAssignmentSubmissionStats(Long assignmentId, Long sectionId) {
        // 1. 과제 정보 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 2. 분반 정보 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // 3. 분반 전체 학생 수
        Integer totalStudents = submissionRepository.countStudentsBySection(sectionId);

        // 4. 과제 제출한 학생 수
        // 4. 과제 제출한 학생 수
        Integer submittedStudents = submissionRepository.countAllProblemsSubmittedStudents(assignmentId, sectionId);
        if (submittedStudents == null) {
            submittedStudents = 0; // null인 경우 0으로 설정
        }
        // 5. 과제 제출률 계산
        Double submissionRate = totalStudents > 0 ?
                (double) submittedStudents / totalStudents * 100 : 0.0;

        // 6. 각 문제별 통계
        List<ProblemSubmissionStats> problemStats = new ArrayList<>();
        List<AssignmentProblem> assignmentProblems = assignmentProblemRepository.findByAssignmentId(assignmentId);

        for (AssignmentProblem ap : assignmentProblems) {
            Problem problem = ap.getProblem();

            // 문제별 제출한 학생 수
            Integer problemSubmittedStudents = submissionRepository.countSubmittedStudentsByProblem(problem.getId(), sectionId);

            // 문제별 정답 제출 수
            Integer correctSubmissions = submissionRepository.countCorrectSubmissionsByProblem(problem.getId(), sectionId);

            // 문제별 제출률
            Double problemSubmissionRate = totalStudents > 0 ?
                    (double) problemSubmittedStudents / totalStudents * 100 : 0.0;

            // 문제별 정답률
            Double correctRate = problemSubmittedStudents > 0 ?
                    (double) correctSubmissions / problemSubmittedStudents * 100 : 0.0;

            ProblemSubmissionStats problemStat = ProblemSubmissionStats.builder()
                    .problemId(problem.getId())
                    .problemTitle(problem.getTitle())
                    .problemOrder(ap.getProblemOrder())
                    .totalStudents(totalStudents)
                    .submittedStudents(problemSubmittedStudents)
                    .correctSubmissions(correctSubmissions)
                    .submissionRate(problemSubmissionRate)
                    .correctRate(correctRate)
                    .build();

            problemStats.add(problemStat);
        }

        return AssignmentSubmissionStatsResponse.builder()
                .assignmentId(assignmentId)
                .assignmentTitle(assignment.getTitle())
                .sectionId(sectionId)
                .sectionName(section.getCourse().getTitle() + " - " + section.getSectionNumber() + "분반")
                .totalStudents(totalStudents)
                .submittedStudents(submittedStudents)
                .submissionRate(submissionRate)
                .problemStats(problemStats)
                .build();
    }

    // 전체 과제 통계 (교수가 담당하는 모든 분반)
    public List<AssignmentSubmissionStatsResponse> getAllAssignmentsSubmissionStats(Long instructorId) {
        // 1. 교수가 담당하는 모든 분반 조회
        List<Section> sections = sectionRepository.findByInstructorId(instructorId);

        List<AssignmentSubmissionStatsResponse> allStats = new ArrayList<>();

        for (Section section : sections) {
            // 2. 각 분반의 과제들 조회
            List<Assignment> assignments = assignmentRepository.findBySectionId(section.getId());

            for (Assignment assignment : assignments) {
                AssignmentSubmissionStatsResponse stats = getAssignmentSubmissionStats(assignment.getId(), section.getId());
                allStats.add(stats);
            }
        }

        return allStats;
    }
}
