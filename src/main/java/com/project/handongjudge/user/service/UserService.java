package com.project.handongjudge.user.service;

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
     * 🔥 로그인한 사용자의 대시보드에 보여줄 수강 중인 과목들 조회
     */
    public List<DashboardCourseDto> getDashboardCourses(Long userId) {
        log.info("🔥 대시보드 조회 시작 - userId: {}", userId);
        
        // 사용자 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        
        List<DashboardCourseDto> result;
        
        // 사용자 역할에 따라 다른 쿼리 사용
        if (user.getRole() == User.Role.ADMIN) {
            // 교수인 경우: 담당하는 분반들 조회
            log.info("🔥 교수용 대시보드 조회 - instructorId: {}", userId);
            result = enrollmentRepository.findDashboardCoursesByInstructorId(userId);
        } else {
            // 학생인 경우: 수강하는 분반들 조회
            log.info("🔥 학생용 대시보드 조회 - studentId: {}", userId);
            result = enrollmentRepository.findDashboardCoursesByUserId(userId);
        }
        
        log.info("🔥 대시보드 조회 결과 - 분반 수: {}", result.size());
        
        // 학생인 경우에만 읽지 않은 공지사항 수를 수동으로 계산
        if (user.getRole() != User.Role.ADMIN) {
            for (DashboardCourseDto dto : result) {
                // 해당 분반의 새로운 공지사항 중 읽지 않은 것들의 수 계산
                long unreadNoticeCount = noticeRepository.findNewNoticesBySectionId(dto.getSectionId())
                    .stream()
                    .filter(notice -> !userReadStatusRepository.existsByUserIdAndNoticeId(userId, notice.getId()))
                    .count();
                
                log.info("🔥 학생 분반 정보 - sectionId: {}, courseTitle: {}, 계산된 읽지않은 공지수: {}, 쿼리결과: {}", 
                    dto.getSectionId(), dto.getCourseTitle(), unreadNoticeCount, dto.getNewNoticeCount());
                
                // 수동 계산된 값으로 업데이트 (리플렉션 사용)
                try {
                    java.lang.reflect.Field field = dto.getClass().getDeclaredField("newNoticeCount");
                    field.setAccessible(true);
                    field.set(dto, unreadNoticeCount);
                } catch (Exception e) {
                    log.error("🔥 newNoticeCount 업데이트 실패", e);
                }
            }
        } else {
            // 교수인 경우 로그만 출력
            for (DashboardCourseDto dto : result) {
                log.info("🔥 교수 분반 정보 - sectionId: {}, courseTitle: {}, newNoticeCount: {}, newAssignmentCount: {}", 
                    dto.getSectionId(), dto.getCourseTitle(), dto.getNewNoticeCount(), dto.getNewAssignmentCount());
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
}
