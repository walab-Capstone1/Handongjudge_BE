package com.project.handongjudge.auth.entity;

import com.project.handongjudge.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuthUser implements OAuth2User {
    private final OAuth2User oauth2User;
    @Getter
    private final User user;

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

//    @Override
//    public String getName() {
//        return user.getEmail();
//    }

    @Override
    public String getName() {
        // 사용자 ID를 principal name으로 사용 (항상 존재하고 유니크함)
        String userId = user.getId().toString();
        log.debug("Using user ID as principal name: {}", userId);
        return userId;
    }


}