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

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfo.of(registrationId, oauth2User.getAttributes());

        User user = saveOrUpdate(userInfo);

        return new AuthUser(oauth2User, user);
    }

    private User saveOrUpdate(OAuth2UserInfo userInfo) {
        User.AuthProvider provider = User.AuthProvider.valueOf(userInfo.getProvider().toUpperCase());

        Optional<User> existingUser = userService.findByProviderAndProviderId(provider, userInfo.getId());

        if (existingUser.isPresent()) {
            return existingUser.get();
        } else {
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