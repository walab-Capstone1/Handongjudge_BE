package com.project.handongjudge.notice.repository;

import com.project.handongjudge.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 특정 분반의 공지사항 목록 조회 (최신순)
    @Query("SELECT n FROM Notice n WHERE n.section.id = :sectionId ORDER BY n.createdAt DESC")
    List<Notice> findBySectionIdOrderByCreatedAtDesc(@Param("sectionId") Long sectionId);

    // 교수가 담당하는 모든 분반의 공지사항 조회
    @Query("SELECT n FROM Notice n WHERE n.section.instructor.id = :instructorId ORDER BY n.createdAt DESC")
    List<Notice> findByInstructorIdOrderByCreatedAtDesc(@Param("instructorId") Long instructorId);

    // 특정 분반의 새로운 공지사항 개수
    @Query("SELECT COUNT(n) FROM Notice n WHERE n.section.id = :sectionId AND n.isNew = true")
    Long countNewNoticesBySectionId(@Param("sectionId") Long sectionId);
}