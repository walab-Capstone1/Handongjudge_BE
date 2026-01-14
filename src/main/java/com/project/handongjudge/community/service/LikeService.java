package com.project.handongjudge.community.service;

import com.project.handongjudge.common.exception.CustomException;
import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.CommentLike;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.entity.QuestionLike;
import com.project.handongjudge.community.repository.CommentLikeRepository;
import com.project.handongjudge.community.repository.CommentRepository;
import com.project.handongjudge.community.repository.QuestionLikeRepository;
import com.project.handongjudge.community.repository.QuestionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final QuestionLikeRepository questionLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final QuestionRepository questionRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * 질문 추천/취소 (토글)
     */
    @Transactional
    public boolean toggleQuestionLike(Long questionId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new CustomException("질문을 찾을 수 없습니다"));

        Optional<QuestionLike> existingLike = questionLikeRepository.findByQuestionAndUser(question, user);

        if (existingLike.isPresent()) {
            // 추천 취소
            questionLikeRepository.delete(existingLike.get());
            question.decrementLikeCount();
            questionRepository.save(question);
            return false;
        } else {
            // 추천
            QuestionLike like = QuestionLike.builder()
                    .question(question)
                    .user(user)
                    .build();
            questionLikeRepository.save(like);
            question.incrementLikeCount();
            questionRepository.save(question);

            // 알림 발송 (질문 작성자에게, 본인이 아닌 경우)
            if (!question.getAuthor().getId().equals(userId)) {
                try {
                    notificationService.notifyQuestionLiked(question, user);
                } catch (Exception e) {
                    // 알림 발송 실패해도 추천은 성공 처리
                    System.err.println("질문 추천 알림 발송 실패: " + e.getMessage());
                }
            }

            return true;
        }
    }

    /**
     * 댓글 추천/취소 (토글)
     */
    @Transactional
    public boolean toggleCommentLike(Long commentId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException("댓글을 찾을 수 없습니다"));

        Optional<CommentLike> existingLike = commentLikeRepository.findByCommentAndUser(comment, user);

        if (existingLike.isPresent()) {
            // 추천 취소
            commentLikeRepository.delete(existingLike.get());
            comment.decrementLikeCount();
            commentRepository.save(comment);
            return false;
        } else {
            // 추천
            CommentLike like = CommentLike.builder()
                    .comment(comment)
                    .user(user)
                    .build();
            commentLikeRepository.save(like);
            comment.incrementLikeCount();
            commentRepository.save(comment);

            // 알림 발송 (댓글 작성자에게, 본인이 아닌 경우)
            if (!comment.getAuthor().getId().equals(userId)) {
                try {
                    notificationService.notifyCommentLiked(comment, user);
                } catch (Exception e) {
                    // 알림 발송 실패해도 추천은 성공 처리
                    System.err.println("댓글 추천 알림 발송 실패: " + e.getMessage());
                }
            }

            return true;
        }
    }

    /**
     * 질문 추천 여부 확인
     */
    public boolean isQuestionLikedByUser(Long questionId, Long userId) {
        return questionLikeRepository.existsByQuestionIdAndUserId(questionId, userId);
    }

    /**
     * 댓글 추천 여부 확인
     */
    public boolean isCommentLikedByUser(Long commentId, Long userId) {
        return commentLikeRepository.existsByCommentIdAndUserId(commentId, userId);
    }
}

