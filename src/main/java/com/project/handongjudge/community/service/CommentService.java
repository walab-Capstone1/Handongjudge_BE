package com.project.handongjudge.community.service;

import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.dto.CommentCreateDto;
import com.project.handongjudge.community.dto.CommentResponseDto;
import com.project.handongjudge.community.dto.CommentUpdateDto;
import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.entity.UserNickname;
import com.project.handongjudge.community.repository.CommentLikeRepository;
import com.project.handongjudge.community.repository.CommentRepository;
import com.project.handongjudge.community.repository.QuestionRepository;
import com.project.handongjudge.community.repository.UserNicknameRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final QuestionRepository questionRepository;
    private final SectionRoleService sectionRoleService;
    private final UserRepository userRepository;
    private final UserNicknameRepository userNicknameRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService;

    /**
     * 댓글 생성
     */
    @Transactional
    public CommentResponseDto createComment(CommentCreateDto dto, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        // 작성자 표시명 설정
        String displayName = getDisplayName(author, question.getSection(), dto.getIsAnonymous());

        // 교수/TA 답변 여부 확인
        boolean isInstructorAnswer = isInstructor(author, question.getSection());

        // 댓글 엔티티 생성
        Comment comment = Comment.builder()
                .question(question)
                .author(author)
                .content(sanitizeHtmlContent(dto.getContent()))
                .isAnonymous(dto.getIsAnonymous())
                .authorDisplayName(displayName)
                .isInstructorAnswer(isInstructorAnswer)
                .build();

        Comment savedComment = commentRepository.save(comment);

        // 질문의 댓글 수 증가
        question.incrementCommentCount();
        questionRepository.save(question);

        // 알림 발송 (질문 작성자에게, 본인이 아닌 경우)
        if (!question.getAuthor().getId().equals(userId)) {
            try {
                notificationService.notifyQuestionComment(question, savedComment);
            } catch (Exception e) {
                // 알림 발송 실패해도 댓글 작성은 성공 처리
                System.err.println("알림 발송 실패: " + e.getMessage());
            }
        }

        return convertToResponseDto(savedComment, userId);
    }

    /**
     * 질문별 댓글 목록 조회
     */
    public List<CommentResponseDto> getCommentsByQuestion(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        List<Comment> comments = commentRepository.findByQuestionOrderByCreatedAtAsc(question);

        return comments.stream()
                .map(comment -> convertToResponseDto(comment, userId))
                .collect(Collectors.toList());
    }

    /**
     * 댓글 수정
     */
    @Transactional
    public CommentResponseDto updateComment(Long commentId, CommentUpdateDto dto, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("댓글을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 작성자 또는 교수만 수정 가능
        if (!comment.getAuthor().getId().equals(userId) && 
            !isInstructor(user, comment.getQuestion().getSection())) {
            throw new CustomException("이 댓글을 수정할 권한이 없습니다");
        }

        comment.setContent(sanitizeHtmlContent(dto.getContent()));
        Comment updatedComment = commentRepository.save(comment);

        return convertToResponseDto(updatedComment, userId);
    }

    /**
     * 댓글 삭제
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("댓글을 찾을 수 없습니다"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        // 권한 검증: 작성자 또는 교수만 삭제 가능
        if (!comment.getAuthor().getId().equals(userId) && 
            !isInstructor(user, comment.getQuestion().getSection())) {
            throw new CustomException("이 댓글을 삭제할 권한이 없습니다");
        }

        // 질문의 댓글 수 감소
        Question question = comment.getQuestion();
        question.decrementCommentCount();
        questionRepository.save(question);

        commentRepository.delete(comment);
    }

    /**
     * 댓글 채택 (질문 작성자만 가능)
     */
    @Transactional
    public CommentResponseDto acceptComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("댓글을 찾을 수 없습니다"));

        Question question = comment.getQuestion();

        // 권한 검증: 질문 작성자만 채택 가능
        if (!question.getAuthor().getId().equals(userId)) {
            throw new CustomException("댓글을 채택할 권한이 없습니다");
        }

        // 기존 채택 댓글이 있다면 채택 해제
        List<Comment> acceptedComments = commentRepository.findAcceptedCommentsByQuestion(question);
        for (Comment acceptedComment : acceptedComments) {
            acceptedComment.unaccept();
            commentRepository.save(acceptedComment);
        }

        // 새로운 댓글 채택
        comment.accept();
        Comment updatedComment = commentRepository.save(comment);

        // 질문 해결 상태로 변경
        question.resolve();
        questionRepository.save(question);

        // 알림 발송 (댓글 작성자에게)
        if (!comment.getAuthor().getId().equals(userId)) {
            notificationService.notifyCommentAccepted(comment);
        }

        return convertToResponseDto(updatedComment, userId);
    }

    /**
     * 댓글 채택 해제
     */
    @Transactional
    public CommentResponseDto unacceptComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("댓글을 찾을 수 없습니다"));

        Question question = comment.getQuestion();

        // 권한 검증: 질문 작성자만 채택 해제 가능
        if (!question.getAuthor().getId().equals(userId)) {
            throw new CustomException("댓글 채택을 해제할 권한이 없습니다");
        }

        comment.unaccept();
        Comment updatedComment = commentRepository.save(comment);

        // 질문을 미해결 상태로 변경
        question.reopen();
        questionRepository.save(question);

        return convertToResponseDto(updatedComment, userId);
    }

    // ========== Helper Methods ==========

    private String getDisplayName(User user, Section section, Boolean isAnonymous) {
        if (isAnonymous) {
            Optional<UserNickname> nicknameOpt = userNicknameRepository.findByUserAndSection(user, section);
            if (nicknameOpt.isPresent()) {
                return nicknameOpt.get().getNickname();
            } else {
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
        // TODO: HTML sanitization 구현
        return content;
    }

    private CommentResponseDto convertToResponseDto(Comment comment, Long currentUserId) {
        CommentResponseDto dto = CommentResponseDto.fromEntity(comment);
        dto.setIsLikedByCurrentUser(
                commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), currentUserId));
        dto.setIsAuthor(comment.getAuthor().getId().equals(currentUserId));
        return dto;
    }
}

