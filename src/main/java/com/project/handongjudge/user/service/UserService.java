package com.project.handongjudge.user.service;

import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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


    // 새 메서드 (ID로 조회)
    public UserDto getUserInfoById(String userId) {
        Long userIdLong = Long.parseLong(userId);
        User user = userRepository.findById(userIdLong)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        return UserDto.from(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

}