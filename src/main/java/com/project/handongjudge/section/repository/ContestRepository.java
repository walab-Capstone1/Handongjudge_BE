package com.project.handongjudge.section.repository;

import com.project.handongjudge.section.entity.Contest;
import com.project.handongjudge.section.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findBySection(Section section);
}
