package com.project.handongjudge.user.service;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.dto.*;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.entity.UserReadStatus;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.repository.UserReadStatusRepository;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SectionRepository sectionRepository;
    private final DomjudgeService domjudgeService;
    private final UserReadStatusRepository userReadStatusRepository;
    private final NoticeRepository noticeRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       EnrollmentRepository enrollmentRepository,
                       SectionRepository sectionRepository,
                       DomjudgeService domjudgeService,
                       UserReadStatusRepository userReadStatusRepository,
                       NoticeRepository noticeRepository,
                       AssignmentRepository assignmentRepository,
                       AssignmentProblemRepository assignmentProblemRepository,
                       SubmissionRepository submissionRepository,
                       ProblemRepository problemRepository,
                       @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sectionRepository = sectionRepository;
        this.domjudgeService = domjudgeService;
        this.userReadStatusRepository = userReadStatusRepository;
        this.noticeRepository = noticeRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentProblemRepository = assignmentProblemRepository;
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Transactional
    public User createUser(String email, String password, String name, String studentId) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
                .studentId(studentId)
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User createOAuthUser(String email, String name, String profileImageUrl,
                                User.AuthProvider provider, String providerId) {
        User user = User.builder()
                .email(email)
                .name(name)
                .profileImageUrl(profileImageUrl)
                .provider(provider)
                .providerId(providerId)
                .role(User.Role.USER)
                .build();

        return userRepository.save(user);
    }

    public UserDto getUserInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return UserDto.from(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public UserDto getUserInfoById(String userId) {
        Long userIdLong = Long.parseLong(userId);
        User user = userRepository.findById(userIdLong)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        return UserDto.from(user);
    }

    public UserDto getUserInfoById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        return UserDto.from(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 🔥 로그인한 사용자의 대시보드에 보여줄 수강 중인 과목들 조회
     */
    public List<DashboardCourseDto> getDashboardCourses(Long userId) {

        // 사용자 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        List<DashboardCourseDto> result = new ArrayList<>();

        // 사용자 역할에 따라 다른 쿼리 사용
        if (user.getRole() == User.Role.ADMIN) {
            // 교수인 경우: 담당하는 분반들 + 학생으로 등록된 분반들 모두 조회

            // 1. 자신이 instructor인 분반들
            List<DashboardCourseDto> instructorSections = enrollmentRepository.findDashboardCoursesByInstructorId(userId);
            result.addAll(instructorSections);

            // 2. 자신이 학생으로 enrollment에 등록된 분반들
            List<DashboardCourseDto> enrolledSections = enrollmentRepository.findDashboardCoursesByUserId(userId);
            result.addAll(enrolledSections);

            // 중복 제거 (같은 sectionId가 있을 수 있으므로)
            result = result.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            DashboardCourseDto::getSectionId,
                            dto -> dto,
                            (existing, replacement) -> existing
                    ))
                    .values()
                    .stream()
                    .collect(java.util.stream.Collectors.toList());

        } else {
            // 학생인 경우: 수강하는 분반들 조회
            result = enrollmentRepository.findDashboardCoursesByUserId(userId);
        }


        // 학생인 경우에만 읽지 않은 공지사항 수를 수동으로 계산
        if (user.getRole() != User.Role.ADMIN) {
            for (DashboardCourseDto dto : result) {
                // 해당 분반의 새로운 공지사항 중 읽지 않은 것들의 수 계산
                long unreadNoticeCount = noticeRepository.findNewNoticesBySectionId(dto.getSectionId())
                        .stream()
                        .filter(notice -> !userReadStatusRepository.existsByUserIdAndNoticeId(userId, notice.getId()))
                        .count();


                // 수동 계산된 값으로 업데이트 (리플렉션 사용)
                try {
                    java.lang.reflect.Field field = dto.getClass().getDeclaredField("newNoticeCount");
                    field.setAccessible(true);
                    field.set(dto, unreadNoticeCount);
                } catch (Exception e) {
                    log.error(" newNoticeCount 업데이트 실패", e);
                }
            }
        } else {
            // 교수인 경우 로그만 출력
            for (DashboardCourseDto dto : result) {
                log.info(" 교수 분반 정보 - sectionId: {}, courseTitle: {}, newNoticeCount: {}, newAssignmentCount: {}", dto.getSectionId(), dto.getCourseTitle(), dto.getNewNoticeCount(), dto.getNewAssignmentCount());
            }
        }

        return result;
    }

    public EnrollmentResponseDTO enrollCourse(EnrollmentRequestDTO request) { // 팀 생성 및 참가 처리
        // 팀 생성
        Enrollment enrollment = Enrollment.builder()
                .user(userRepository.findById(request.getUserId()).orElseThrow(() -> new RuntimeException("User not found")))
                .section(sectionRepository.findById(request.getSectionId()).orElseThrow(() -> new RuntimeException("Section not found")))
                .roleInCourse(request.getRoleInCourse())
                .build();
        // user

        // 팀 생성
        String teamId = domjudgeService.createTeam(enrollment.getUser().getId(), enrollment.getSection().getId(), enrollment.getUser().getName());
        enrollment.setTeamId(teamId);

        // 참가 처리
        enrollmentRepository.save(enrollment);

        EnrollmentResponseDTO response = new EnrollmentResponseDTO();
        response.setId(enrollment.getId());
        response.setTeamId(enrollment.getTeamId());
        response.setSectionId(enrollment.getSection().getId().toString());
        response.setRoleInCourse(enrollment.getRoleInCourse());
        return response;


    }

    /**
     * 특정 분반의 학생 목록 조회 (과제 진도율 포함)
     */
    public List<StudentDto> getStudentsBySection(Long sectionId) {
        List<StudentDto> students = enrollmentRepository.findStudentsBySectionId(sectionId);

        // 각 학생의 과제 진도율 계산
        for (StudentDto student : students) {
            calculateAssignmentProgress(student, sectionId);
        }

        return students;
    }

    /**
     * 교수가 담당하는 모든 분반의 학생 목록 조회 (과제 진도율 포함)
     */
    public List<StudentDto> getStudentsByInstructor(Long instructorId) {
        List<StudentDto> students = enrollmentRepository.findStudentsByInstructorId(instructorId);

        // 각 학생의 과제 진도율 계산
        for (StudentDto student : students) {
            calculateAssignmentProgress(student, student.getSectionId());
        }

        return students;
    }

    /**
     * 학생의 과제 진도율 계산
     */
    private void calculateAssignmentProgress(StudentDto student, Long sectionId) {
        // 1. 해당 분반의 전체 과제 수
        Integer totalAssignments = assignmentRepository.countBySectionId(sectionId);
        student.setTotalAssignments(totalAssignments);

        if (totalAssignments == 0) {
            student.setCompletedAssignments(0);
            student.setAssignmentCompletionRate(0.0);
            return;
        }

        // 2. 해당 분반의 모든 과제 ID 가져오기
        List<Long> assignmentIds = assignmentRepository.findAssignmentIdsBySectionId(sectionId);

        // 3. 각 과제별로 완료 여부 확인
        int completedCount = 0;
        for (Long assignmentId : assignmentIds) {
            if (isAssignmentCompleted(student.getUserId(), assignmentId)) {
                completedCount++;
            }
        }

        student.setCompletedAssignments(completedCount);

        // 4. 완료율 계산 (%)
        double completionRate = (completedCount * 100.0) / totalAssignments;
        student.setAssignmentCompletionRate(Math.round(completionRate * 10.0) / 10.0); // 소수점 첫째자리까지
    }

    /**
     * 특정 학생이 특정 과제를 완료했는지 확인
     * (과제의 모든 문제를 ACCEPTED로 제출한 경우 완료로 간주)
     */
    private boolean isAssignmentCompleted(Long userId, Long assignmentId) {
        // 1. 해당 과제의 모든 문제 ID 가져오기
        List<Long> problemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignmentId);

        if (problemIds.isEmpty()) {
            return false;
        }

        // 2. 학생이 ACCEPTED를 받은 문제 ID 가져오기
        List<Long> acceptedProblemIds = submissionRepository.findAcceptedProblemIdsByUserAndProblems(userId, problemIds);

        // 3. 모든 문제를 풀었는지 확인
        return acceptedProblemIds.size() == problemIds.size();
    }

    // 공지사항 읽음 처리
    @Transactional
    public void markNoticeAsRead(Long userId, Long noticeId) {
        log.info("🔥 공지사항 읽음 처리 시작 - userId: {}, noticeId: {}", userId, noticeId);

        // 이미 읽음 처리된 경우 중복 방지
        if (userReadStatusRepository.existsByUserIdAndNoticeId(userId, noticeId)) {
            log.info("🔥 이미 읽음 처리된 공지사항 - userId: {}, noticeId: {}", userId, noticeId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        UserReadStatus readStatus = UserReadStatus.builder()
                .user(user)
                .notice(notice)
                .readType(UserReadStatus.ReadType.NOTICE)
                .readAt(LocalDateTime.now())
                .build();

        UserReadStatus saved = userReadStatusRepository.save(readStatus);
        log.info("🔥 공지사항 읽음 처리 완료 - readStatusId: {}", saved.getId());
    }

    // 과제 읽음 처리
    @Transactional
    public void markAssignmentAsRead(Long userId, Long assignmentId) {
        log.info("🔥 과제 읽음 처리 시작 - userId: {}, assignmentId: {}", userId, assignmentId);

        // 이미 읽음 처리된 경우 중복 방지
        if (userReadStatusRepository.existsByUserIdAndAssignmentId(userId, assignmentId)) {
            log.info("🔥 이미 읽음 처리된 과제 - userId: {}, assignmentId: {}", userId, assignmentId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("과제를 찾을 수 없습니다: " + assignmentId));

        UserReadStatus readStatus = UserReadStatus.builder()
                .user(user)
                .assignment(assignment)
                .readType(UserReadStatus.ReadType.ASSIGNMENT)
                .readAt(LocalDateTime.now())
                .build();

        UserReadStatus saved = userReadStatusRepository.save(readStatus);
        log.info("🔥 과제 읽음 처리 완료 - readStatusId: {}", saved.getId());
    }
    /**
     * 특정 학생의 특정 분반 모든 과제에 대한 진도율 조회
     */
    public List<StudentAssignmentProgressDto> getStudentAssignmentsProgress(Long userId, Long sectionId) {
        // 1. 해당 분반의 모든 과제 가져오기
        List<Assignment> assignments = assignmentRepository.findBySectionId(sectionId);

        // 2. 각 과제별로 학생의 진도율 계산
        List<StudentAssignmentProgressDto> progressList = new ArrayList<>();

        for (Assignment assignment : assignments) {
            StudentAssignmentProgressDto progress = calculateStudentAssignmentProgress(userId, assignment);
            progressList.add(progress);
        }

        return progressList;
    }

    /**
     * 특정 학생의 특정 과제 진도율 계산
     */
    private StudentAssignmentProgressDto calculateStudentAssignmentProgress(Long userId, Assignment assignment) {
        // 1. 해당 과제의 모든 문제 ID 가져오기
        List<Long> problemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignment.getId());

        int totalProblems = problemIds.size();
        int solvedProblems = 0;

        if (totalProblems > 0) {
            // 2. 학생이 ACCEPTED를 받은 문제 ID 가져오기
            List<Long> acceptedProblemIds = submissionRepository.findAcceptedProblemIdsByUserAndProblems(userId, problemIds);
            solvedProblems = acceptedProblemIds.size();
        }

        // 3. 진도율 계산
        double progressRate = totalProblems > 0 ? (solvedProblems * 100.0) / totalProblems : 0.0;

        return StudentAssignmentProgressDto.builder()
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .description(assignment.getDescription())
                .totalProblems(totalProblems)
                .solvedProblems(solvedProblems)
                .progressRate(Math.round(progressRate * 10.0) / 10.0)
                .build();
    }
    // SubmissionRepository에서 메서드 제거하고 UserService에서 직접 처리

    // UserService의 getStudentAssignmentProblemsStatus 메서드:
    public List<StudentProblemStatusDto> getStudentAssignmentProblemsStatus(Long userId, Long sectionId, Long assignmentId) {
        // 1. 해당 과제의 모든 문제 가져오기
        List<Long> problemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignmentId);

        List<StudentProblemStatusDto> problemStatusList = new ArrayList<>();

        for (Long problemId : problemIds) {
            // 2. 문제 정보 가져오기
            Problem problem = problemRepository.findById(problemId)
                    .orElse(null);

            if (problem == null) continue;

            // 3. 해당 문제에 대한 학생의 제출 상태 확인
            String status = "NOT_SUBMITTED";
            int submissionCount = submissionRepository.countByUserIdAndProblemId(userId, problemId);

            if (submissionCount > 0) {
                // ACCEPTED("AC")가 있는지 확인
                int acceptedCount = submissionRepository.countAcceptedByUserIdAndProblemId(userId, problemId);

                status = acceptedCount > 0 ? "ACCEPTED" : "SUBMITTED";
            }

            StudentProblemStatusDto statusDto = StudentProblemStatusDto.builder()
                    .problemId(problemId)
                    .problemTitle(problem.getTitle())
                    .status(status)
                    .submissionCount(submissionCount)
                    .build();

            problemStatusList.add(statusDto);
        }

        return problemStatusList;
    }
}