package com.project.handongjudge.grade.service;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.grade.dto.*;
import com.project.handongjudge.grade.entity.Grade;
import com.project.handongjudge.grade.repository.GradeRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.common.time.SubmissionDeadlineComparison;
import com.project.handongjudge.submission.service.SubmissionService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.domain.PageRequest;

@Transactional
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private final GradeRepository gradeRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SectionRoleService sectionRoleService;
    private final SubmissionService submissionService;

    @Override
    public GradeResponseDTO saveGrade(GradeRequestDTO request, Long tutorId) {
        // 1. 권한 확인
        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        Section section = assignment.getSection();
        if (!sectionRoleService.isManager(tutorId, section.getId())) {
            throw new IllegalArgumentException("해당 과제의 성적을 입력할 권한이 없습니다");
        }

        // 2. 배점 확인
        AssignmentProblem assignmentProblem = assignmentProblemRepository
                .findByAssignmentIdAndProblemId(request.getAssignmentId(), request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("과제 문제를 찾을 수 없습니다"));

        // 3. 점수 유효성 검증
        if (request.getScore() != null && request.getScore() > assignmentProblem.getPoints()) {
            throw new IllegalArgumentException(
                    "점수는 배점(" + assignmentProblem.getPoints() + ")을 초과할 수 없습니다"
            );
        }

        // 4. 기존 성적 조회 또는 생성
        Grade grade = gradeRepository
                .findByAssignmentIdAndProblemIdAndStudentId(
                        request.getAssignmentId(),
                        request.getProblemId(),
                        request.getUserId()
                )
                .orElse(new Grade());

        // 5. 성적 정보 설정
        if (grade.getId() == null) {
            grade.setAssignment(assignment);
            grade.setProblem(assignmentProblem.getProblem());
            grade.setStudent(userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다")));
            grade.setPoints(assignmentProblem.getPoints());
        }

        grade.setScore(request.getScore());
        grade.setComment(request.getComment());
        grade.setGradedBy(userRepository.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("튜터를 찾을 수 없습니다")));
        grade.setGradedAt(LocalDateTime.now());

        // 6. 저장
        Grade savedGrade = gradeRepository.save(grade);

        // 7. 제출 정보 조회
        Optional<Submission> latestSubmission = findLatestSubmission(
                request.getUserId(), request.getProblemId(), section.getId());

        // 8. DTO 변환 및 반환
        return convertToResponseDTO(savedGrade, latestSubmission.orElse(null), assignment);
    }

    @Override
    public List<GradeResponseDTO> saveBulkGrades(BulkGradeRequestDTO request, Long tutorId) {
        // 권한 확인
        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        
        Section section = assignment.getSection();
        if (!sectionRoleService.isManager(tutorId, section.getId())) {
            throw new IllegalArgumentException("해당 과제의 성적을 입력할 권한이 없습니다");
        }

        List<GradeResponseDTO> results = new ArrayList<>();
        for (GradeRequestDTO gradeRequest : request.getGrades()) {
            gradeRequest.setAssignmentId(request.getAssignmentId());
            try {
                GradeResponseDTO result = saveGrade(gradeRequest, tutorId);
                results.add(result);
            } catch (Exception e) {
                // 개별 실패는 로그만 남기고 계속 진행
                System.err.println("성적 저장 실패: " + e.getMessage());
            }
        }
        return results;
    }

    @Override
    public List<StudentGradeSummaryDTO> getAssignmentGrades(Long assignmentId, Long sectionId) {
        // 1. 과제 정보 조회
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // 2. 과제의 모든 문제와 배점 조회
        List<AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByAssignmentIdOrderByProblemOrderAsc(assignmentId);

        // 3. 분반의 모든 학생 조회
        List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);

        // 4. 각 학생별로 성적 조회
        List<StudentGradeSummaryDTO> gradeSummaries = new ArrayList<>();

        for (User student : students) {
            StudentGradeSummaryDTO summary = new StudentGradeSummaryDTO();
            summary.setUserId(student.getId());
            summary.setStudentName(student.getName());
            summary.setStudentId(student.getStudentId() != null ? student.getStudentId() : student.getEmail());

            // 문제별 성적 조회
            List<StudentGradeSummaryDTO.ProblemGradeDTO> problemGrades = new ArrayList<>();
            int totalScore = 0;
            int totalPoints = 0;

            for (AssignmentProblem ap : assignmentProblems) {
                Problem problem = ap.getProblem();
                StudentGradeSummaryDTO.ProblemGradeDTO pg = new StudentGradeSummaryDTO.ProblemGradeDTO();
                pg.setProblemId(problem.getId());
                pg.setProblemTitle(problem.getTitle());
                pg.setPoints(1); // 과제/퀴즈: 통과 시 1점
                totalPoints += 1;

                Optional<Submission> submission = findLatestSubmission(
                        student.getId(), problem.getId(), sectionId);

                if (submission.isPresent()) {
                    Submission sub = submission.get();
                    submissionService.backfillTestCaseCountsIfMissing(sub);
                    pg.setSubmitted(true);
                    pg.setSubmittedAt(sub.getSubmittedAt());
                    if (assignment.getEndDate() != null) {
                        pg.setIsOnTime(SubmissionDeadlineComparison.isSubmittedOnTime(sub.getSubmittedAt(), assignment.getEndDate()));
                    } else {
                        pg.setIsOnTime(true);
                    }
                    pg.setResult(sub.getResult());
                    pg.setPassedTestCases(sub.getPassedTestCases());
                    pg.setTotalTestCases(sub.getTotalTestCases());
                    if ("AC".equals(sub.getResult())) {
                        pg.setScore(1);
                        totalScore += 1;
                    } else {
                        pg.setScore(0);
                    }
                } else {
                    pg.setSubmitted(false);
                    pg.setIsOnTime(false);
                    pg.setScore(0);
                }

                problemGrades.add(pg);
            }

            summary.setProblemGrades(problemGrades);
            summary.setTotalScore(totalScore);
            summary.setTotalPoints(totalPoints);
            summary.setGradeRatio(totalScore + "/" + totalPoints);

            gradeSummaries.add(summary);
        }

        return gradeSummaries;
    }

    @Override
    public StudentGradeSummaryDTO getStudentGrade(Long assignmentId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        List<AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByAssignmentIdOrderByProblemOrderAsc(assignmentId);

        StudentGradeSummaryDTO summary = new StudentGradeSummaryDTO();
        summary.setUserId(student.getId());
        summary.setStudentName(student.getName());
        summary.setStudentId(student.getStudentId() != null ? student.getStudentId() : student.getEmail());

        List<StudentGradeSummaryDTO.ProblemGradeDTO> problemGrades = new ArrayList<>();
        int totalScore = 0;
        int totalPoints = 0;

        for (AssignmentProblem ap : assignmentProblems) {
            Problem problem = ap.getProblem();
            StudentGradeSummaryDTO.ProblemGradeDTO pg = new StudentGradeSummaryDTO.ProblemGradeDTO();
            pg.setProblemId(problem.getId());
            pg.setProblemTitle(problem.getTitle());
            pg.setPoints(1);
            totalPoints += 1;

            Optional<Submission> submission = findLatestSubmission(
                    userId, problem.getId(), assignment.getSection().getId());

            if (submission.isPresent()) {
                Submission sub = submission.get();
                submissionService.backfillTestCaseCountsIfMissing(sub);
                pg.setSubmitted(true);
                pg.setSubmittedAt(sub.getSubmittedAt());
                if (assignment.getEndDate() != null) {
                        pg.setIsOnTime(SubmissionDeadlineComparison.isSubmittedOnTime(sub.getSubmittedAt(), assignment.getEndDate()));
                } else {
                    pg.setIsOnTime(true);
                }
                pg.setResult(sub.getResult());
                pg.setPassedTestCases(sub.getPassedTestCases());
                pg.setTotalTestCases(sub.getTotalTestCases());
                if ("AC".equals(sub.getResult())) {
                    pg.setScore(1);
                    totalScore += 1;
                } else {
                    pg.setScore(0);
                }
            } else {
                pg.setSubmitted(false);
                pg.setIsOnTime(false);
                pg.setScore(0);
            }

            problemGrades.add(pg);
        }

        summary.setProblemGrades(problemGrades);
        summary.setTotalScore(totalScore);
        summary.setTotalPoints(totalPoints);
        summary.setGradeRatio(totalScore + "/" + totalPoints);

        return summary;
    }

    @Override
    public GradeResponseDTO getProblemGrade(Long assignmentId, Long problemId, Long userId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        Optional<Grade> grade = gradeRepository
                .findByAssignmentIdAndProblemIdAndStudentId(assignmentId, problemId, userId);

        if (grade.isEmpty()) {
            throw new IllegalArgumentException("성적을 찾을 수 없습니다");
        }

        Optional<Submission> submission = findLatestSubmission(
                userId, problemId, assignment.getSection().getId());

        return convertToResponseDTO(grade.get(), submission.orElse(null), assignment);
    }

    @Override
    public void deleteGrade(Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found"));
        
        gradeRepository.delete(grade);
    }

    @Override
    public void setProblemPoints(Long assignmentId, Long problemId, Integer points) {
        if (points < 0) {
            throw new IllegalArgumentException("배점은 0 이상이어야 합니다");
        }

        AssignmentProblem assignmentProblem = assignmentProblemRepository
                .findByAssignmentIdAndProblemId(assignmentId, problemId)
                .orElseThrow(() -> new IllegalArgumentException("과제 문제를 찾을 수 없습니다"));

        assignmentProblem.setPoints(points);
        assignmentProblemRepository.save(assignmentProblem);
    }

    @Override
    public void setBulkProblemPoints(Long assignmentId, Map<Long, Integer> problemPoints) {
        for (Map.Entry<Long, Integer> entry : problemPoints.entrySet()) {
            setProblemPoints(assignmentId, entry.getKey(), entry.getValue());
        }
    }

    // Helper 메서드
    private GradeResponseDTO convertToResponseDTO(Grade grade, Submission submission, Assignment assignment) {
        GradeResponseDTO.GradeResponseDTOBuilder builder = GradeResponseDTO.builder()
                .id(grade.getId())
                .assignmentId(grade.getAssignment().getId())
                .assignmentTitle(grade.getAssignment().getTitle())
                .problemId(grade.getProblem().getId())
                .problemTitle(grade.getProblem().getTitle())
                .userId(grade.getStudent().getId())
                .studentName(grade.getStudent().getName())
                .studentId(grade.getStudent().getStudentId() != null ? 
                        grade.getStudent().getStudentId() : grade.getStudent().getEmail())
                .points(grade.getPoints())
                .score(grade.getScore())
                .comment(grade.getComment())
                .gradedAt(grade.getGradedAt());

        if (grade.getGradedBy() != null) {
            builder.gradedByName(grade.getGradedBy().getName());
        }

        if (submission != null) {
            builder.submitted(true)
                    .submittedAt(submission.getSubmittedAt())
                    .result(submission.getResult());

            if (assignment.getEndDate() != null) {
                builder.isOnTime(SubmissionDeadlineComparison.isSubmittedOnTime(submission.getSubmittedAt(), assignment.getEndDate()));
            } else {
                builder.isOnTime(true);
            }
        } else {
            builder.submitted(false)
                    .isOnTime(false);
        }

        return builder.build();
    }

    private Optional<Submission> findLatestSubmission(Long userId, Long problemId, Long sectionId) {
        List<Submission> list = submissionRepository.findLatestSubmissionsByUserAndProblem(
                userId, problemId, sectionId, PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}

