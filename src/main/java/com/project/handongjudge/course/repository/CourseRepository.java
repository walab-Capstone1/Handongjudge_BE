package com.project.handongjudge.course.repository;

import com.project.handongjudge.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
}
