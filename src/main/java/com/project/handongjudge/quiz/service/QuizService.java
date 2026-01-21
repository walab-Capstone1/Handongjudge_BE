package com.project.handongjudge.quiz.service;

import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.entity.QuizProblem;
import com.project.handongjudge.quiz.repository.QuizRepository;
import com.project.handongjudge.quiz.repository.QuizProblemRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizProblemRepository quizProblemRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;

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
                .active(true)
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

        // 상태 업데이트
        Quiz.QuizStatus newStatus = determineStatus(request.getStartTime(), request.getEndTime());
        quiz.setStatus(newStatus);

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
     * Quiz 상태 자동 업데이트
     */
    private void updateQuizStatus(Quiz quiz) {
        Quiz.QuizStatus currentStatus = quiz.getStatus();
        Quiz.QuizStatus newStatus = determineStatus(quiz.getStartTime(), quiz.getEndTime());

        if (currentStatus != newStatus) {
            quiz.setStatus(newStatus);
            quizRepository.save(quiz);
        }
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
     * Quiz -> QuizResponse 변환
     */
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
                .build();
    }
}

