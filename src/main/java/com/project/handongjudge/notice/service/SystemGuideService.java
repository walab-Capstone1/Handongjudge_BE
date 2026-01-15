package com.project.handongjudge.notice.service;

import com.project.handongjudge.notice.dto.SystemGuideRequestDto;
import com.project.handongjudge.notice.dto.SystemGuideResponseDto;
import com.project.handongjudge.notice.entity.SystemGuide;
import com.project.handongjudge.notice.repository.SystemGuideRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SystemGuideService {

    private final SystemGuideRepository systemGuideRepository;
    private final UserRepository userRepository;

    /**
     * 시스템 이용안내 생성 (SUPER_ADMIN만 가능)
     */
    public SystemGuideResponseDto createSystemGuide(SystemGuideRequestDto requestDto, Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 생성 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 이용안내를 작성할 권한이 없습니다");
        }

        // 이용안내 생성
        SystemGuide guide = SystemGuide.builder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .category(requestDto.getCategory())
                .priority(requestDto.getPriority() != null ? requestDto.getPriority() : 0)
                .active(true)
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        SystemGuide savedGuide = systemGuideRepository.save(guide);
        log.info("시스템 이용안내 생성 완료 - ID: {}, 제목: {}", savedGuide.getId(), savedGuide.getTitle());

        return convertToResponse(savedGuide);
    }

    /**
     * 활성화된 시스템 이용안내 목록 조회 (모든 사용자)
     */
    @Transactional(readOnly = true)
    public List<SystemGuideResponseDto> getActiveSystemGuides() {
        List<SystemGuide> guides = systemGuideRepository.findByActiveTrueOrderByPriorityDescCreatedAtDesc();
        return guides.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 특정 카테고리의 활성화된 이용안내 조회 (모든 사용자)
     */
    @Transactional(readOnly = true)
    public List<SystemGuideResponseDto> getActiveSystemGuidesByCategory(String category) {
        List<SystemGuide> guides = systemGuideRepository.findByCategoryAndActiveTrueOrderByPriorityDescCreatedAtDesc(category);
        return guides.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 모든 시스템 이용안내 목록 조회 (SUPER_ADMIN만)
     */
    @Transactional(readOnly = true)
    public List<SystemGuideResponseDto> getAllSystemGuides(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 조회 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 이용안내 목록을 조회할 권한이 없습니다");
        }

        List<SystemGuide> guides = systemGuideRepository.findAllByOrderByPriorityDescCreatedAtDesc();
        return guides.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 고유한 카테고리 목록 조회
     */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return systemGuideRepository.findDistinctCategories();
    }

    /**
     * 시스템 이용안내 수정 (SUPER_ADMIN만 가능)
     */
    public SystemGuideResponseDto updateSystemGuide(Long guideId, SystemGuideRequestDto requestDto, Long userId) {
        SystemGuide guide = systemGuideRepository.findById(guideId)
                .orElseThrow(() -> new IllegalArgumentException("이용안내를 찾을 수 없습니다: " + guideId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 수정 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 이용안내를 수정할 권한이 없습니다");
        }

        // 이용안내 수정
        guide.setTitle(requestDto.getTitle());
        guide.setContent(requestDto.getContent());
        if (requestDto.getCategory() != null) {
            guide.setCategory(requestDto.getCategory());
        }
        if (requestDto.getPriority() != null) {
            guide.setPriority(requestDto.getPriority());
        }
        guide.setUpdatedAt(LocalDateTime.now());

        SystemGuide updatedGuide = systemGuideRepository.save(guide);
        log.info("시스템 이용안내 수정 완료 - ID: {}", updatedGuide.getId());

        return convertToResponse(updatedGuide);
    }

    /**
     * 시스템 이용안내 삭제 (SUPER_ADMIN만 가능)
     */
    public void deleteSystemGuide(Long guideId, Long userId) {
        SystemGuide guide = systemGuideRepository.findById(guideId)
                .orElseThrow(() -> new IllegalArgumentException("이용안내를 찾을 수 없습니다: " + guideId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 삭제 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 이용안내를 삭제할 권한이 없습니다");
        }

        systemGuideRepository.delete(guide);
        log.info("시스템 이용안내 삭제 완료 - ID: {}", guideId);
    }

    /**
     * 시스템 이용안내 활성화/비활성화 (SUPER_ADMIN만 가능)
     */
    public SystemGuideResponseDto toggleSystemGuideActive(Long guideId, Boolean active, Long userId) {
        SystemGuide guide = systemGuideRepository.findById(guideId)
                .orElseThrow(() -> new IllegalArgumentException("이용안내를 찾을 수 없습니다: " + guideId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 수정 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 이용안내를 수정할 권한이 없습니다");
        }

        guide.setActive(active);
        guide.setUpdatedAt(LocalDateTime.now());
        SystemGuide updatedGuide = systemGuideRepository.save(guide);
        log.info("시스템 이용안내 활성화 상태 변경 - ID: {}, active: {}", updatedGuide.getId(), active);

        return convertToResponse(updatedGuide);
    }

    private SystemGuideResponseDto convertToResponse(SystemGuide guide) {
        return SystemGuideResponseDto.builder()
                .id(guide.getId())
                .title(guide.getTitle())
                .content(guide.getContent())
                .category(guide.getCategory())
                .priority(guide.getPriority())
                .active(guide.getActive())
                .createdByName(guide.getCreatedBy() != null ? guide.getCreatedBy().getName() : null)
                .createdAt(guide.getCreatedAt())
                .updatedAt(guide.getUpdatedAt())
                .build();
    }
}

