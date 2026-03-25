package com.project.handongjudge.quiz.service;

import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.entity.QuizGrade;
import com.project.handongjudge.quiz.entity.QuizProblem;
import com.project.handongjudge.quiz.repository.QuizRepository;
import com.project.handongjudge.quiz.repository.QuizProblemRepository;
import com.project.handongjudge.quiz.repository.QuizGradeRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.assignment.dto.ProblemSubmissionStats;
import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;
import com.project.handongjudge.grade.dto.StudentGradeSummaryDTO;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Output;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.submission.service.SubmissionService;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.mypage.dto.SubmissionCodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizProblemRepository quizProblemRepository;
    private final QuizGradeRepository quizGradeRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SectionRoleService sectionRoleService;
    private final DomjudgeService domjudgeService;
    private final SubmissionService submissionService;

    /**
     * Quiz 생성
     */
    public QuizResponse createQuiz(Long sectionId, QuizRequest request, Long userId) {
        // Section 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        // 사용자 조회 및 권한 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        boolean isAuthorized = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 분반의 코딩 테스트를 생성할 권한이 없습니다");
        }

        // 상태 자동 설정
        Quiz.QuizStatus status = determineStatus(request.getStartTime(), request.getEndTime());

        // Quiz 엔티티 생성 및 저장
        Quiz quiz = Quiz.builder()
                .section(section)
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(status)
                // 생성 직후는 비공개. 메인 탭에서 공개 토글 후 학생에게 노출
                .active(false)
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);

        // Problem 연결 및 DOMjudge 등록
        if (request.getProblemIds() != null && !request.getProblemIds().isEmpty()) {
            List<QuizProblem> quizProblems = new ArrayList<>();
            int order = 1;

            for (Long problemId : request.getProblemIds()) {
                Problem problem = problemRepository.findById(problemId)
                        .orElseThrow(() -> new IllegalArgumentException("문제 ID 없음: " + problemId));

                QuizProblem qp = QuizProblem.builder()
                        .quiz(savedQuiz)
                        .problem(problem)
                        .problemOrder(order++)
                        .points(1)
                        .build();
                quizProblems.add(qp);

                // DOMjudge 등록
                Long contestId = section.getId(); // sectionId == contestId
                String domjudgeProblemId = problem.getDomjudgeProblemId();
                domjudgeService.addProblemToContest(contestId, domjudgeProblemId);
            }

            quizProblemRepository.saveAll(quizProblems);
        }

        return toResponse(savedQuiz);
    }

    /**
     * 섹션별 Quiz 목록 조회
     */
    @Transactional(readOnly = true)
    public List<QuizListDto> getQuizzesBySection(Long sectionId, Long userId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인
        boolean isInstructor = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        List<Quiz> quizzes;
        if (isInstructor) {
            quizzes = quizRepository.findBySectionIdOrderByStartTimeDesc(sectionId);
        } else {
            quizzes = quizRepository.findActiveQuizzesBySectionId(sectionId);
        }

        // 상태 업데이트 및 DTO 변환
        return quizzes.stream()
                .map(quiz -> {
                    updateQuizStatus(quiz);
                    return toListDto(quiz);
                })
                .collect(Collectors.toList());
    }

    /**
     * Quiz 상세 정보 조회
     */
    @Transactional(readOnly = true)
    public QuizResponse getQuizInfo(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        // Section 조회
        Section section = quiz.getSection();

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인
        boolean isInstructor = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        // 학생이고 Quiz가 비활성화되어 있으면 접근 불가
        if (!isInstructor && !quiz.getActive()) {
            throw new IllegalArgumentException("해당 코딩 테스트는 비활성화되어 있어 접근할 수 없습니다");
        }

        // 상태 업데이트
        updateQuizStatus(quiz);

        return toResponse(quiz);
    }

    /**
     * Quiz의 문제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<QuizProblemDto> getQuizProblems(Long quizId) {
        // Quiz 존재 여부 확인
        if (!quizRepository.existsById(quizId)) {
            throw new IllegalArgumentException("Quiz not found");
        }

        List<QuizProblem> quizProblems = quizProblemRepository.findByQuizIdOrderByProblemOrderAsc(quizId);

        return quizProblems.stream()
                .map(qp -> QuizProblemDto.builder()
                        .id(qp.getId())
                        .problemId(qp.getProblem().getId())
                        .title(qp.getProblem().getTitle())
                        .problemOrder(qp.getProblemOrder())
                        .points(qp.getPoints() != null && qp.getPoints() > 0 ? qp.getPoints() : 1)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Quiz 수정
     */
    public QuizResponse updateQuiz(Long sectionId, Long quizId, QuizRequest request, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인
        Section section = quiz.getSection();
        boolean isAuthorized = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 코딩 테스트를 수정할 권한이 없습니다");
        }

        // Quiz 정보 업데이트
        quiz.setTitle(request.getTitle());
        quiz.setDescription(request.getDescription());
        quiz.setStartTime(request.getStartTime());
        quiz.setEndTime(request.getEndTime());

        // 상태 업데이트 (수정 시에는 시간 기반 자동 결정)
        Quiz.QuizStatus newStatus = determineStatus(request.getStartTime(), request.getEndTime());
        quiz.setStatus(newStatus);
        quiz.setManualStatusOverride(false);

        Quiz savedQuiz = quizRepository.save(quiz);

        // 문제 연결 업데이트 및 DOMjudge 등록
        if (request.getProblemIds() != null) {
            // 기존 QuizProblem 삭제
            quizProblemRepository.deleteByQuizId(quizId);

            // 새로운 문제 연결
            if (!request.getProblemIds().isEmpty()) {
                List<QuizProblem> quizProblems = new ArrayList<>();
                int order = 1;

                for (Long problemId : request.getProblemIds()) {
                    Problem problem = problemRepository.findById(problemId)
                            .orElseThrow(() -> new IllegalArgumentException("문제 ID 없음: " + problemId));

                    QuizProblem qp = QuizProblem.builder()
                            .quiz(savedQuiz)
                            .problem(problem)
                            .problemOrder(order++)
                            .build();
                    quizProblems.add(qp);

                    // DOMjudge 등록
                    Long contestId = section.getId(); // sectionId == contestId
                    String domjudgeProblemId = problem.getDomjudgeProblemId();
                    domjudgeService.addProblemToContest(contestId, domjudgeProblemId);
                }

                quizProblemRepository.saveAll(quizProblems);
            }
        }

        return toResponse(savedQuiz);
    }

    /**
     * Quiz 삭제
     */
    public void deleteQuiz(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인
        Section section = quiz.getSection();
        boolean isAuthorized = section.getInstructor().getId().equals(userId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 코딩 테스트를 삭제할 권한이 없습니다");
        }

        quizRepository.delete(quiz);
    }

    /**
     * 퀴즈 학생 진행 현황 일괄 조회
     */
    @Transactional(readOnly = true)
    public List<StudentProgressResponse> getQuizStudentProgress(Long quizId, Long sectionId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(userId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 진행 현황을 조회할 권한이 없습니다");
        }

        if (!quiz.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("해당 퀴즈는 이 분반에 속하지 않습니다");
        }

        List<Long> quizProblemIds = quizProblemRepository.findProblemIdsByQuizId(quizId);
        List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);
        List<StudentProgressResponse> progressList = new ArrayList<>();

        for (User student : students) {
            List<Long> solvedProblemIds = submissionRepository.findSolvedProblemIdsByUserAndQuiz(
                    student.getId(), quizId, sectionId);

            Map<Long, LocalDateTime> problemSubmissionTimes = new HashMap<>();
            LocalDateTime quizCompletedAt = null;

            for (Long problemId : solvedProblemIds) {
                List<Submission> latestList = submissionRepository.findLatestSubmissionsByUserAndProblem(
                        student.getId(), problemId, sectionId, PageRequest.of(0, 1));
                if (!latestList.isEmpty()) {
                    problemSubmissionTimes.put(problemId, latestList.get(0).getSubmittedAt());
                }
            }

            if (solvedProblemIds.size() == quizProblemIds.size() && solvedProblemIds.containsAll(quizProblemIds)) {
                quizCompletedAt = problemSubmissionTimes.values().stream()
                        .max(LocalDateTime::compareTo)
                        .orElse(null);
            }

            String sid = student.getStudentId();
            if (sid == null || sid.isBlank()) {
                sid = student.getEmail() != null ? student.getEmail() : "-";
            }

            progressList.add(StudentProgressResponse.builder()
                    .userId(student.getId())
                    .studentId(sid)
                    .studentName(student.getName())
                    .solvedProblems(solvedProblemIds)
                    .problemSubmissionTimes(problemSubmissionTimes)
                    .assignmentCompletedAt(quizCompletedAt)
                    .build());
        }

        progressList.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getStudentId(), b.getStudentId()));
        return progressList;
    }

    /**
     * 퀴즈 문제별 제출 통계 조회
     */
    @Transactional(readOnly = true)
    public QuizSubmissionStatsResponse getQuizSubmissionStats(Long quizId, Long sectionId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(userId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 제출 통계를 조회할 권한이 없습니다");
        }

        if (!quiz.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("해당 퀴즈는 이 분반에 속하지 않습니다");
        }

        Section section = quiz.getSection();
        Integer totalStudents = submissionRepository.countStudentsBySection(sectionId);
        List<QuizProblem> quizProblems = quizProblemRepository.findByQuizIdOrderByProblemOrderAsc(quizId);
        List<ProblemSubmissionStats> problemStats = new ArrayList<>();

        for (QuizProblem qp : quizProblems) {
            Problem problem = qp.getProblem();
            Integer submittedStudents = submissionRepository.countSubmittedStudentsByProblem(problem.getId(), sectionId);
            Integer correctSubmissions = submissionRepository.countCorrectSubmissionsByProblem(problem.getId(), sectionId);

            Double submissionRate = totalStudents > 0 ? (double) submittedStudents / totalStudents * 100 : 0.0;
            Double correctRate = submittedStudents > 0 ? (double) correctSubmissions / submittedStudents * 100 : 0.0;

            problemStats.add(ProblemSubmissionStats.builder()
                    .problemId(problem.getId())
                    .problemTitle(problem.getTitle())
                    .problemOrder(qp.getProblemOrder())
                    .totalStudents(totalStudents)
                    .submittedStudents(submittedStudents)
                    .correctSubmissions(correctSubmissions)
                    .submissionRate(submissionRate)
                    .correctRate(correctRate)
                    .build());
        }

        String sectionName = section.getCourse() != null
                ? section.getCourse().getTitle() + " - " + (section.getSectionNumber() != null ? section.getSectionNumber() + "분반" : "")
                : "";

        return QuizSubmissionStatsResponse.builder()
                .quizId(quizId)
                .quizTitle(quiz.getTitle())
                .sectionId(sectionId)
                .sectionName(sectionName)
                .totalStudents(totalStudents)
                .problemStats(problemStats)
                .build();
    }

    /**
     * 퀴즈에서 문제 제거
     */
    public void removeProblemFromQuiz(Long quizId, Long problemId, Long instructorId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(instructorId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 코딩 테스트를 수정할 권한이 없습니다");
        }

        quizProblemRepository.findByQuizIdAndProblemId(quizId, problemId)
                .orElseThrow(() -> new IllegalArgumentException("해당 문제는 이 퀴즈에 포함되어 있지 않습니다"));

        quizProblemRepository.deleteByQuizIdAndProblemId(quizId, problemId);

        // 남은 문제들 problemOrder 재정렬
        List<QuizProblem> remaining = quizProblemRepository.findByQuizIdOrderByProblemOrderAsc(quizId);
        int order = 1;
        for (QuizProblem remainingQp : remaining) {
            if (remainingQp.getProblemOrder() != order) {
                remainingQp.setProblemOrder(order);
                quizProblemRepository.save(remainingQp);
            }
            order++;
        }
    }

    /**
     * 퀴즈 비활성화/활성화 토글
     */
    public QuizResponse toggleQuizActive(Long quizId, Boolean active, Long instructorId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        // 권한 확인: 해당 Section의 관리자인지 확인
        if (!sectionRoleService.isManager(instructorId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 코딩 테스트를 수정할 권한이 없습니다");
        }

        quiz.setActive(active);
        Quiz updatedQuiz = quizRepository.save(quiz);

        return toResponse(updatedQuiz);
    }

    /**
     * 퀴즈 성적 조회 (제출 정보 기반)
     */
    @Transactional(readOnly = true)
    public List<StudentGradeSummaryDTO> getQuizGrades(
            Long quizId, Long sectionId, Long userId, boolean includeTestCaseResults) {
        // 1. 퀴즈 정보 조회
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        // 2. 권한 확인
        Section section = quiz.getSection();
        if (!sectionRoleService.isManager(userId, section.getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 성적을 조회할 권한이 없습니다");
        }

        // 3. 퀴즈의 모든 문제 조회
        List<QuizProblem> quizProblems = quizProblemRepository.findByQuizIdOrderByProblemOrderAsc(quizId);

        // 4. 분반의 모든 학생 조회
        List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);

        // 5. 각 학생별로 성적 조회
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

            for (QuizProblem qp : quizProblems) {
                Problem problem = qp.getProblem();
                StudentGradeSummaryDTO.ProblemGradeDTO pg = new StudentGradeSummaryDTO.ProblemGradeDTO();
                pg.setProblemId(problem.getId());
                pg.setProblemTitle(problem.getTitle());
                pg.setPoints(1); // 퀴즈: 통과 시 1점
                totalPoints += 1;

                List<Submission> latestList = submissionRepository.findLatestSubmissionsByUserAndProblem(
                        student.getId(), problem.getId(), sectionId, PageRequest.of(0, 1));
                Optional<Submission> submission = latestList.isEmpty()
                        ? Optional.empty()
                        : Optional.of(latestList.get(0));

                if (submission.isPresent()) {
                    Submission sub = submission.get();
                    pg.setSubmitted(true);
                    pg.setSubmittedAt(sub.getSubmittedAt());
                    pg.setSubmissionDomjudgeId(sub.getSubmissionId());
                    pg.setResult(sub.getResult());
                    if (quiz.getEndTime() != null) {
                        pg.setIsOnTime(
                                sub.getSubmittedAt().isBefore(quiz.getEndTime()) ||
                                sub.getSubmittedAt().isEqual(quiz.getEndTime())
                        );
                    } else {
                        pg.setIsOnTime(true);
                    }
                    if ("AC".equals(sub.getResult())) {
                        pg.setScore(1);
                        totalScore += 1;
                    } else {
                        pg.setScore(0);
                    }
                    enrichProblemGradeWithTestCases(pg, sectionId, sub, includeTestCaseResults);
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

    private void enrichProblemGradeWithTestCases(
            StudentGradeSummaryDTO.ProblemGradeDTO pg,
            Long sectionId,
            Submission sub,
            boolean includeTestCaseResults) {
        if (!includeTestCaseResults || sub == null) {
            return;
        }
        String sid = sub.getSubmissionId();
        if (sid == null || sid.isBlank()) {
            return;
        }
        SubmissionOutputResponseDTO out = submissionService.getResultOutput(sectionId, sid);
        if (out == null || out.getOutputList() == null || out.getOutputList().isEmpty()) {
            pg.setTestCaseResults(Collections.emptyList());
            pg.setTotalTestCaseCount(0);
            pg.setPassedTestCaseCount(0);
            pg.setAllTestCasesPassed(false);
            return;
        }
        List<Output> list = out.getOutputList();
        int passed = (int) list.stream()
                .filter(o -> o.getResult() != null && "correct".equalsIgnoreCase(o.getResult().trim()))
                .count();
        pg.setTestCaseResults(list);
        pg.setTotalTestCaseCount(list.size());
        pg.setPassedTestCaseCount(passed);
        pg.setAllTestCasesPassed(passed == list.size());
    }

    /**
     * 퀴즈 단일 성적 저장/수정
     */
    public QuizGradeResponseDTO saveQuizGrade(QuizGradeRequestDTO request, Long tutorId) {
        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        Section section = quiz.getSection();
        if (!sectionRoleService.isManager(tutorId, section.getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 성적을 입력할 권한이 없습니다");
        }

        QuizProblem quizProblem = quizProblemRepository
                .findByQuizIdAndProblemId(request.getQuizId(), request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("퀴즈 문제를 찾을 수 없습니다"));
        int maxPoints = (quizProblem.getPoints() != null && quizProblem.getPoints() > 0) ? quizProblem.getPoints() : 1;
        if (request.getScore() != null && request.getScore() > maxPoints) {
            throw new IllegalArgumentException("점수는 배점(" + maxPoints + ")을 초과할 수 없습니다");
        }

        QuizGrade grade = quizGradeRepository
                .findByQuizIdAndProblemIdAndStudentId(
                        request.getQuizId(), request.getProblemId(), request.getUserId()
                )
                .orElse(new QuizGrade());

        if (grade.getId() == null) {
            grade.setQuiz(quiz);
            grade.setProblem(quizProblem.getProblem());
            grade.setStudent(userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다")));
            grade.setPoints(maxPoints);
        }
        grade.setScore(request.getScore());
        grade.setComment(request.getComment());
        grade.setGradedBy(userRepository.findById(tutorId)
                .orElseThrow(() -> new IllegalArgumentException("튜터를 찾을 수 없습니다")));
        grade.setGradedAt(LocalDateTime.now());

        QuizGrade saved = quizGradeRepository.save(grade);

        List<Submission> latestList = submissionRepository.findLatestSubmissionsByUserAndProblem(
                request.getUserId(), request.getProblemId(), section.getId(), PageRequest.of(0, 1));
        Submission sub = latestList.isEmpty() ? null : latestList.get(0);

        return toQuizGradeResponseDTO(saved, quiz, sub);
    }

    /**
     * 퀴즈 일괄 성적 저장
     */
    public List<QuizGradeResponseDTO> saveBulkQuizGrades(QuizBulkGradeRequestDTO request, Long tutorId) {
        List<QuizGradeResponseDTO> results = new ArrayList<>();
        for (QuizGradeRequestDTO g : request.getGrades()) {
            g.setQuizId(request.getQuizId());
            try {
                results.add(saveQuizGrade(g, tutorId));
            } catch (Exception e) {
                // 개별 실패 시 로그만 남기고 계속
            }
        }
        return results;
    }

    /**
     * 퀴즈 문제 배점 설정
     */
    public void setQuizProblemPoints(Long quizId, Long problemId, Integer points) {
        if (points == null || points < 0) {
            throw new IllegalArgumentException("배점은 0 이상이어야 합니다");
        }
        QuizProblem qp = quizProblemRepository
                .findByQuizIdAndProblemId(quizId, problemId)
                .orElseThrow(() -> new IllegalArgumentException("퀴즈 문제를 찾을 수 없습니다"));
        qp.setPoints(points);
        quizProblemRepository.save(qp);
    }

    /**
     * 퀴즈 문제 배점 일괄 설정
     */
    public void setBulkQuizProblemPoints(Long quizId, Map<Long, Integer> problemPoints) {
        for (Map.Entry<Long, Integer> entry : problemPoints.entrySet()) {
            setQuizProblemPoints(quizId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 퀴즈 제출 코드 조회 (학생의 accept된 코드)
     */
    public StudentAcceptedCodeResponse getStudentAcceptedCode(
            Long sectionId, Long quizId, Long userId, Long problemId, Long instructorId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));
        if (!sectionRoleService.isManager(instructorId, sectionId)) {
            throw new IllegalArgumentException("해당 분반의 학생 코드를 조회할 권한이 없습니다");
        }
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!quiz.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("해당 퀴즈는 이 분반에 속하지 않습니다");
        }
        QuizProblem qp = quizProblemRepository.findByQuizIdAndProblemId(quizId, problemId)
                .orElseThrow(() -> new IllegalArgumentException("해당 문제는 이 퀴즈에 포함되어 있지 않습니다"));
        List<Submission> latestList = submissionRepository.findLatestSubmissionsByUserAndProblem(
                userId, problemId, sectionId, PageRequest.of(0, 1));
        if (latestList.isEmpty()) {
            throw new IllegalArgumentException("해당 학생의 제출 기록이 없습니다");
        }
        Submission last = latestList.get(0);
        return StudentAcceptedCodeResponse.builder()
                .submissionId(last.getId())
                .userId(student.getId())
                .studentId(student.getStudentId() != null ? student.getStudentId() : student.getEmail())
                .studentName(student.getName())
                .problemId(problem.getId())
                .problemTitle(problem.getTitle())
                .code(last.getCode())
                .language(last.getLanguage())
                .submittedAt(last.getSubmittedAt())
                .result(last.getResult())
                .build();
    }

    private QuizGradeResponseDTO toQuizGradeResponseDTO(QuizGrade grade, Quiz quiz, Submission submission) {
        QuizGradeResponseDTO.QuizGradeResponseDTOBuilder b = QuizGradeResponseDTO.builder()
                .id(grade.getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .problemId(grade.getProblem().getId())
                .problemTitle(grade.getProblem().getTitle())
                .userId(grade.getStudent().getId())
                .studentName(grade.getStudent().getName())
                .studentId(grade.getStudent().getStudentId() != null ? grade.getStudent().getStudentId() : grade.getStudent().getEmail())
                .points(grade.getPoints())
                .score(grade.getScore())
                .comment(grade.getComment())
                .gradedAt(grade.getGradedAt());

        if (grade.getGradedBy() != null) {
            b.gradedByName(grade.getGradedBy().getName());
        }
        if (submission != null) {
            b.submitted(true)
                    .submittedAt(submission.getSubmittedAt())
                    .result(submission.getResult());
            if (quiz.getEndTime() != null) {
                b.isOnTime(
                        submission.getSubmittedAt().isBefore(quiz.getEndTime()) ||
                        submission.getSubmittedAt().isEqual(quiz.getEndTime())
                );
            } else {
                b.isOnTime(true);
            }
        } else {
            b.submitted(false).isOnTime(false);
        }
        return b.build();
    }

    /**
     * Quiz 상태 자동 업데이트
     * - 종료 시각 경과 시 항상 ENDED
     * - manualStatusOverride면 수동 상태 유지 (종료 시각 제외)
     * - 그 외에는 시간 기반 자동 결정
     */
    private void updateQuizStatus(Quiz quiz) {
        LocalDateTime now = LocalDateTime.now();

        // 종료 시각 경과 시 항상 ENDED (안전)
        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            if (quiz.getStatus() != Quiz.QuizStatus.ENDED) {
                quiz.setStatus(Quiz.QuizStatus.ENDED);
                quiz.setManualStatusOverride(false);
                quizRepository.save(quiz);
            }
            return;
        }

        // 수동 제어 중이면 자동 전환하지 않음
        if (Boolean.TRUE.equals(quiz.getManualStatusOverride())) {
            return;
        }

        Quiz.QuizStatus newStatus = determineStatus(quiz.getStartTime(), quiz.getEndTime());
        if (quiz.getStatus() != newStatus) {
            quiz.setStatus(newStatus);
            quizRepository.save(quiz);
        }
    }

    /**
     * 퀴즈 상태 수동 변경 (시작/정지/종료)
     */
    public QuizResponse updateQuizStatus(Long quizId, Quiz.QuizStatus newStatus, Long instructorId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(instructorId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 코딩 테스트의 상태를 변경할 권한이 없습니다");
        }

        // 종료 시각 경과 시 ENDED만 허용
        LocalDateTime now = LocalDateTime.now();
        if (quiz.getEndTime() != null && now.isAfter(quiz.getEndTime())) {
            if (newStatus != Quiz.QuizStatus.ENDED) {
                throw new IllegalArgumentException("종료 시각이 지난 코딩 테스트는 ENDED 상태만 가능합니다");
            }
        }

        // 유효한 상태 전이 검증
        Quiz.QuizStatus current = quiz.getStatus();
        boolean valid;
        switch (current) {
            case WAITING:
                valid = newStatus == Quiz.QuizStatus.ACTIVE;
                break;
            case ACTIVE:
                valid = newStatus == Quiz.QuizStatus.PAUSED || newStatus == Quiz.QuizStatus.ENDED;
                break;
            case PAUSED:
                valid = newStatus == Quiz.QuizStatus.ACTIVE || newStatus == Quiz.QuizStatus.ENDED;
                break;
            case ENDED:
                valid = false;
                break;
            default:
                valid = false;
        }
        if (!valid) {
            throw new IllegalArgumentException("허용되지 않은 상태 전이입니다. 현재: " + current + ", 요청: " + newStatus);
        }

        quiz.setStatus(newStatus);
        quiz.setManualStatusOverride(true);
        Quiz updated = quizRepository.save(quiz);

        return toResponse(updated);
    }

    /**
     * 시작/종료 시간에 따라 상태 결정
     */
    private Quiz.QuizStatus determineStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(startTime)) {
            return Quiz.QuizStatus.WAITING;
        } else if (now.isAfter(endTime)) {
            return Quiz.QuizStatus.ENDED;
        } else {
            return Quiz.QuizStatus.ACTIVE;
        }
    }

    /**
     * 퀴즈 제출 기록 목록 조회 (튜터용)
     */
    @Transactional(readOnly = true)
    public QuizSubmissionListResponse getQuizSubmissions(
            Long sectionId, Long quizId, Long problemId, Long userId, String result,
            int page, int size, Long tutorId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(tutorId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 제출 기록을 조회할 권한이 없습니다");
        }

        if (!quiz.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("해당 퀴즈는 이 분반에 속하지 않습니다");
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<Submission> pageResult = submissionRepository.findQuizSubmissions(
                sectionId, quizId, problemId, userId, (result != null && !result.isBlank()) ? result : null, pageable);

        List<QuizSubmissionRecordDto> content = pageResult.getContent().stream()
                .map(s -> {
                    User u = s.getUser();
                    String sid = u.getStudentId() != null ? u.getStudentId() : (u.getEmail() != null ? u.getEmail() : "");
                    return QuizSubmissionRecordDto.builder()
                            .submissionId(s.getId())
                            .userId(u.getId())
                            .studentId(sid)
                            .studentName(u.getName())
                            .problemId(s.getProblem().getId())
                            .problemTitle(s.getProblem().getTitle())
                            .submittedAt(s.getSubmittedAt())
                            .result(s.getResult())
                            .language(s.getLanguage())
                            .build();
                })
                .collect(Collectors.toList());

        return QuizSubmissionListResponse.builder()
                .content(content)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .number(pageResult.getNumber())
                .size(pageResult.getSize())
                .build();
    }

    /**
     * 튜터용 퀴즈 제출 코드 조회 (분반 관리자만 해당 분반 학생의 제출 코드 조회 가능)
     */
    @Transactional(readOnly = true)
    public SubmissionCodeDto getQuizSubmissionCodeForTutor(Long sectionId, Long quizId, Long submissionId, Long tutorId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        if (!sectionRoleService.isManager(tutorId, quiz.getSection().getId())) {
            throw new IllegalArgumentException("해당 퀴즈의 제출 코드를 조회할 권한이 없습니다");
        }

        if (!quiz.getSection().getId().equals(sectionId)) {
            throw new IllegalArgumentException("해당 퀴즈는 이 분반에 속하지 않습니다");
        }

        List<Long> quizProblemIds = quizProblemRepository.findByQuizId(quizId).stream()
                .map(qp -> qp.getProblem().getId())
                .collect(Collectors.toList());

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("제출 정보를 찾을 수 없습니다: " + submissionId));

        if (!submission.getSection().getId().equals(sectionId)) {
            throw new RuntimeException("해당 제출은 이 분반에 속하지 않습니다");
        }

        if (!quizProblemIds.contains(submission.getProblem().getId())) {
            throw new RuntimeException("해당 제출은 이 퀴즈의 문제가 아닙니다");
        }

        return SubmissionCodeDto.builder()
                .submissionId(submission.getId())
                .problemTitle(submission.getProblem().getTitle())
                .sectionName(submission.getSection().getCourse().getTitle() + " - " +
                        (submission.getSection().getSectionNumber() != null ? submission.getSection().getSectionNumber() + "분반" : ""))
                .language(submission.getLanguage())
                .code(submission.getCode())
                .result(submission.getResult())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }

    private QuizResponse toResponse(Quiz quiz) {
        int problemCount = quizProblemRepository.findByQuizId(quiz.getId()).size();

        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .startTime(quiz.getStartTime())
                .endTime(quiz.getEndTime())
                .status(quiz.getStatus().name())
                .active(quiz.getActive())
                .problemCount(problemCount)
                .build();
    }

    /**
     * Quiz -> QuizListDto 변환
     */
    private QuizListDto toListDto(Quiz quiz) {
        int problemCount = quizProblemRepository.findByQuizId(quiz.getId()).size();

        return QuizListDto.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .startTime(quiz.getStartTime())
                .endTime(quiz.getEndTime())
                .status(quiz.getStatus().name())
                .problemCount(problemCount)
                .active(quiz.getActive())
                .build();
    }
}

