package com.project.handongjudge.auth.service;

import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.service.UserService;
import com.project.handongjudge.auth.dto.OAuth2UserInfo;
import com.project.handongjudge.auth.entity.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * OAuth2 사용자 서비스
 * 
 * 주요 기능:
 * - OAuth2 제공자로부터 사용자 정보 로드
 * - 기존 사용자 확인 또는 새 사용자 생성
 * - OAuth2 사용자 정보를 내부 사용자 엔티티로 변환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    /**
     * OAuth2 사용자 정보 로드 및 처리
     * 
     * @param userRequest OAuth2 사용자 요청
     * @return OAuth2 사용자 (내부 사용자 정보 포함)
     * @throws OAuth2AuthenticationException OAuth2 인증 실패 시
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // OAuth2 제공자로부터 사용자 정보 가져오기
        OAuth2User oauth2User = super.loadUser(userRequest);

        // OAuth2 제공자 정보 추출
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        
        // OAuth2 사용자 정보를 내부 DTO로 변환
        OAuth2UserInfo userInfo = OAuth2UserInfo.of(registrationId, oauth2User.getAttributes());

        // 기존 사용자 확인 또는 새 사용자 생성
        User user = saveOrUpdate(userInfo);

        // OAuth2 사용자와 내부 사용자 정보를 결합하여 반환
        return new AuthUser(oauth2User, user);
    }

    /**
     * OAuth2 사용자 정보 저장 또는 업데이트
     * 
     * @param userInfo OAuth2 사용자 정보
     * @return 저장된 또는 업데이트된 사용자 엔티티
     */
    private User saveOrUpdate(OAuth2UserInfo userInfo) {
        // OAuth 제공자 타입 변환
        User.AuthProvider provider = User.AuthProvider.valueOf(userInfo.getProvider().toUpperCase());

        // 기존 사용자 확인 (OAuth 제공자 + 제공자 ID로 조회)
        Optional<User> existingUser = userService.findByProviderAndProviderId(provider, userInfo.getId());

        if (existingUser.isPresent()) {
            // 기존 사용자가 있으면 반환
            log.info("Existing OAuth user found: {} ({})", userInfo.getEmail(), provider);
            return existingUser.get();
        } else {
            // 새 사용자 생성
            log.info("Creating new OAuth user: {} ({})", userInfo.getEmail(), provider);
            return userService.createOAuthUser(
                    userInfo.getEmail(),
                    userInfo.getName(),
                    userInfo.getImageUrl(),
                    provider,
                    userInfo.getId()
            );
        }
    }
}