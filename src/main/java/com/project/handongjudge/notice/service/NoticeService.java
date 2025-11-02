package com.project.handongjudge.notice.service;

import com.project.handongjudge.notice.dto.NoticeRequestDto;
import com.project.handongjudge.notice.dto.NoticeResponseDto;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.repository.EnrollmentRepository;
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
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;

    public NoticeResponseDto createNotice(NoticeRequestDto requestDto, Long instructorId) {
        // 분반 조회
        Section section = sectionRepository.findById(requestDto.getSectionId())
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + requestDto.getSectionId()));

        // 권한 확인: 해당 분반의 교수인지 확인
        if (!section.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 분반의 공지사항을 작성할 권한이 없습니다");
        }

        // 공지사항 생성
        Notice notice = Notice.builder()
                .section(section)
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .difficulty(requestDto.getDifficulty())
                .isNew(true)
                .active(true)  // 추가
                .createdAt(LocalDateTime.now())
                .build();

        Notice savedNotice = noticeRepository.save(notice);
        log.info("공지사항 생성 완료 - ID: {}, 제목: {}", savedNotice.getId(), savedNotice.getTitle());

        return convertToResponse(savedNotice);
    }

    public List<NoticeResponseDto> getSectionNotices(Long sectionId, Long userId) {
        // 분반 조회
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + sectionId));

        // 권한 확인: 교수이거나 해당 분반을 수강하는 학생이어야 함
        boolean isInstructor = section.getInstructor().getId().equals(userId);

        List<Notice> notices;
        if (isInstructor) {
            // 교수는 모든 공지사항 조회 (active 여부와 관계없이)
            notices = noticeRepository.findBySectionIdOrderByCreatedAtDesc(sectionId);
        } else {
            // 학생은 active=true인 공지사항만 조회
            notices = noticeRepository.findActiveNoticesBySectionId(sectionId);
        }

        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto requestDto, Long instructorId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 권한 확인: 해당 분반의 교수인지 확인
        if (!notice.getSection().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 공지사항을 수정할 권한이 없습니다");
        }

        // 공지사항 수정
        notice.setTitle(requestDto.getTitle());
        notice.setContent(requestDto.getContent());
        notice.setDifficulty(requestDto.getDifficulty());

        Notice updatedNotice = noticeRepository.save(notice);
        log.info("공지사항 수정 완료 - ID: {}", updatedNotice.getId());

        return convertToResponse(updatedNotice);
    }

    public void deleteNotice(Long noticeId, Long instructorId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 권한 확인: 해당 분반의 교수인지 확인
        if (!notice.getSection().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 공지사항을 삭제할 권한이 없습니다");
        }

        noticeRepository.delete(notice);
        log.info("공지사항 삭제 완료 - ID: {}", noticeId);
    }

    // 교수가 담당하는 모든 분반의 공지사항 조회
    public List<NoticeResponseDto> getInstructorNotices(Long instructorId) {
        log.info("🔥 교수 공지사항 조회 시작 - instructorId: {}", instructorId);
        
        // 교수가 담당하는 모든 분반의 공지사항 조회
        List<Notice> notices = noticeRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
        log.info("🔥 교수 공지사항 조회 결과 - 공지사항 수: {}", notices.size());
        
        return notices.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private NoticeResponseDto convertToResponse(Notice notice) {
        log.info("🔥 공지사항 변환 - ID: {}, isNew: {}", notice.getId(), notice.isNew());
        
        // 분반 정보 생성 (예: "컴퓨터과학개론 - 16분반")
        Section section = notice.getSection();
        String sectionName = section.getCourse().getTitle() + " - " + section.getSectionNumber() + "분반";
        
        return NoticeResponseDto.builder()
                .id(notice.getId())
                .sectionId(notice.getSection().getId())
                .sectionName(sectionName)
                .title(notice.getTitle())
                .content(notice.getContent())
                .difficulty(notice.getDifficulty())
                .isNew(notice.isNew())
                .active(notice.getActive())  // 추가
                .createdAt(notice.getCreatedAt())
                .build();
    }
    public NoticeResponseDto toggleNoticeActive(Long noticeId, Boolean active, Long instructorId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지사항을 찾을 수 없습니다: " + noticeId));

        // 권한 확인
        if (!notice.getSection().getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("해당 공지사항을 수정할 권한이 없습니다");
        }

        notice.setActive(active);
        Notice updatedNotice = noticeRepository.save(notice);
        log.info("공지사항 활성화 상태 변경 - ID: {}, active: {}", updatedNotice.getId(), active);

        return convertToResponse(updatedNotice);
    }
}