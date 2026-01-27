package com.project.handongjudge.section.repository;

import com.project.handongjudge.section.entity.SectionUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionUserRoleRepository extends JpaRepository<SectionUserRole, Long> {

    /**
     * 특정 수업에서 특정 사용자의 역할 조회
     */
    Optional<SectionUserRole> findBySectionIdAndUserId(Long sectionId, Long userId);

    /**
     * 특정 수업의 모든 역할 조회
     */
    List<SectionUserRole> findBySectionId(Long sectionId);

    /**
     * 특정 사용자의 모든 수업별 역할 조회
     */
    List<SectionUserRole> findByUserId(Long userId);

    /**
     * 특정 수업에서 특정 역할을 가진 사용자들 조회
     */
    List<SectionUserRole> findBySectionIdAndRole(Long sectionId, SectionUserRole.SectionRole role);

    /**
     * 특정 사용자가 특정 역할을 가진 수업들 조회
     */
    List<SectionUserRole> findByUserIdAndRole(Long userId, SectionUserRole.SectionRole role);

    /**
     * 특정 수업에서 역할이 존재하는지 확인
     */
    boolean existsBySectionIdAndUserId(Long sectionId, Long userId);

    /**
     * 특정 수업에서 특정 역할이 존재하는지 확인
     */
    boolean existsBySectionIdAndUserIdAndRole(Long sectionId, Long userId, SectionUserRole.SectionRole role);

    /**
     * 특정 수업의 ADMIN 역할을 가진 사용자 조회 (수업 생성자)
     */
    @Query("SELECT sur FROM SectionUserRole sur WHERE sur.section.id = :sectionId AND sur.role = 'ADMIN'")
    List<SectionUserRole> findAdminsBySectionId(@Param("sectionId") Long sectionId);

    /**
     * 특정 사용자가 ADMIN 또는 TUTOR 역할을 가진 수업들 조회 (관리 중인 수업)
     */
    @Query("SELECT sur FROM SectionUserRole sur WHERE sur.user.id = :userId AND sur.role IN ('ADMIN', 'TUTOR')")
    List<SectionUserRole> findManagingSectionsByUserId(@Param("userId") Long userId);

    /**
     * 특정 사용자가 STUDENT 역할을 가진 수업들 조회 (수강 중인 수업)
     */
    @Query("SELECT sur FROM SectionUserRole sur WHERE sur.user.id = :userId AND sur.role = 'STUDENT'")
    List<SectionUserRole> findEnrolledSectionsByUserId(@Param("userId") Long userId);

    /**
     * 특정 수업에서 특정 사용자의 역할 삭제
     */
    @Modifying
    @Query("DELETE FROM SectionUserRole sur WHERE sur.section.id = :sectionId AND sur.user.id = :userId")
    void deleteBySectionIdAndUserId(@Param("sectionId") Long sectionId, @Param("userId") Long userId);
}

