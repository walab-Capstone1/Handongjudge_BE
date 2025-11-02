package com.project.handongjudge.notice.repository;

import com.project.handongjudge.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    
    @Query("SELECT n FROM Notice n WHERE n.section.id = :sectionId ORDER BY n.createdAt DESC")
    List<Notice> findBySectionIdOrderByCreatedAtDesc(@Param("sectionId") Long sectionId);
    
    @Query("SELECT n FROM Notice n WHERE n.section.id = :sectionId AND n.isNew = true")
    List<Notice> findNewNoticesBySectionId(@Param("sectionId") Long sectionId);
    
    // 교수가 담당하는 모든 분반의 공지사항 조회
    @Query("SELECT n FROM Notice n WHERE n.section.instructor.id = :instructorId ORDER BY n.createdAt DESC")
    List<Notice> findByInstructorIdOrderByCreatedAtDesc(@Param("instructorId") Long instructorId);

    @Query("SELECT n FROM Notice n WHERE n.section.id = :sectionId AND n.active = true ORDER BY n.createdAt DESC")
    List<Notice> findActiveNoticesBySectionId(@Param("sectionId") Long sectionId);
}
