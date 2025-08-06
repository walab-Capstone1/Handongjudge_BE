package com.project.handongjudge.user.service;

import com.project.handongjudge.user.dto.DashboardCourseDto;
import com.project.handongjudge.user.dto.UserDto;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.dto.EnrollmentRequestDTO;
import com.project.handongjudge.user.entity.Enrollment;
import com.project.handongjudge.section.repository.SectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;    
import com.project.handongjudge.problem.service.DomjudgeService;    
import com.project.handongjudge.user.dto.EnrollmentResponseDTO;
import java.util.List;
import java.util.Optional;
import java.util.Map;
@Service
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SectionRepository sectionRepository;
    private final DomjudgeService domjudgeService;
    @Autowired
    public UserService(UserRepository userRepository,
                       EnrollmentRepository enrollmentRepository,
                       SectionRepository sectionRepository,
                       DomjudgeService domjudgeService,
                       @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sectionRepository = sectionRepository;
        this.domjudgeService = domjudgeService;
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
        return enrollmentRepository.findDashboardCoursesByUserId(userId);
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
}
