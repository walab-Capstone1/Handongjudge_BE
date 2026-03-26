package com.project.handongjudge.progress.repository;

import com.project.handongjudge.progress.entity.CodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodeProgressRepository extends JpaRepository<CodeProgress, Long> {
    Optional<CodeProgress> findByUserIdAndProblemIdAndSectionIdAndLanguage(
        Long userId,
        Long problemId,
        Long sectionId,
        String language
    );

    boolean existsByUserIdAndProblemIdAndSectionIdAndLanguage(
        Long userId,
        Long problemId,
        Long sectionId,
        String language
    );
}
