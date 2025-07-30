package com.project.handongjudge.auth.service;

import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.auth.dto.AuthRequestDto;
import com.project.handongjudge.auth.dto.AuthResponseDto;
import com.project.handongjudge.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponseDto login(AuthRequestDto.LoginRequest loginRequest) throws AuthenticationException {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        String token = jwtUtil.generateToken(authentication);

        return AuthResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .build();
    }

    @Transactional
    public AuthResponseDto register(AuthRequestDto.RegisterRequest registerRequest) {
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다.");
        }

        User user = userService.createUser(
                registerRequest.getEmail(),
                registerRequest.getPassword(),
                registerRequest.getName()
        );
        String token = jwtUtil.generateToken(user.getEmail());

        return AuthResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .build();
    }
}