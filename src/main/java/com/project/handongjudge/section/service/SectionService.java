package com.project.handongjudge.section.service;

import com.project.handongjudge.course.entity.Course;
import com.project.handongjudge.course.repository.CourseRepository;
import com.project.handongjudge.section.dto.SectionInfoDto;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;

import com.project.handongjudge.section.dto.SectionWithCourseRequest;
import com.project.handongjudge.section.entity.Section;

import com.project.handongjudge.section.repository.SectionRepository;

import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service

@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;

    private String generateEnrollmentCode() {
        // UUID 기반 고유 코드 생성
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    @Transactional
    public SectionResponse createSection(SectionRequest request) {
        String enrollmentCode = generateEnrollmentCode();

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        User instructor = userRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Section section = Section.builder()
                .course(course)
                .instructor(instructor)
                .sectionNumber(request.getSectionNumber())
                .enrollmentCode(enrollmentCode)
                .year(request.getYear())
                .semester(request.getSemester())
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
    // SectionService.java에 추가
    // SectionService의 getSectionInfo 메서드 수정

    public SectionInfoDto getSectionInfo(Long sectionId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + sectionId));

        return SectionInfoDto.builder()
                .sectionId(section.getId())
                .sectionNumber(section.getSectionNumber())
                .courseTitle(section.getCourse().getTitle())
                .instructorName(section.getInstructor().getName())
                .enrollmentCode(section.getEnrollmentCode())  // 추가
                .build();
    }

    // SectionService.java에 추가
    @Transactional
    public SectionResponse createSectionWithCourse(SectionWithCourseRequest request) {
        String enrollmentCode = generateEnrollmentCode();

        // 먼저 Course 생성 또는 조회
        Course course;
        Optional<Course> existingCourse = courseRepository.findByTitle(request.getCourseTitle());

        if (existingCourse.isPresent()) {
            course = existingCourse.get();
        } else {
            course = Course.builder()
                    .title(request.getCourseTitle())
                    .build();
            course = courseRepository.save(course);
        }

        User instructor = userRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Section section = Section.builder()
                .course(course)
                .instructor(instructor)
                .sectionNumber(request.getSectionNumber())
                .enrollmentCode(enrollmentCode)
                .year(request.getYear())
                .semester(request.getSemester())
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
