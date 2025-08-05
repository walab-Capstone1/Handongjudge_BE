package com.project.handongjudge.problem.repository;

import com.project.handongjudge.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
