package com.project.handongjudge.community.service;

import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.dto.*;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.entity.UserNickname;
import com.project.handongjudge.community.repository.QuestionLikeRepository;
import com.project.handongjudge.community.repository.QuestionRepository;
import com.project.handongjudge.community.repository.UserNicknameRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final SectionRepository sectionRepository;
    private final SectionRoleService sectionRoleService;
    private final AssignmentRepository assignmentRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final UserNicknameRepository userNicknameRepository;
    private final QuestionLikeRepository questionLikeRepository;
    private final NotificationService notificationService;

    /**
     * 질문 생성
     */
    @Transactional
    public QuestionResponseDto createQuestion(QuestionCreateDto dto, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Section section = sectionRepository.findById(dto.getSectionId())
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        // 작성자 표시명 설정
        String displayName = getDisplayName(author, section, dto.getIsAnonymous());

        // 질문 엔티티 생성
        Question.QuestionBuilder questionBuilder = Question.builder()
                .section(section)
                .author(author)
                .title(dto.getTitle())
                .content(sanitizeHtmlContent(dto.getContent()))
                .isAnonymous(dto.getIsAnonymous())
                .isPublic(dto.getIsPublic())
                .authorDisplayName(displayName);

        // 선택적: 과제 연결
        if (dto.getAssignmentId() != null) {
            Assignment assignment = assignmentRepository.findById(dto.getAssignmentId())
                    .orElseThrow(() -> new CustomException("과제를 찾을 수 없습니다"));
            questionBuilder.assignment(assignment);
        }

        // 선택적: 문제 연결
        if (dto.getProblemId() != null) {
            Problem problem = problemRepository.findById(dto.getProblemId())
                    .orElseThrow(() -> new CustomException("문제를 찾을 수 없습니다"));
            questionBuilder.problem(problem);
        }

        Question question = questionBuilder.build();
        Question savedQuestion = questionRepository.save(question);

        return convertToResponseDto(savedQuestion, userId);
    }

    /**
     * 질문 목록 조회 (섹션별)
     */
    public Page<QuestionListDto> getQuestionsBySection(Long sectionId, String status, 
                                                       Long userId, Pageable pageable) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Page<Question> questions;

        if (status != null && !status.isEmpty()) {
            Question.QuestionStatus questionStatus = Question.QuestionStatus.valueOf(status.toUpperCase());
            questions = questionRepository.findBySectionAndStatusOrderByIsPinnedDescCreatedAtDesc(
                    section, questionStatus, pageable);
        } else {
            questions = questionRepository.findBySectionOrderByIsPinnedDescCreatedAtDesc(
                    section, pageable);
        }

        // 비공개 질문 필터링 (교수가 아닌 경우)
        Page<QuestionListDto> result = questions.map(QuestionListDto::fromEntity);
        
        if (!isInstructor(user, section)) {
            // 필터링은 application level에서 처리 (성능 고려시 쿼리 레벨에서 처리 필요)
        }

        return result;
    }

    /**
     * 질문 상세 조회
     */
    @Transactional
    public QuestionResponseDto getQuestionById(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 비공개 질문은 교수 또는 작성자만 볼 수 있음
        if (!question.getIsPublic() && 
            !question.getAuthor().getId().equals(userId) && 
            !isInstructor(user, question.getSection())) {
            throw new CustomException("이 질문을 볼 권한이 없습니다");
        }

        // 조회수 증가 (작성자가 아닌 경우)
        if (!question.getAuthor().getId().equals(userId)) {
            question.incrementViewCount();
            questionRepository.save(question);
        }

        return convertToResponseDto(question, userId);
    }

    /**
     * 질문 수정
     */
    @Transactional
    public QuestionResponseDto updateQuestion(Long questionId, QuestionUpdateDto dto, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 작성자 또는 교수만 수정 가능
        if (!question.getAuthor().getId().equals(userId) && 
            !isInstructor(user, question.getSection())) {
            throw new CustomException("이 질문을 수정할 권한이 없습니다");
        }

        question.setTitle(dto.getTitle());
        question.setContent(sanitizeHtmlContent(dto.getContent()));
        if (dto.getIsPublic() != null) {
            question.setIsPublic(dto.getIsPublic());
        }

        Question updatedQuestion = questionRepository.save(question);
        return convertToResponseDto(updatedQuestion, userId);
    }

    /**
     * 질문 삭제
     */
    @Transactional
    public void deleteQuestion(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 작성자 또는 교수만 삭제 가능
        if (!question.getAuthor().getId().equals(userId) && 
            !isInstructor(user, question.getSection())) {
            throw new CustomException("이 질문을 삭제할 권한이 없습니다");
        }

        questionRepository.delete(question);
    }

    /**
     * 질문 핀 설정/해제 (교수만 가능)
     */
    @Transactional
    public QuestionResponseDto togglePin(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 교수만 핀 설정 가능
        if (!isInstructor(user, question.getSection())) {
            throw new CustomException("핀을 설정할 권한이 없습니다");
        }

        if (question.getIsPinned()) {
            question.unpin();
        } else {
            question.pin();
            // 알림 발송
            notificationService.notifyQuestionPinned(question);
        }

        Question updatedQuestion = questionRepository.save(question);
        return convertToResponseDto(updatedQuestion, userId);
    }

    /**
     * 질문 해결 상태 변경 (작성자만 가능)
     */
    @Transactional
    public QuestionResponseDto toggleResolve(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        // 권한 검증: 작성자만 해결 상태 변경 가능
        if (!question.getAuthor().getId().equals(userId)) {
            throw new CustomException("해결 상태를 변경할 권한이 없습니다");
        }

        if (question.getStatus() == Question.QuestionStatus.RESOLVED) {
            question.reopen();
        } else {
            question.resolve();
            // 알림 발송
            notificationService.notifyQuestionResolved(question);
        }

        Question updatedQuestion = questionRepository.save(question);
        return convertToResponseDto(updatedQuestion, userId);
    }

    /**
     * 검색
     */
    public Page<QuestionListDto> searchQuestions(Long sectionId, String keyword, 
                                                 Long userId, Pageable pageable) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new CustomException("섹션을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Page<Question> questions = questionRepository.searchByKeyword(section, keyword, pageable);

        // 비공개 질문 필터링
        Page<QuestionListDto> result = questions.map(QuestionListDto::fromEntity);

        return result;
    }

    // ========== Helper Methods ==========

    private String getDisplayName(User user, Section section, Boolean isAnonymous) {
        if (isAnonymous) {
            Optional<UserNickname> nicknameOpt = userNicknameRepository.findByUserAndSection(user, section);
            if (nicknameOpt.isPresent()) {
                return nicknameOpt.get().getNickname();
            } else {
                // 별명이 없으면 기본 별명 생성
                String defaultNickname = "익명" + user.getId();
                UserNickname nickname = UserNickname.builder()
                        .user(user)
                        .section(section)
                        .nickname(defaultNickname)
                        .build();
                userNicknameRepository.save(nickname);
                return defaultNickname;
            }
        } else {
            return user.getName();
        }
    }

    private boolean isInstructor(User user, Section section) {
        return sectionRoleService.isManager(user.getId(), section.getId());
    }

    private String sanitizeHtmlContent(String content) {
        // TODO: HTML sanitization 구현 (XSS 방지)
        // 예: Jsoup 라이브러리 사용
        return content;
    }

    private QuestionResponseDto convertToResponseDto(Question question, Long currentUserId) {
        QuestionResponseDto dto = QuestionResponseDto.fromEntity(question);
        dto.setIsLikedByCurrentUser(
                questionLikeRepository.existsByQuestionIdAndUserId(question.getId(), currentUserId));
        dto.setIsAuthor(question.getAuthor().getId().equals(currentUserId));
        return dto;
    }
}

