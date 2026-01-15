package com.project.handongjudge.notice.service;

import com.project.handongjudge.notice.dto.SystemNoticeRequestDto;
import com.project.handongjudge.notice.dto.SystemNoticeResponseDto;
import com.project.handongjudge.notice.entity.SystemNotice;
import com.project.handongjudge.notice.repository.SystemNoticeRepository;
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
public class SystemNoticeService {

    private final SystemNoticeRepository systemNoticeRepository;
    private final UserRepository userRepository;

    /**
     * 시스템 전체 공지사항 생성 (SUPER_ADMIN만 가능)
     */
    public SystemNoticeResponseDto createSystemNotice(SystemNoticeRequestDto requestDto, Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 생성 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 전체 공지사항을 작성할 권한이 없습니다");
        }

        // 공지사항 생성
        SystemNotice notice = SystemNotice.builder()
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .priority(requestDto.getPriority() != null ? requestDto.getPriority() : 0)
                .isNew(true)
                .active(true)
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        SystemNotice savedNotice = systemNoticeRepository.save(notice);
        log.info("시스템 전체 공지사항 생성 완료 - ID: {}, 제목: {}", savedNotice.getId(), savedNotice.getTitle());

        return convertToResponse(savedNotice);
    }

    /**
     * 시스템 전체 공지사항 목록 조회 (활성화된 것만)
     */
    @Transactional(readOnly = true)
    public List<SystemNoticeResponseDto> getActiveSystemNotices() {
        List<SystemNotice> notices = systemNoticeRepository.findByActiveTrueOrderByPriorityDescCreatedAtDesc();
        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 시스템 전체 공지사항 목록 조회 (모든 공지사항, 관리자용)
     */
    @Transactional(readOnly = true)
    public List<SystemNoticeResponseDto> getAllSystemNotices(Long userId) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 조회 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 전체 공지사항 목록을 조회할 권한이 없습니다");
        }

        List<SystemNotice> notices = systemNoticeRepository.findAllByOrderByPriorityDescCreatedAtDesc();
        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 시스템 전체 공지사항 수정 (SUPER_ADMIN만 가능)
     */
    public SystemNoticeResponseDto updateSystemNotice(Long noticeId, SystemNoticeRequestDto requestDto, Long userId) {
        SystemNotice notice = systemNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 수정 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 전체 공지사항을 수정할 권한이 없습니다");
        }

        // 공지사항 수정
        notice.setTitle(requestDto.getTitle());
        notice.setContent(requestDto.getContent());
        if (requestDto.getPriority() != null) {
            notice.setPriority(requestDto.getPriority());
        }
        notice.setUpdatedAt(LocalDateTime.now());

        SystemNotice updatedNotice = systemNoticeRepository.save(notice);
        log.info("시스템 전체 공지사항 수정 완료 - ID: {}", updatedNotice.getId());

        return convertToResponse(updatedNotice);
    }

    /**
     * 시스템 전체 공지사항 삭제 (SUPER_ADMIN만 가능)
     */
    public void deleteSystemNotice(Long noticeId, Long userId) {
        SystemNotice notice = systemNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 삭제 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 전체 공지사항을 삭제할 권한이 없습니다");
        }

        systemNoticeRepository.delete(notice);
        log.info("시스템 전체 공지사항 삭제 완료 - ID: {}", noticeId);
    }

    /**
     * 시스템 전체 공지사항 활성화/비활성화 (SUPER_ADMIN만 가능)
     */
    public SystemNoticeResponseDto toggleSystemNoticeActive(Long noticeId, Boolean active, Long userId) {
        SystemNotice notice = systemNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 권한 확인: 시스템 관리자만 수정 가능
        if (user.getRole() != User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("시스템 전체 공지사항을 수정할 권한이 없습니다");
        }

        notice.setActive(active);
        notice.setUpdatedAt(LocalDateTime.now());
        SystemNotice updatedNotice = systemNoticeRepository.save(notice);
        log.info("시스템 전체 공지사항 활성화 상태 변경 - ID: {}, active: {}", updatedNotice.getId(), active);

        return convertToResponse(updatedNotice);
    }

    private SystemNoticeResponseDto convertToResponse(SystemNotice notice) {
        return SystemNoticeResponseDto.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .priority(notice.getPriority())
                .isNew(notice.isNew())
                .active(notice.getActive())
                .createdByName(notice.getCreatedBy() != null ? notice.getCreatedBy().getName() : null)
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}

