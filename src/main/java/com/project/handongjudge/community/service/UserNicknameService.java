package com.project.handongjudge.community.service;

import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.dto.UserNicknameDto;
import com.project.handongjudge.community.entity.UserNickname;
import com.project.handongjudge.community.repository.UserNicknameRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserNicknameService {

    private final UserNicknameRepository userNicknameRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;

    /**
     * 사용자의 별명 조회
     */
    public String getNickname(Long userId, Long sectionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        Optional<UserNickname> nicknameOpt = userNicknameRepository.findByUserAndSection(user, section);

        return nicknameOpt.map(UserNickname::getNickname).orElse(null);
    }

    /**
     * 별명 설정/수정
     */
    @Transactional
    public String setNickname(UserNicknameDto dto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Section section = sectionRepository.findById(dto.getSectionId())
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        // 별명 중복 확인 (현재 사용자 제외)
        if (userNicknameRepository.existsBySectionAndNicknameExcludingUser(
                section, dto.getNickname(), userId)) {
            throw new CustomException("이미 사용 중인 별명입니다");
        }

        // 기존 별명이 있으면 수정, 없으면 생성
        Optional<UserNickname> existingNickname = userNicknameRepository.findByUserAndSection(user, section);

        UserNickname nickname;
        if (existingNickname.isPresent()) {
            nickname = existingNickname.get();
            nickname.setNickname(dto.getNickname());
        } else {
            nickname = UserNickname.builder()
                    .user(user)
                    .section(section)
                    .nickname(dto.getNickname())
                    .build();
        }

        UserNickname savedNickname = userNicknameRepository.save(nickname);
        return savedNickname.getNickname();
    }

    /**
     * 별명 중복 확인
     */
    public boolean isNicknameAvailable(Long sectionId, String nickname, Long userId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        return !userNicknameRepository.existsBySectionAndNicknameExcludingUser(
                section, nickname, userId);
    }
}

