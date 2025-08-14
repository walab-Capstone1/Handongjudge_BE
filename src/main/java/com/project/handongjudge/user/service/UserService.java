package com.project.handongjudge.user.service;

import com.project.handongjudge.user.dto.*;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.repository.UserReadStatusRepository;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.user.entity.UserReadStatus;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;    
import com.project.handongjudge.domjudge.service.DomjudgeService;

import java.time.LocalDateTime;

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
    @Autowired
    public UserService(UserRepository userRepository,
                       EnrollmentRepository enrollmentRepository,
                       SectionRepository sectionRepository,
                       DomjudgeService domjudgeService,
                       UserReadStatusRepository userReadStatusRepository,
                       NoticeRepository noticeRepository,
                       AssignmentRepository assignmentRepository,
                       @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sectionRepository = sectionRepository;
        this.domjudgeService = domjudgeService;
        this.userReadStatusRepository = userReadStatusRepository;
        this.noticeRepository = noticeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Transactional
    public User createUser(String email, String password, String name) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .name(name)
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
     * 🔥 로그인한 사용자의 대시보드에 보여줄 수강 중인 과목들 조회 (학생용)
     */
    public List<DashboardCourseDto> getDashboardCourses(Long userId) {
        List<DashboardCourseDto> courses = enrollmentRepository.findDashboardCoursesByUserId(userId);
        log.info("🔥 학생 대시보드 조회 - userId: {}, courses: {}", userId, courses.size());
        for (DashboardCourseDto course : courses) {
            log.info("🔥 섹션 정보 - sectionId: {}, newNoticeCount: {}, newAssignmentCount: {}", 
                    course.getSectionId(), course.getNewNoticeCount(), course.getNewAssignmentCount());
        }
        return courses;
    }

    /**
     * 🔥 교수가 담당하고 있는 분반들 조회 (교수용)
     */
    public List<DashboardCourseDto> getInstructorDashboardCourses(Long instructorId) {
        return enrollmentRepository.findDashboardCoursesByInstructorId(instructorId);
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
    // UserService.java에 추가
    /**
     * 특정 분반의 학생 목록 조회
     */
    public List<StudentDto> getStudentsBySection(Long sectionId) {
        return enrollmentRepository.findStudentsBySectionId(sectionId);
    }

    /**
     * 교수가 담당하는 모든 분반의 학생 목록 조회
     */
    public List<StudentDto> getStudentsByInstructor(Long instructorId) {
        return enrollmentRepository.findStudentsByInstructorId(instructorId);
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
        // 이미 읽음 처리된 경우 중복 방지
        if (userReadStatusRepository.existsByUserIdAndAssignmentId(userId, assignmentId)) {
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

        userReadStatusRepository.save(readStatus);
    }
}
