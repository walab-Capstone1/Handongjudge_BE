package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.community.entity.QuestionLike;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionLikeRepository extends JpaRepository<QuestionLike, Long> {

    // 특정 사용자가 특정 질문에 추천했는지 확인
    Optional<QuestionLike> findByQuestionAndUser(Question question, User user);

    // 특정 사용자가 특정 질문에 추천했는지 여부
    boolean existsByQuestionAndUser(Question question, User user);

    // 질문별 추천 수
    Long countByQuestion(Question question);

    // 사용자가 추천한 질문인지 확인
    boolean existsByQuestionIdAndUserId(Long questionId, Long userId);
}

