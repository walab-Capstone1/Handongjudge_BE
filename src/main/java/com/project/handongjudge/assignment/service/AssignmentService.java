package com.project.handongjudge.assignment.service;

import com.project.handongjudge.assignment.dto.*;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.community.service.NotificationService;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.submission.entity.Submission;

import java.time.LocalDateTime;
import java.util.*;

import com.project.handongjudge.problem.dto.ProblemDto;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.project.handongjudge.user.repository.EnrollmentRepository;

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
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public AssignmentResponse createAssignment(Long sectionId, AssignmentRequest request, Long userId) {
        // 1. Section 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 해당 Section의 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 분반의 과제를 생성할 권한이 없습니다");
        }

        // 2. Assignment 엔티티 생성 및 저장
        Assignment assignment = Assignment.builder()
                .section(section)
                .assignmentNumber(request.getAssignmentNumber())
                .title(request.getTitle())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(true)  // 추가
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

        // 알림 발송 (모든 수강생에게)
        notificationService.notifyAssignmentCreated(savedAssignment, section);

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
                .active(assignment.getActive())  // 추가
                .build();
    }

    public List<AssignmentResponse> getAssignmentsBySection(Long sectionId, Long userId) {
        // Section 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 교수이거나 시스템 관리자이거나 해당 분반을 수강하는 학생이어야 함
        boolean isInstructor = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        List<Assignment> assignments;
        if (isInstructor) {
            // 교수는 모든 과제 조회 (active 여부와 관계없이)
            assignments = assignmentRepository.findAllAssignmentsBySectionId(sectionId);
        } else {
            // 학생은 active=true인 과제만 조회
            assignments = assignmentRepository.findActiveAssignmentsBySectionId(sectionId);
        }

        return assignments.stream()
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

    public AssignmentResponse getAssignmentInfo(Long assignmentId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // Section 조회
        Section section = assignment.getSection();

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 교수이거나 시스템 관리자이거나 해당 분반을 수강하는 학생이어야 함
        boolean isInstructor = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        // 학생이고 과제가 비활성화되어 있으면 접근 불가
        if (!isInstructor && assignment.getActive() == false) {
            throw new IllegalArgumentException("해당 과제는 비활성화되어 있어 접근할 수 없습니다");
        }

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

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 2. 권한 확인: 해당 Section의 교수이거나 시스템 관리자인지 확인
        Section section = assignment.getSection();
        boolean isAuthorized = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 과제를 수정할 권한이 없습니다");
        }

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

        // 2. 과제의 모든 문제 ID 조회
        List<Long> assignmentProblemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignmentId);

        // 3. 분반의 모든 학생 조회
        List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);

        // 4. 각 학생별로 푼 문제 및 제출 시간 조회
        List<StudentProgressResponse> progressList = new ArrayList<>();

        for (User student : students) {
            // 학생이 푼 문제 ID 목록 조회 (정답 처리된 것만)
            List<Long> solvedProblemIds = submissionRepository
                    .findSolvedProblemIdsByUserAndAssignment(student.getId(), assignmentId, sectionId);

            // 각 문제별 첫 정답 제출 시간 조회
            Map<Long, LocalDateTime> problemSubmissionTimes = new HashMap<>();
            LocalDateTime assignmentCompletedAt = null;

            for (Long problemId : solvedProblemIds) {
                Optional<LocalDateTime> firstSubmissionTime = submissionRepository
                        .findFirstAcceptedSubmissionTime(student.getId(), problemId, sectionId);

                if (firstSubmissionTime.isPresent()) {
                    problemSubmissionTimes.put(problemId, firstSubmissionTime.get());
                }
            }

            // 과제 완료 시간 계산 (모든 문제를 다 푼 경우, 마지막 문제 제출 시간)
            if (solvedProblemIds.size() == assignmentProblemIds.size() &&
                    solvedProblemIds.containsAll(assignmentProblemIds)) {
                // 모든 문제를 다 푼 경우, 가장 늦은 제출 시간을 완료 시간으로 설정
                assignmentCompletedAt = problemSubmissionTimes.values().stream()
                        .max(LocalDateTime::compareTo)
                        .orElse(null);
            }

            StudentProgressResponse progress = StudentProgressResponse.builder()
                    .userId(student.getId())
                    .studentId(student.getEmail())
                    .studentName(student.getName())
                    .solvedProblems(solvedProblemIds)
                    .problemSubmissionTimes(problemSubmissionTimes)
                    .assignmentCompletedAt(assignmentCompletedAt)
                    .build();

            progressList.add(progress);
        }

        // 이메일 순으로 정렬
        progressList.sort((a, b) -> a.getStudentId().compareTo(b.getStudentId()));

        return progressList;
    }

    public AssignmentResponse toggleAssignmentActive(Long assignmentId, Boolean active, Long instructorId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 확인: 해당 Section의 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = assignment.getSection().getInstructor().getId().equals(instructorId) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 과제를 수정할 권한이 없습니다");
        }

        assignment.setActive(active);
        Assignment updatedAssignment = assignmentRepository.save(assignment);

        return toResponse(updatedAssignment);
    }

    /**
     * 과제 삭제
     */
    @Transactional
    public void deleteAssignment(Long sectionId, Long assignmentId, Long userId) {
        // 과제 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // Section 확인
        if (!assignment.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("Section ID가 일치하지 않습니다.");
        }

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 해당 Section의 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = assignment.getSection().getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 과제를 삭제할 권한이 없습니다");
        }

        // 과제에 연결된 문제 관계 삭제
        assignmentProblemRepository.deleteByAssignmentId(assignmentId);

        // 과제 삭제
        assignmentRepository.delete(assignment);
    }

    /**
     * 마감 직전 과제 조회
     * @param sectionId 분반 ID
     * @param days 마감일까지 남은 일수 (기본값: 3일)
     * @return 마감 직전 과제 목록 (제출률 포함)
     */
    public List<UpcomingAssignmentResponse> getUpcomingAssignments(Long sectionId, Integer days) {
        // Section 존재 여부 확인
        sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // days가 null이면 기본값 3일 사용
        int daysToCheck = (days != null && days > 0) ? days : 3;

        // 현재 시간
        LocalDateTime now = LocalDateTime.now();
        // 마감일 기준 (현재 시간 + 지정된 일수)
        LocalDateTime deadline = now.plusDays(daysToCheck);

        // 마감 직전 과제 조회
        List<Assignment> upcomingAssignments = assignmentRepository
                .findUpcomingAssignmentsBySectionId(sectionId, now, deadline);

        // 각 과제별 제출 통계 조회하여 응답 생성
        List<UpcomingAssignmentResponse> responses = new ArrayList<>();

        for (Assignment assignment : upcomingAssignments) {
            try {
                // 제출 통계 조회
                AssignmentSubmissionStatsResponse stats = getAssignmentSubmissionStats(
                        assignment.getId(), sectionId);

                UpcomingAssignmentResponse response = UpcomingAssignmentResponse.builder()
                        .assignmentId(assignment.getId())
                        .title(assignment.getTitle())
                        .endDate(assignment.getEndDate())
                        .submissionRate(stats.getSubmissionRate())
                        .build();

                responses.add(response);
            } catch (Exception e) {
                // 통계 조회 실패 시 제출률을 0으로 설정
                UpcomingAssignmentResponse response = UpcomingAssignmentResponse.builder()
                        .assignmentId(assignment.getId())
                        .title(assignment.getTitle())
                        .endDate(assignment.getEndDate())
                        .submissionRate(0.0)
                        .build();

                responses.add(response);
            }
        }

        return responses;
    }

    /**
     * 튜터가 학생의 accept된 코드를 조회
     * @param sectionId 분반 ID
     * @param assignmentId 과제 ID
     * @param userId 학생 ID
     * @param problemId 문제 ID
     * @param instructorId 튜터 ID (권한 확인용)
     * @return 학생의 accept된 코드 정보
     */
    public StudentAcceptedCodeResponse getStudentAcceptedCode(
            Long sectionId, Long assignmentId, Long userId, Long problemId, Long instructorId) {
        // 1. Section 조회 및 권한 확인
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found"));

        // 권한 확인: 해당 Section의 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = section.getInstructor().getId().equals(instructorId) ||
                instructor.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 분반의 학생 코드를 조회할 권한이 없습니다");
        }

        // 2. 학생 조회
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        // 3. 문제 조회
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));

        // 4. 과제에 해당 문제가 포함되어 있는지 확인
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        List<AssignmentProblem> assignmentProblems = assignmentProblemRepository.findByAssignmentId(assignmentId);
        boolean problemInAssignment = assignmentProblems.stream()
                .anyMatch(ap -> ap.getProblem().getId().equals(problemId));

        if (!problemInAssignment) {
            throw new IllegalArgumentException("해당 문제는 이 과제에 포함되어 있지 않습니다");
        }

        // 5. 학생의 accept된 제출 조회 (첫 번째 accept된 제출)
        List<Submission> acceptedSubmissions = submissionRepository
                .findAcceptedSubmissionsByUserAndProblem(userId, problemId, sectionId);

        if (acceptedSubmissions.isEmpty()) {
            throw new IllegalArgumentException("해당 학생은 이 문제를 아직 정답으로 제출하지 않았습니다");
        }

        Submission firstAcceptedSubmission = acceptedSubmissions.get(0);

        // 6. DTO 생성 및 반환
        return StudentAcceptedCodeResponse.builder()
                .submissionId(firstAcceptedSubmission.getId())
                .userId(student.getId())
                .studentId(student.getEmail())
                .studentName(student.getName())
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .code(firstAcceptedSubmission.getCode())
                .language(firstAcceptedSubmission.getLanguage())
                .submittedAt(firstAcceptedSubmission.getSubmittedAt())
                .result(firstAcceptedSubmission.getResult())
                .build();
    }
}
