package com.project.handongjudge.course.service;

import com.project.handongjudge.course.dto.CourseRequest;
import com.project.handongjudge.course.dto.CourseResponse;
import com.project.handongjudge.course.entity.Course;
import com.project.handongjudge.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(course -> CourseResponse.builder()
                        .id(course.getId())
                        .title(course.getTitle())
                        .description(course.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public CourseResponse createCourse(CourseRequest request) {
        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();

        Course saved = courseRepository.save(course);

        return CourseResponse.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .build();
    }
}