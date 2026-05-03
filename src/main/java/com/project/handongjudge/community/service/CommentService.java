package com.project.handongjudge.community.service;

import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.dto.CommentCreateDto;
import com.project.handongjudge.community.dto.CommentResponseDto;
import com.project.handongjudge.community.dto.CommentUpdateDto;
import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.repository.CommentLikeRepository;
import com.project.handongjudge.community.repository.CommentRepository;
import com.project.handongjudge.community.repository.QuestionRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.service.SectionRoleService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final QuestionRepository questionRepository;
    private final SectionRoleService sectionRoleService;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final NotificationService notificationService;

    /**
     * 질문 내 익명 댓글에 부여할 표시 순번 (작성 시각 오름차순 기준 1부터)
     */
    private static Map<Long, Integer> buildAnonymousDisplayOrder(List<Comment> commentsOrderedAsc) {
        Map<Long, Integer> map = new HashMap<>();
        int n = 0;
        for (Comment c : commentsOrderedAsc) {
            if (Boolean.TRUE.equals(c.getIsAnonymous())) {
                n++;
                map.put(c.getId(), n);
            }
        }
        return map;
    }

    /**
     * 댓글 생성
     */
    @Transactional
    public CommentResponseDto createComment(CommentCreateDto dto, Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        boolean anonymous = Boolean.TRUE.equals(dto.getIsAnonymous());
        String displayName = anonymous ? "익명" : author.getName();

        // 익명이면 교수/TA 배지를 쓰지 않음(익명 의미가 깨짐)
        boolean isInstructorAnswer = !anonymous && isInstructor(author, question.getSection());

        Comment comment = Comment.builder()
                .question(question)
                .author(author)
                .content(sanitizeHtmlContent(dto.getContent()))
                .isAnonymous(anonymous)
                .authorDisplayName(displayName)
                .isInstructorAnswer(isInstructorAnswer)
                .build();

        Comment savedComment = commentRepository.save(comment);

        question.incrementCommentCount();
        questionRepository.save(question);

        if (!question.getAuthor().getId().equals(userId)) {
            try {
                notificationService.notifyQuestionComment(question, savedComment);
            } catch (Exception e) {
                System.err.println("알림 발송 실패: " + e.getMessage());
            }
        }

        List<Comment> all = commentRepository.findByQuestionOrderByCreatedAtAsc(question);
        Map<Long, Integer> anonOrder = buildAnonymousDisplayOrder(all);
        boolean viewerIsManager = isInstructor(author, question.getSection());
        return toResponseDto(savedComment, userId, viewerIsManager, anonOrder.get(savedComment.getId()));
    }

    /**
     * 질문별 댓글 목록 조회
     */
    public List<CommentResponseDto> getCommentsByQuestion(Long questionId, Long userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        User viewer = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        List<Comment> comments = commentRepository.findByQuestionOrderByCreatedAtAsc(question);
        Map<Long, Integer> anonOrder = buildAnonymousDisplayOrder(comments);
        boolean manager = isInstructor(viewer, question.getSection());

        return comments.stream()
                .map(c -> toResponseDto(c, userId, manager, anonOrder.get(c.getId())))
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

        if (!comment.getAuthor().getId().equals(userId) &&
                !isInstructor(user, comment.getQuestion().getSection())) {
            throw new CustomException("이 댓글을 수정할 권한이 없습니다");
        }

        comment.setContent(sanitizeHtmlContent(dto.getContent()));
        Comment updatedComment = commentRepository.save(comment);

        Question question = comment.getQuestion();
        List<Comment> all = commentRepository.findByQuestionOrderByCreatedAtAsc(question);
        Map<Long, Integer> anonOrder = buildAnonymousDisplayOrder(all);
        boolean manager = isInstructor(user, question.getSection());
        return toResponseDto(updatedComment, userId, manager, anonOrder.get(updatedComment.getId()));
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

        if (!comment.getAuthor().getId().equals(userId) &&
                !isInstructor(user, comment.getQuestion().getSection())) {
            throw new CustomException("이 댓글을 삭제할 권한이 없습니다");
        }

        Question question = comment.getQuestion();
        question.decrementCommentCount();
        questionRepository.save(question);

        commentRepository.delete(comment);
    }

    private boolean isInstructor(User user, Section section) {
        return sectionRoleService.isManager(user.getId(), section.getId());
    }

    private String sanitizeHtmlContent(String content) {
        return content;
    }

    private CommentResponseDto toResponseDto(Comment comment, Long currentUserId,
                                               boolean viewerIsManager, Integer anonymousDisplayIndex) {
        CommentResponseDto dto = CommentResponseDto.fromEntity(comment);
        if (Boolean.TRUE.equals(comment.getIsAnonymous())) {
            dto.setIsInstructorAnswer(false);
        }
        if (Boolean.TRUE.equals(comment.getIsAnonymous()) && anonymousDisplayIndex != null) {
            dto.setAuthorDisplayName("익명 " + anonymousDisplayIndex);
        }
        if (Boolean.TRUE.equals(comment.getIsAnonymous()) && !viewerIsManager) {
            dto.setAuthorId(null);
        } else {
            dto.setAuthorId(comment.getAuthor().getId());
        }
        if (viewerIsManager) {
            dto.setAuthorRealNameForStaff(comment.getAuthor().getName());
        }
        dto.setIsAccepted(false);
        dto.setIsLikedByCurrentUser(
                commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), currentUserId));
        dto.setIsAuthor(comment.getAuthor().getId().equals(currentUserId));
        return dto;
    }
}
