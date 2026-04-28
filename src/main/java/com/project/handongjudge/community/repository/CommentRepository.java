package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.Comment;
import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 질문별 댓글 목록 조회 (최신순)
    List<Comment> findByQuestionOrderByCreatedAtAsc(Question question);

    // 질문별 댓글 목록 조회 (페이징)
    Page<Comment> findByQuestionOrderByCreatedAtAsc(Question question, Pageable pageable);

    // 작성자별 댓글 목록 조회
    Page<Comment> findByAuthorOrderByCreatedAtDesc(User author, Pageable pageable);

    // 질문별 댓글 수
    Long countByQuestion(Question question);

    // 교수/TA 답변 조회
    @Query("SELECT c FROM Comment c WHERE c.question = :question AND c.isInstructorAnswer = true ORDER BY c.createdAt ASC")
    List<Comment> findInstructorCommentsByQuestion(@Param("question") Question question);
}


