package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.CommentLike;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    // 특정 사용자가 특정 댓글에 추천했는지 확인
    Optional<CommentLike> findByCommentAndUser(Comment comment, User user);

    // 특정 사용자가 특정 댓글에 추천했는지 여부
    boolean existsByCommentAndUser(Comment comment, User user);

    // 댓글별 추천 수
    Long countByComment(Comment comment);

    // 사용자가 추천한 댓글인지 확인
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);
}


