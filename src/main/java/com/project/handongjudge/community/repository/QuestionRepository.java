package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.Question;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    // 섹션별 질문 목록 조회 (페이징)
    Page<Question> findBySectionOrderByIsPinnedDescCreatedAtDesc(Section section, Pageable pageable);

    // 섹션별 + 상태별 질문 목록 조회
    Page<Question> findBySectionAndStatusOrderByIsPinnedDescCreatedAtDesc(
            Section section, Question.QuestionStatus status, Pageable pageable);

    // 섹션별 + 공개 여부 질문 목록 조회
    Page<Question> findBySectionAndIsPublicOrderByIsPinnedDescCreatedAtDesc(
            Section section, Boolean isPublic, Pageable pageable);

    // 작성자별 질문 목록 조회
    Page<Question> findByAuthorOrderByCreatedAtDesc(User author, Pageable pageable);

    // 과제별 질문 목록 조회
    @Query("SELECT q FROM Question q WHERE q.assignment.id = :assignmentId ORDER BY q.isPinned DESC, q.createdAt DESC")
    Page<Question> findByAssignmentId(@Param("assignmentId") Long assignmentId, Pageable pageable);

    // 문제별 질문 목록 조회
    @Query("SELECT q FROM Question q WHERE q.problem.id = :problemId ORDER BY q.isPinned DESC, q.createdAt DESC")
    Page<Question> findByProblemId(@Param("problemId") Long problemId, Pageable pageable);

    // 섹션별 미해결 질문 수
    @Query("SELECT COUNT(q) FROM Question q WHERE q.section = :section AND q.status = 'PENDING'")
    Long countPendingQuestionsBySection(@Param("section") Section section);

    // 섹션별 총 질문 수
    Long countBySection(Section section);

    // 검색 기능 (제목 + 내용)
    @Query("SELECT q FROM Question q WHERE q.section = :section AND " +
           "(LOWER(q.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(q.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY q.isPinned DESC, q.createdAt DESC")
    Page<Question> searchByKeyword(@Param("section") Section section, 
                                    @Param("keyword") String keyword, 
                                    Pageable pageable);

    // ID와 섹션으로 질문 조회 (권한 검증용)
    Optional<Question> findByIdAndSection(Long id, Section section);

    // 핀된 질문 목록
    List<Question> findBySectionAndIsPinnedTrueOrderByCreatedAtDesc(Section section);
}

