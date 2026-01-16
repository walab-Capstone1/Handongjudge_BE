package com.project.handongjudge.section.service;

import com.project.handongjudge.course.entity.Course;
import com.project.handongjudge.course.repository.CourseRepository;
import com.project.handongjudge.section.dto.SectionInfoDto;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;
import com.project.handongjudge.section.dto.SectionWithCourseRequest;
import com.project.handongjudge.section.dto.NoticeEditData;
import com.project.handongjudge.section.dto.AssignmentEditData;
import com.project.handongjudge.section.dto.ProblemEditData;
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
import java.util.*;

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
                .sectionNumber(request.getSectionNumber())  // null 허용
                .enrollmentCode(enrollmentCode)
                .year(request.getYear())
                .semester(request.getSemester())
                .active(false) // 새로 생성된 수업은 초기에 비활성화 상태로 생성
                .build();

        Section saved = sectionRepository.save(section);

        // courseTitle을 전달 (sectionNumber는 null일 수 있음)
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

    /**
     * Section 복사 (권한 체크 포함)
     * 원본 Section을 만든 instructor만 복사 가능
     * 옵션에 따라 Assignment, Problem, Notice 선택적 복사
     */
    /**
     * Section 복사 (권한 체크 포함)
     * 선택된 Assignment와 Problem만 복사
     */
    @Transactional
    public Long copySection(Long sourceSectionId, Integer newSectionNumber,
                            Integer newYear, String newSemester, String newCourseTitle,
                            String newDescription, Boolean copyNotices, Boolean copyAssignments,
                            List<Long> selectedNoticeIds, List<Long> selectedAssignmentIds,
                            Map<Long, List<Long>> assignmentProblems, Long instructorId,
                            Map<Long, NoticeEditData> noticeEdits,
                            Map<Long, AssignmentEditData> assignmentEdits,
                            Map<Long, ProblemEditData> problemEdits) throws IOException {
        Section sourceSection = sectionRepository.findById(sourceSectionId)
                .orElseThrow(() -> new IllegalArgumentException("원본 Section을 찾을 수 없습니다: " + sourceSectionId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // ✅ 권한 체크: 해당 Section의 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = sourceSection.getInstructor().getId().equals(instructorId) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("이 수업을 복사할 권한이 없습니다.");
        }

        // ✅ 새 Course 생성
        String originalCourseTitle = sourceSection.getCourse().getTitle();
        String finalTitle = (newCourseTitle != null && !newCourseTitle.trim().isEmpty())
                ? newCourseTitle
                : originalCourseTitle + " 복사본";

        String finalDescription = (newDescription != null && !newDescription.trim().isEmpty())
                ? newDescription
                : sourceSection.getCourse().getDescription();

        Course newCourse = Course.builder()
                .title(finalTitle)
                .description(finalDescription)
                .createdAt(LocalDateTime.now())
                .build();
        Course savedCourse = courseRepository.save(newCourse);

        // 새 Section 생성
        String enrollmentCode = generateEnrollmentCode();
        Section newSection = Section.builder()
                .course(savedCourse)
                .instructor(sourceSection.getInstructor())
                .sectionNumber(newSectionNumber)
                .enrollmentCode(enrollmentCode)
                .year(newYear)
                .semester(newSemester)
                .active(false) // 복사된 수업도 초기에 비활성화 상태로 생성
                .build();

        Section savedSection = sectionRepository.save(newSection);

        // ✅ DOMJudge Contest 생성
        domjudgeService.createContest(
                savedSection.getId(),
                newSectionNumber,
                finalTitle
        );

        // ✨ 1. 공지사항 복사 (선택된 공지사항만)
        if (copyNotices != null && copyNotices) {
            List<Notice> sourceNotices;

            if (selectedNoticeIds != null && !selectedNoticeIds.isEmpty()) {
                sourceNotices = noticeRepository.findByIdIn(selectedNoticeIds);
                // 권한 체크: 선택된 공지사항이 모두 해당 Section에 속하는지 확인
                for (Notice notice : sourceNotices) {
                    if (!notice.getSection().getId().equals(sourceSectionId)) {
                        throw new IllegalArgumentException("선택한 공지사항이 해당 수업에 속하지 않습니다.");
                    }
                }
            } else {
                sourceNotices = noticeRepository.findBySectionIdOrderByCreatedAtDesc(sourceSectionId);
            }

            List<Notice> newNotices = new ArrayList<>();
            for (Notice sourceNotice : sourceNotices) {
                // 수정된 제목/내용이 있으면 사용, 없으면 원본 사용
                String noticeTitle = sourceNotice.getTitle();
                String noticeContent = sourceNotice.getContent();
                if (noticeEdits != null && noticeEdits.containsKey(sourceNotice.getId())) {
                    NoticeEditData editData = noticeEdits.get(sourceNotice.getId());
                    if (editData != null) {
                        if (editData.getTitle() != null && !editData.getTitle().trim().isEmpty()) {
                            noticeTitle = editData.getTitle();
                        }
                        if (editData.getContent() != null && !editData.getContent().trim().isEmpty()) {
                            noticeContent = editData.getContent();
                        }
                    }
                }
                
                Notice newNotice = Notice.builder()
                        .section(savedSection)
                        .title(noticeTitle)
                        .content(noticeContent)
                        .difficulty(sourceNotice.getDifficulty())
                        .isNew(true)
                        .createdAt(LocalDateTime.now())
                        .active(false) // 복사된 공지사항은 초기에 비활성화 상태로 생성
                        .build();
                newNotices.add(newNotice);
            }
            noticeRepository.saveAll(newNotices);
            log.info("공지사항 {}개 복사 완료", newNotices.size());
        }

        // ✨ 2. 선택된 Assignment 및 Problem만 복사
        if (copyAssignments != null && copyAssignments) {
            List<Assignment> sourceAssignments;

            // 선택된 과제 ID가 있으면 해당 과제만 조회
            if (selectedAssignmentIds != null && !selectedAssignmentIds.isEmpty()) {
                sourceAssignments = assignmentRepository.findByIdIn(selectedAssignmentIds);
                // 권한 체크: 선택된 과제가 모두 해당 Section에 속하는지 확인
                for (Assignment assignment : sourceAssignments) {
                    if (!assignment.getSection().getId().equals(sourceSectionId)) {
                        throw new IllegalArgumentException("선택한 과제가 해당 수업에 속하지 않습니다.");
                    }
                }
            } else {
                // 선택된 과제가 없으면 모든 과제 복사
                sourceAssignments = assignmentRepository.findBySectionId(sourceSectionId);
            }

            int copiedAssignmentCount = 0;
            int copiedProblemCount = 0;

            for (Assignment sourceAssignment : sourceAssignments) {
                // 수정된 제목/내용이 있으면 사용, 없으면 원본 사용
                String assignmentTitle = sourceAssignment.getTitle();
                String assignmentDescription = sourceAssignment.getDescription();
                if (assignmentEdits != null && assignmentEdits.containsKey(sourceAssignment.getId())) {
                    AssignmentEditData editData = assignmentEdits.get(sourceAssignment.getId());
                    if (editData != null) {
                        if (editData.getTitle() != null && !editData.getTitle().trim().isEmpty()) {
                            assignmentTitle = editData.getTitle();
                        }
                        if (editData.getDescription() != null && !editData.getDescription().trim().isEmpty()) {
                            assignmentDescription = editData.getDescription();
                        }
                    }
                }
                
                Assignment newAssignment = Assignment.builder()
                        .section(savedSection)
                        .assignmentNumber(sourceAssignment.getAssignmentNumber())
                        .title(assignmentTitle)
                        .description(assignmentDescription)
                        .startDate(sourceAssignment.getStartDate())
                        .endDate(sourceAssignment.getEndDate())
                        .active(false) // 복사된 과제는 초기에 비활성화 상태로 생성
                        .build();

                Assignment savedAssignment = assignmentRepository.save(newAssignment);
                copiedAssignmentCount++;

                // ✨ 3. 선택된 Problem만 복사
                List<AssignmentProblem> sourceAssignmentProblems = assignmentProblemRepository
                        .findByAssignmentId(sourceAssignment.getId());

                List<AssignmentProblem> newAssignmentProblems = new ArrayList<>();
                int order = 1;

                for (AssignmentProblem sourceAp : sourceAssignmentProblems) {
                    Problem sourceProblem = sourceAp.getProblem();

                    // 선택된 문제만 복사 (assignmentProblems에 해당 과제의 문제 ID가 있는 경우)
                    if (assignmentProblems != null && assignmentProblems.containsKey(sourceAssignment.getId())) {
                        List<Long> selectedProblemIds = assignmentProblems.get(sourceAssignment.getId());
                        if (selectedProblemIds != null && !selectedProblemIds.contains(sourceProblem.getId())) {
                            log.info("문제 {}는 선택되지 않아 건너뜁니다.", sourceProblem.getId());
                            continue;
                        }
                    }

                    // 권한 체크: 문제를 만든 instructor인지 확인
                    if (sourceProblem.getCreatedBy() == null ||
                            !sourceProblem.getCreatedBy().getId().equals(instructorId)) {
                        log.warn("문제 {}는 복사할 권한이 없어 건너뜁니다.", sourceProblem.getId());
                        continue;
                    }

                    // 수정된 제목이 있으면 사용, 없으면 null (원본 제목 사용)
                    String problemTitle = null;
                    if (problemEdits != null && problemEdits.containsKey(sourceProblem.getId())) {
                        ProblemEditData editData = problemEdits.get(sourceProblem.getId());
                        if (editData != null && editData.getTitle() != null && !editData.getTitle().trim().isEmpty()) {
                            problemTitle = editData.getTitle();
                        }
                    }
                    
                    // ✅ 문제 복사 (DOMJudge에 새 문제로 업로드됨)
                    Long newProblemId = problemService.copyProblem(
                            sourceProblem.getId(), problemTitle, instructorId);

                    Problem newProblem = problemRepository.findById(newProblemId)
                            .orElseThrow(() -> new RuntimeException("복사된 문제를 찾을 수 없습니다: " + newProblemId));

                    AssignmentProblem newAp = AssignmentProblem.builder()
                            .assignment(savedAssignment)
                            .problem(newProblem)
                            .problemOrder(order++)
                            .build();

                    newAssignmentProblems.add(newAp);
                    copiedProblemCount++;

                    // ✅ DOMJudge Contest에 문제 추가
                    domjudgeService.addProblemToContest(savedSection.getId(), newProblem.getDomjudgeProblemId());
                }

                assignmentProblemRepository.saveAll(newAssignmentProblems);
            }

            log.info("과제 {}개, 문제 {}개 복사 완료", copiedAssignmentCount, copiedProblemCount);
        }

        log.info("Section 복사 완료: 원본 ID={}, 새 ID={}", sourceSectionId, savedSection.getId());
        return savedSection.getId();
    }
}
