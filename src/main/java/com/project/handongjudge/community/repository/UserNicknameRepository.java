package com.project.handongjudge.community.repository;

import com.project.handongjudge.community.entity.UserNickname;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNicknameRepository extends JpaRepository<UserNickname, Long> {

    // 특정 섹션에서 사용자의 별명 조회
    Optional<UserNickname> findByUserAndSection(User user, Section section);

    // 특정 섹션에서 별명 중복 확인
    boolean existsBySectionAndNickname(Section section, String nickname);

    // 특정 섹션에서 별명으로 사용자 조회
    Optional<UserNickname> findBySectionAndNickname(Section section, String nickname);

    // 사용자의 특정 섹션 별명 존재 여부
    boolean existsByUserAndSection(User user, Section section);

    // 섹션 내 사용 가능한 별명인지 확인 (현재 사용자 제외)
    @Query("SELECT CASE WHEN COUNT(un) > 0 THEN true ELSE false END " +
           "FROM UserNickname un " +
           "WHERE un.section = :section AND un.nickname = :nickname AND un.user.id != :userId")
    boolean existsBySectionAndNicknameExcludingUser(
            @Param("section") Section section, 
            @Param("nickname") String nickname, 
            @Param("userId") Long userId);
}

