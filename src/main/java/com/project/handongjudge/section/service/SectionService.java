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
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.entity.AssignmentProblem;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.repository.AssignmentProblemRepository;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.problem.service.ProblemService;
import com.project.handongjudge.notice.entity.Notice;
import com.project.handongjudge.notice.repository.NoticeRepository;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Slf4j
@Service

@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DomjudgeService domjudgeService;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentProblemRepository assignmentProblemRepository;
    private final ProblemRepository problemRepository;
    private final ProblemService problemService;
    private final NoticeRepository noticeRepository;  // ✨ 공지사항 복사용

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

        // courseTitle을 전달
        domjudgeService.createContest(saved.getId(), request.getSectionNumber(), course.getTitle());

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
                .active(section.getActive())  // 추가
                .build();
    }

    @Transactional
    public Section toggleSectionActive(Long sectionId, Boolean active) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("분반을 찾을 수 없습니다: " + sectionId));

        section.setActive(active);
        return sectionRepository.save(section);
    }
    /**
     * Section 복사 (권한 체크 포함)
     * 원본 Section을 만든 instructor만 복사 가능
     * Assignment, Problem, Notice 모두 복사
     */
    @Transactional
    public Long copySection(Long sourceSectionId, Integer newSectionNumber,
                            Integer newYear, String newSemester, Long instructorId) throws IOException {
        Section sourceSection = sectionRepository.findById(sourceSectionId)
                .orElseThrow(() -> new IllegalArgumentException("원본 Section을 찾을 수 없습니다: " + sourceSectionId));

        // ✅ 권한 체크
        if (!sourceSection.getInstructor().getId().equals(instructorId)) {
            throw new IllegalArgumentException("이 수업을 복사할 권한이 없습니다.");
        }

        // 새 Section 생성
        String enrollmentCode = generateEnrollmentCode();
        Section newSection = Section.builder()
                .course(sourceSection.getCourse())
                .instructor(sourceSection.getInstructor())
                .sectionNumber(newSectionNumber)
                .enrollmentCode(enrollmentCode)
                .year(newYear)
                .semester(newSemester)
                .active(true)
                .build();

        Section savedSection = sectionRepository.save(newSection);

        // ✅ DOMJudge Contest 생성
        domjudgeService.createContest(
                savedSection.getId(),
                newSectionNumber,
                sourceSection.getCourse().getTitle()
        );

        // ✨ 1. 공지사항 복사
        List<Notice> sourceNotices = noticeRepository.findBySectionIdOrderByCreatedAtDesc(sourceSectionId);
        List<Notice> newNotices = new ArrayList<>();

        for (Notice sourceNotice : sourceNotices) {
            Notice newNotice = Notice.builder()
                    .section(savedSection)
                    .title(sourceNotice.getTitle())
                    .content(sourceNotice.getContent())
                    .difficulty(sourceNotice.getDifficulty())
                    .isNew(true)  // 새 공지로 표시
                    .createdAt(LocalDateTime.now())  // 현재 시간으로 설정
                    .active(sourceNotice.getActive())
                    .build();

            newNotices.add(newNotice);
        }
        noticeRepository.saveAll(newNotices);
        log.info("공지사항 {}개 복사 완료", newNotices.size());

        // ✨ 2. 원본 Section의 모든 Assignment 복사
        List<Assignment> sourceAssignments = assignmentRepository.findBySectionId(sourceSectionId);
        for (Assignment sourceAssignment : sourceAssignments) {
            Assignment newAssignment = Assignment.builder()
                    .section(savedSection)
                    .assignmentNumber(sourceAssignment.getAssignmentNumber())
                    .title(sourceAssignment.getTitle())
                    .description(sourceAssignment.getDescription())
                    .startDate(sourceAssignment.getStartDate())
                    .endDate(sourceAssignment.getEndDate())
                    .active(sourceAssignment.getActive())
                    .build();

            Assignment savedAssignment = assignmentRepository.save(newAssignment);

            // ✨ 3. 원본 Assignment의 모든 Problem 복사
            List<AssignmentProblem> sourceAssignmentProblems = assignmentProblemRepository
                    .findByAssignmentId(sourceAssignment.getId());

            List<AssignmentProblem> newAssignmentProblems = new ArrayList<>();
            int order = 1;

            for (AssignmentProblem sourceAp : sourceAssignmentProblems) {
                Problem sourceProblem = sourceAp.getProblem();

                // 권한 체크: 문제를 만든 instructor인지 확인
                if (sourceProblem.getCreatedBy() == null ||
                        !sourceProblem.getCreatedBy().getId().equals(instructorId)) {
                    log.warn("문제 {}는 복사할 권한이 없어 건너뜁니다.", sourceProblem.getId());
                    continue;
                }

                // ✅ 문제 복사 (DOMJudge에 새 문제로 업로드됨)
                Long newProblemId = problemService.copyProblem(
                        sourceProblem.getId(), null, instructorId);

                Problem newProblem = problemRepository.findById(newProblemId)
                        .orElseThrow(() -> new RuntimeException("복사된 문제를 찾을 수 없습니다: " + newProblemId));

                AssignmentProblem newAp = AssignmentProblem.builder()
                        .assignment(savedAssignment)
                        .problem(newProblem)
                        .problemOrder(order++)
                        .build();

                newAssignmentProblems.add(newAp);

                // ✅ DOMJudge Contest에 문제 추가
                domjudgeService.addProblemToContest(savedSection.getId(), newProblem.getDomjudgeProblemId());
            }

            assignmentProblemRepository.saveAll(newAssignmentProblems);
        }

        log.info("Section 복사 완료: 원본 ID={}, 새 ID={}, 공지사항={}개, 과제={}개",
                sourceSectionId, savedSection.getId(), newNotices.size(), sourceAssignments.size());
        return savedSection.getId();
    }
}
