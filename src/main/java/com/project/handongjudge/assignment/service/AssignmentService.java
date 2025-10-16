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
import com.project.handongjudge.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;
import java.util.ArrayList;

import com.project.handongjudge.problem.dto.ProblemDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;

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
    private final EnrollmentRepository enrollmentRepository;  // 이 줄 추가

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
    public AssignmentResponse updateAssignment(Long sectionId, Long assignmentId, AssignmentRequest request, Long userId) {
        // 1. Assignment 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 2. 권한 확인 (선택사항 - 교수만 수정 가능하도록)
        // Section section = assignment.getSection();
        // if (!section.getInstructor().getId().equals(userId)) {
        //     throw new IllegalArgumentException("권한이 없습니다");
        // }

        // 3. Assignment 정보 업데이트
        assignment.updateAssignment(
                request.getAssignmentNumber(),
                request.getTitle(),
                request.getDescription(),
                request.getStartDate(),
                request.getEndDate()
        );

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // 4. 문제 연결 업데이트 (기존 문제 연결 삭제 후 새로 추가)
        if (request.getProblemIds() != null) {
            // 기존 AssignmentProblem 삭제
            assignmentProblemRepository.deleteByAssignmentId(assignmentId);

            // 새로운 문제 연결
            if (!request.getProblemIds().isEmpty()) {
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
        }

        return toResponse(savedAssignment);
    }
    public UserSubmissionStatusResponse getUserSubmissionStatus(Long sectionId, Long assignmentId, Long userId) {
        // 과제 정보 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 과제의 문제 목록 조회
        List<AssignmentProblem> assignmentProblems = assignmentProblemRepository.findByAssignmentId(assignmentId);

        List<ProblemSubmissionStatus> problemStatuses = new ArrayList<>();

        for (AssignmentProblem ap : assignmentProblems) {
            Problem problem = ap.getProblem();

            // 사용자가 이 문제를 제출했는지 확인
            boolean hasSubmitted = submissionRepository.existsByUserIdAndProblemIdAndSectionId(userId, problem.getId(), sectionId);

            // 사용자가 이 문제를 정답으로 제출했는지 확인
            boolean hasCorrectSubmission = submissionRepository.existsCorrectSubmissionByUserIdAndProblemIdAndSectionId(userId, problem.getId(), sectionId);

            ProblemSubmissionStatus status = ProblemSubmissionStatus.builder()
                    .problemId(problem.getId())
                    .problemTitle(problem.getTitle())
                    .problemOrder(ap.getProblemOrder())
                    .hasSubmitted(hasSubmitted)
                    .hasCorrectSubmission(hasCorrectSubmission)
                    .build();

            problemStatuses.add(status);
        }

        return UserSubmissionStatusResponse.builder()
                .assignmentId(assignmentId)
                .assignmentTitle(assignment.getTitle())
                .sectionId(sectionId)
                .userId(userId)
                .problemStatuses(problemStatuses)
                .build();
    }
    public List<StudentProgressResponse> getAssignmentStudentProgress(Long assignmentId, Long sectionId) {
        // 1. 과제 정보 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 2. 분반의 모든 학생 조회
        List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);

        // 3. 각 학생별로 푼 문제 조회
        List<StudentProgressResponse> progressList = new ArrayList<>();

        for (User student : students) {
            // 학생이 푼 문제 ID 목록 조회 (정답 처리된 것만)
            List<Long> solvedProblemIds = submissionRepository
                    .findSolvedProblemIdsByUserAndAssignment(student.getId(), assignmentId, sectionId);

            StudentProgressResponse progress = StudentProgressResponse.builder()
                    .userId(student.getId())
                    .studentId(student.getEmail())
                    .studentName(student.getName())
                    .solvedProblems(solvedProblemIds)
                    .build();

            progressList.add(progress);
        }

        // 이메일 순으로 정렬
        progressList.sort((a, b) -> a.getStudentId().compareTo(b.getStudentId()));

        return progressList;
    }
}
