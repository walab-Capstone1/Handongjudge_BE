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
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.entity.SectionUserRole;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.repository.SectionUserRoleRepository;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.quiz.repository.QuizProblemRepository;
import com.project.handongjudge.submission.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SectionRepository sectionRepository;
    private final SectionUserRoleRepository sectionUserRoleRepository;
    private final SectionRoleService sectionRoleService;
    private final DomjudgeService domjudgeService;
    private final UserReadStatusRepository userReadStatusRepository;
    private final NoticeRepository noticeRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final QuizProblemRepository quizProblemRepository;

    @Autowired
    public UserService(UserRepository userRepository,
                       EnrollmentRepository enrollmentRepository,
                       SectionRepository sectionRepository,
                       SectionUserRoleRepository sectionUserRoleRepository,
                       SectionRoleService sectionRoleService,
                       DomjudgeService domjudgeService,
                       UserReadStatusRepository userReadStatusRepository,
                       NoticeRepository noticeRepository,
                       AssignmentRepository assignmentRepository,
                       AssignmentProblemRepository assignmentProblemRepository,
                       SubmissionRepository submissionRepository,
                       ProblemRepository problemRepository,
                       QuizProblemRepository quizProblemRepository,
                       @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sectionRepository = sectionRepository;
        this.sectionUserRoleRepository = sectionUserRoleRepository;
        this.sectionRoleService = sectionRoleService;
        this.domjudgeService = domjudgeService;
        this.userReadStatusRepository = userReadStatusRepository;
        this.noticeRepository = noticeRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentProblemRepository = assignmentProblemRepository;
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.quizProblemRepository = quizProblemRepository;
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
     * 🔥 로그인한 사용자의 대시보드에 보여줄 과목들 조회
     * @param instructorOnly true면 내가 강사인 분반만 반환 (튜터 페이지용), false면 강사 분반 + 수강 중인 분반
     */
    public List<DashboardCourseDto> getDashboardCourses(Long userId, boolean instructorOnly) {
        if (instructorOnly) {
            List<DashboardCourseDto> instructorSections = enrollmentRepository.findDashboardCoursesByInstructorId(userId);
            instructorSections.forEach(dto -> dto.setRoleInSection("INSTRUCTOR"));

            List<SectionUserRole> managingSections = sectionRoleService.getManagingSections(userId);
            Set<Long> instructorSectionIds = instructorSections.stream()
                    .map(DashboardCourseDto::getSectionId)
                    .collect(Collectors.toSet());
            List<Long> tutorOnlySectionIds = managingSections.stream()
                    .map(sur -> sur.getSection().getId())
                    .filter(id -> !instructorSectionIds.contains(id))
                    .distinct()
                    .collect(Collectors.toList());

            if (tutorOnlySectionIds.isEmpty()) {
                return instructorSections;
            }
            List<DashboardCourseDto> tutorSections = enrollmentRepository.findDashboardCoursesBySectionIds(tutorOnlySectionIds);
            Map<Long, String> sectionIdToRole = managingSections.stream()
                    .collect(Collectors.toMap(sur -> sur.getSection().getId(), sur -> sur.getRole().name(), (a, b) -> a));
            tutorSections.forEach(dto -> dto.setRoleInSection(sectionIdToRole.getOrDefault(dto.getSectionId(), "TUTOR")));

            List<DashboardCourseDto> result = new ArrayList<>(instructorSections);
            result.addAll(tutorSections);
            return result;
        }
        return getDashboardCourses(userId);
    }

    /**
     * 로그인한 사용자의 대시보드에 보여줄 수강 중인 과목들 조회 (강사 분반 + 수강 분반)
     */
    public List<DashboardCourseDto> getDashboardCourses(Long userId) {
        // 사용자 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        List<DashboardCourseDto> result = new ArrayList<>();

        // 1. 자신이 instructor인 분반들 (새 수업 만들기로 생성한 경우 포함 - 역할 무관)
        List<DashboardCourseDto> instructorSections = enrollmentRepository.findDashboardCoursesByInstructorId(userId);
        result.addAll(instructorSections);

        // 2. 자신이 학생으로 수강 등록된 분반들
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


        // 학생인 경우에만 읽지 않은 공지사항 수를 수동으로 계산
        if (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUPER_ADMIN) {
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

        // SectionUserRole에 STUDENT 역할 부여
        sectionRoleService.assignStudentRole(enrollment.getSection().getId(), enrollment.getUser().getId());

        EnrollmentResponseDTO response = new EnrollmentResponseDTO();
        response.setId(enrollment.getId());
        response.setTeamId(enrollment.getTeamId());
        response.setSectionId(enrollment.getSection().getId().toString());
        response.setRoleInCourse(enrollment.getRoleInCourse());
        return response;


    }

    /**
     * 특정 분반의 수강생·관리자 목록 조회 (과제 진도율 포함)
     * Enrollment에 있는 수강생 + SectionUserRole에만 있는 ADMIN/TUTOR 포함 (어드민이 목록 맨 위에 오도록)
     */
    public List<StudentDto> getStudentsBySection(Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + sectionId));

        // Enrollment 기준 수강생 목록 (진도율 계산)
        List<StudentDto> fromEnrollment = enrollmentRepository.findStudentsBySectionId(sectionId);
        Map<Long, StudentDto> userIdToDto = new HashMap<>();
        for (StudentDto dto : fromEnrollment) {
            calculateAssignmentProgress(dto, sectionId);
            userIdToDto.put(dto.getUserId(), dto);
        }

        // 분반 내 모든 역할(ADMIN, TUTOR, STUDENT) 기준으로 목록 구성
        List<SectionUserRole> sectionRoles = sectionUserRoleRepository.findBySectionId(sectionId);
        String sectionName = section.getCourse().getTitle() + " - "
                + (section.getSectionNumber() != null ? section.getSectionNumber() + "분반" : "");
        Integer sectionNumber = section.getSectionNumber();

        List<StudentDto> result = new ArrayList<>();
        for (SectionUserRole sur : sectionRoles) {
            Long userId = sur.getUser().getId();
            String roleName = sur.getRole().name();
            if (userIdToDto.containsKey(userId)) {
                StudentDto dto = userIdToDto.get(userId);
                dto.setRole(roleName);
                result.add(dto);
                userIdToDto.remove(userId); // 중복 방지
            } else {
                // Enrollment에 없고 SectionUserRole에만 있는 경우 (ADMIN·튜터 등)
                User u = sur.getUser();
                StudentDto dto = StudentDto.builder()
                        .userId(u.getId())
                        .name(u.getName())
                        .email(u.getEmail() != null ? u.getEmail() : "")
                        .studentId(u.getStudentId() != null ? u.getStudentId() : "")
                        .teamId("")
                        .sectionId(sectionId)
                        .sectionName(sectionName)
                        .courseTitle(section.getCourse().getTitle())
                        .sectionNumber(sectionNumber)
                        .enrolledAt(null)
                        .lastLogin(null)
                        .assignmentCompletionRate(0.0)
                        .completedAssignments(0)
                        .totalAssignments(assignmentRepository.countBySectionId(sectionId))
                        .role(roleName)
                        .build();
                result.add(dto);
            }
        }

        // Enrollment에만 있고 SectionUserRole에 없는 경우(레거시) STUDENT로 추가
        for (StudentDto dto : userIdToDto.values()) {
            dto.setRole(sectionRoleService.getUserRoleInSection(dto.getUserId(), sectionId)
                    .map(Enum::name)
                    .orElse("STUDENT"));
            result.add(dto);
        }

        // 기본 정렬: 관리자(ADMIN)가 항상 위, 그다음 튜터(TUTOR), 그다음 수강생(STUDENT)
        result.sort((a, b) -> {
            int order = roleOrder(a.getRole()) - roleOrder(b.getRole());
            if (order != 0) return order;
            return Long.compare(a.getUserId(), b.getUserId());
        });
        return result;
    }

    private static int roleOrder(String role) {
        if (role == null) return 2;
        if ("ADMIN".equals(role)) return 0;
        if ("TUTOR".equals(role)) return 1;
        return 2; // STUDENT
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

    /** 분반·문제별 submitted_at 가장 늦은 제출 1건 기준으로 정답/제출/미제출 및 제출 시각 표시 (성적과 동일) */
    public List<StudentProblemStatusDto> getStudentAssignmentProblemsStatus(Long userId, Long sectionId, Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        LocalDateTime endDate = assignment != null ? assignment.getEndDate() : null;

        List<Long> problemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignmentId);
        List<StudentProblemStatusDto> problemStatusList = new ArrayList<>();

        for (Long problemId : problemIds) {
            Problem problem = problemRepository.findById(problemId).orElse(null);
            if (problem == null) continue;

            String status = "NOT_SUBMITTED";
            Boolean isOnTime = null;
            LocalDateTime submittedAt = null;
            Integer minutesLate = null;

            List<Submission> latestList = submissionRepository.findLatestSubmissionsByUserAndProblem(
                    userId, problemId, sectionId, PageRequest.of(0, 1));
            if (!latestList.isEmpty()) {
                Submission latest = latestList.get(0);
                submittedAt = latest.getSubmittedAt();
                status = "AC".equals(latest.getResult()) ? "ACCEPTED" : "SUBMITTED";
                if (endDate != null) {
                    isOnTime = !submittedAt.isAfter(endDate);
                    if (submittedAt.isAfter(endDate)) {
                        minutesLate = (int) ChronoUnit.MINUTES.between(endDate, submittedAt);
                    }
                } else {
                    isOnTime = true;
                }
            }

            int submissionCount = submissionRepository.countByUserIdAndProblemId(userId, problemId);
            problemStatusList.add(StudentProblemStatusDto.builder()
                    .problemId(problemId)
                    .problemTitle(problem.getTitle())
                    .status(status)
                    .submissionCount(submissionCount)
                    .isOnTime(isOnTime)
                    .submittedAt(submittedAt)
                    .minutesLate(minutesLate)
                    .build());
        }

        return problemStatusList;
    }

    /**
     * 특정 학생의 특정 코딩 퀴즈(코딩 테스트)의 문제별 제출 상태 조회
     */
    public List<StudentProblemStatusDto> getStudentQuizProblemsStatus(Long userId, Long sectionId, Long quizId) {
        List<Long> problemIds = quizProblemRepository.findProblemIdsByQuizId(quizId);

        List<StudentProblemStatusDto> problemStatusList = new ArrayList<>();

        for (Long problemId : problemIds) {
            Problem problem = problemRepository.findById(problemId).orElse(null);
            if (problem == null) continue;

            String status = "NOT_SUBMITTED";
            int submissionCount = submissionRepository.countByUserIdAndProblemId(userId, problemId);

            if (submissionCount > 0) {
                int acceptedCount = submissionRepository.countAcceptedByUserIdAndProblemId(userId, problemId);
                status = acceptedCount > 0 ? "ACCEPTED" : "SUBMITTED";
            }

            problemStatusList.add(StudentProblemStatusDto.builder()
                    .problemId(problemId)
                    .problemTitle(problem.getTitle())
                    .status(status)
                    .submissionCount(submissionCount)
                    .build());
        }

        return problemStatusList;
    }

    /**
     * 관리자 대시보드 통계 조회
     */
    public AdminDashboardStatsDto getAdminDashboardStats(Long instructorId) {
        // 사용자 조회 및 권한 확인
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        if (user.getRole() != User.Role.ADMIN && user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("관리자 권한이 필요합니다.");
        }

        // 담당하는 모든 분반 조회
        List<DashboardCourseDto> sections = enrollmentRepository.findDashboardCoursesByInstructorId(instructorId);
        List<Long> sectionIds = sections.stream()
                .map(DashboardCourseDto::getSectionId)
                .collect(java.util.stream.Collectors.toList());

        // 전체 통계 계산
        long totalSections = sections.size();
        long totalAssignments = sections.stream()
                .mapToLong(DashboardCourseDto::getAssignmentCount)
                .sum();
        long totalProblems = problemRepository.countByCreatedById(instructorId);
        long totalStudents = sections.stream()
                .mapToLong(DashboardCourseDto::getStudentCount)
                .sum();

        // 최근 7일 활동
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentSubmissions = submissionRepository.countBySubmittedAtAfter(sevenDaysAgo);
        long recentAssignments = assignmentRepository.countByCreatedAtAfterAndSectionInstructorId(sevenDaysAgo, instructorId);

        // 과제별 통계
        List<AdminDashboardStatsDto.AssignmentStatsDto> assignmentStats = new ArrayList<>();
        if (!sectionIds.isEmpty()) {
            List<Assignment> assignments = assignmentRepository.findBySectionIdIn(sectionIds);
            for (Assignment assignment : assignments) {
                Long sectionId = assignment.getSection().getId();
                Long totalStudentsInSection = enrollmentRepository.countBySectionId(sectionId);
                
                // 과제의 문제 수
                List<Long> problemIds = assignmentProblemRepository.findProblemIdsByAssignmentId(assignment.getId());
                
                // 제출한 학생 수 (과제의 문제 중 하나라도 제출한 학생)
                long submittedStudents = submissionRepository.countDistinctUsersByProblemIdsAndSectionId(problemIds, sectionId);
                
                // 완료한 학생 수 (과제의 모든 문제를 정답으로 제출한 학생)
                // 간단하게 계산: 각 학생이 푼 문제 수를 확인
                long completedStudents = 0;
                if (!problemIds.isEmpty()) {
                    // 분반의 모든 학생 조회
                    List<User> students = enrollmentRepository.findUsersBySectionId(sectionId);
                    for (User student : students) {
                        // 학생이 푼 문제 ID 목록 (정답만)
                        List<Long> solvedProblemIds = submissionRepository.findAcceptedProblemIdsByUserAndProblems(
                            student.getId(), problemIds);
                        // 모든 문제를 다 풀었는지 확인
                        if (solvedProblemIds.size() == problemIds.size() && 
                            solvedProblemIds.containsAll(problemIds)) {
                            completedStudents++;
                        }
                    }
                }

                double submissionRate = totalStudentsInSection > 0 
                    ? (double) submittedStudents / totalStudentsInSection * 100 
                    : 0.0;
                double completionRate = totalStudentsInSection > 0 
                    ? (double) completedStudents / totalStudentsInSection * 100 
                    : 0.0;

                assignmentStats.add(AdminDashboardStatsDto.AssignmentStatsDto.builder()
                        .assignmentId(assignment.getId())
                        .assignmentTitle(assignment.getTitle())
                        .sectionId(sectionId)
                        .sectionTitle(sections.stream()
                                .filter(s -> s.getSectionId().equals(sectionId))
                                .findFirst()
                                .map(DashboardCourseDto::getCourseTitle)
                                .orElse(""))
                        .totalStudents(totalStudentsInSection)
                        .submittedStudents(submittedStudents)
                        .completedStudents(completedStudents)
                        .submissionRate(submissionRate)
                        .completionRate(completionRate)
                        .build());
            }
        }

        // 수업별 통계
        List<AdminDashboardStatsDto.SectionStatsDto> sectionStats = sections.stream()
                .map(section -> AdminDashboardStatsDto.SectionStatsDto.builder()
                        .sectionId(section.getSectionId())
                        .sectionTitle(section.getCourseTitle() + " " + section.getSectionNumber() + "분반")
                        .studentCount(section.getStudentCount())
                        .assignmentCount(section.getAssignmentCount())
                        .activeAssignmentCount(assignmentRepository.countActiveBySectionId(section.getSectionId()))
                        .noticeCount(section.getNoticeCount())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        return AdminDashboardStatsDto.builder()
                .totalSections(totalSections)
                .totalAssignments(totalAssignments)
                .totalProblems(totalProblems)
                .totalStudents(totalStudents)
                .recentSubmissions(recentSubmissions)
                .recentAssignments(recentAssignments)
                .assignmentStats(assignmentStats)
                .sectionStats(sectionStats)
                .build();
    }
}