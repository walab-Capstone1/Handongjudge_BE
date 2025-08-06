package com.project.handongjudge.section.service;

import com.project.handongjudge.course.entity.Course;
import com.project.handongjudge.course.repository.CourseRepository;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.problem.service.DomjudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;

    @Transactional
    public SectionResponse createSection(SectionRequest request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User instructor = userRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Section section = Section.builder()
                .course(course)
                .instructor(instructor)
                .sectionNumber(request.getSectionNumber())
                .build();

        Section saved = sectionRepository.save(section);

        domjudgeService.createContest(saved.getId(), request.getSectionNumber());

        return SectionResponse.builder()
                .id(saved.getId())
                .courseId(course.getId())
                .instructorId(instructor.getId())
                .sectionNumber(saved.getSectionNumber())
                .build();
    }
}
