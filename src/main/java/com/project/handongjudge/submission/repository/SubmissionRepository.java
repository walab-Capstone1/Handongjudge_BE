package com.project.handongjudge.submission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.project.handongjudge.submission.entity.Submission;
import java.util.List;
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserId(Long userId);
    
    }
