package com.project.handongjudge.notice.service;

import com.project.handongjudge.notice.dto.NoticeRequestDto;
import com.project.handongjudge.notice.dto.NoticeResponseDto;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final SectionRepository sectionRepository;

    // 공지사항 생성
    @Transactional
    public NoticeResponseDto createNotice(NoticeRequestDto request, Long instructorId) {
        // 분반 조회 및 권한 확인
        Section section = sectionRepository.findById(request.getSectionId())
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + request.getSectionId()));

        // 해당 분반의 담당 교수인지 확인
        if (!section.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 분반의 공지사항을 작성할 권한이 없습니다");
        }

        Notice notice = Notice.builder()
                .section(section)
                .title(request.getTitle())
                .content(request.getContent())
                .difficulty(request.getDifficulty())
                .isNew(request.isNew())
                .createdAt(LocalDateTime.now())
                .build();

        Notice savedNotice = noticeRepository.save(notice);

        return convertToResponse(savedNotice);
    }

    // 교수의 모든 분반 공지사항 조회
    public List<NoticeResponseDto> getInstructorNotices(Long instructorId) {
        List<Notice> notices = noticeRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 특정 분반의 공지사항 조회
    public List<NoticeResponseDto> getSectionNotices(Long sectionId, Long instructorId) {
        // 분반 조회 및 권한 확인
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + sectionId));

        if (!section.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 분반의 공지사항을 조회할 권한이 없습니다");
        }

        List<Notice> notices = noticeRepository.findBySectionIdOrderByCreatedAtDesc(sectionId);
        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 공지사항 수정
    @Transactional
    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto request, Long instructorId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 권한 확인
        if (!notice.getSection().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 공지사항을 수정할 권한이 없습니다");
        }

        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setDifficulty(request.getDifficulty());
        notice.setNew(request.isNew());

        Notice updatedNotice = noticeRepository.save(notice);
        return convertToResponse(updatedNotice);
    }

    // 공지사항 삭제
    @Transactional
    public void deleteNotice(Long noticeId, Long instructorId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 권한 확인
        if (!notice.getSection().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 공지사항을 삭제할 권한이 없습니다");
        }

        noticeRepository.delete(notice);
    }

    // 공지사항 읽음 처리 (isNew = false)
    @Transactional
    public void markNoticeAsRead(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        notice.setNew(false);
        noticeRepository.save(notice);
    }

    // Entity -> Response DTO 변환
    private NoticeResponseDto convertToResponse(Notice notice) {
        return NoticeResponseDto.builder()
                .id(notice.getId())
                .sectionId(notice.getSection().getId())
                .sectionName(notice.getSection().getCourse().getTitle() + " - " + notice.getSection().getSectionNumber() + "분반")
                .courseTitle(notice.getSection().getCourse().getTitle())
                .title(notice.getTitle())
                .content(notice.getContent())
                .difficulty(notice.getDifficulty())
                .isNew(notice.isNew())
                .createdAt(notice.getCreatedAt())
                .build();
    }
}