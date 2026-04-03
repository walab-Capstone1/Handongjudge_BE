package com.project.handongjudge.assignment.controller;

import com.project.handongjudge.assignment.dto.AssignmentRequest;
import com.project.handongjudge.assignment.dto.AssignmentResponse;
import com.project.handongjudge.assignment.dto.AssignmentSubmissionStatsResponse;
import com.project.handongjudge.assignment.dto.UpcomingAssignmentResponse;
import com.project.handongjudge.assignment.dto.UserSubmissionStatusResponse;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.service.AssignmentService;
import com.project.handongjudge.grade.dto.StudentGradeSummaryDTO;
import com.project.handongjudge.grade.service.GradeService;
import com.project.handongjudge.problem.dto.ProblemDto;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;
import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.common.ZipExportPathUtils;
import com.project.handongjudge.common.time.SubmissionDeadlineComparison;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final ProblemService problemService;
    private final GradeService gradeService;
    private final AssignmentRepository assignmentRepository;

    @PostMapping
    public ResponseEntity<AssignmentResponse> createAssignment(
            @PathVariable Long sectionId,
            @RequestBody AssignmentRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.createAssignment(sectionId, request, userId)
        );
    }

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAssignmentsBySection(
            @PathVariable Long sectionId,
            Authentication authentication) {  // Authentication 추가
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getAssignmentsBySection(sectionId, userId)  // userId 전달
        );
    }

    @GetMapping("/{assignmentId}")
    public ResponseEntity<AssignmentResponse> getAssignmentInfo(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication  // 추가
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getAssignmentInfo(assignmentId, userId)  // userId 전달
        );
    }

    @GetMapping("/{assignmentId}/problems")
    public ResponseEntity<List<ProblemDto>> getAssignmentProblems( // 과제 문제 목록 조회 (순서대로)
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(
                assignmentService.getAssignmentProblems(assignmentId).getProblems()
        );
    }

    // 기존 코드에 추가
    @GetMapping("/{assignmentId}/submission-stats")
    public ResponseEntity<AssignmentSubmissionStatsResponse> getAssignmentSubmissionStats(
            @PathVariable Long assignmentId,
            @PathVariable Long sectionId) {  // @RequestParam → @PathVariable로 변경

        AssignmentSubmissionStatsResponse stats = assignmentService.getAssignmentSubmissionStats(assignmentId, sectionId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/instructor/{instructorId}/all-submission-stats")
    public ResponseEntity<List<AssignmentSubmissionStatsResponse>> getAllAssignmentsSubmissionStats(
            @PathVariable Long instructorId) {

        List<AssignmentSubmissionStatsResponse> allStats = assignmentService.getAllAssignmentsSubmissionStats(instructorId);
        return ResponseEntity.ok(allStats);
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody AssignmentRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.updateAssignment(sectionId, assignmentId, request, userId)
        );
    }
    @GetMapping("/{assignmentId}/user-submission-status")
    public ResponseEntity<UserSubmissionStatusResponse> getUserSubmissionStatus(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                assignmentService.getUserSubmissionStatus(sectionId, assignmentId, userId)
        );
    }
    // 과제별 학생 진행 현황 조회
    @GetMapping("/{assignmentId}/student-progress")
    public ResponseEntity<List<StudentProgressResponse>> getAssignmentStudentProgress(
            @PathVariable Long assignmentId,
            @PathVariable Long sectionId) {

        List<StudentProgressResponse> progress = assignmentService.getAssignmentStudentProgress(assignmentId, sectionId);
        return ResponseEntity.ok(progress);
    }
    @PatchMapping("/{assignmentId}/active")
    public ResponseEntity<AssignmentResponse> toggleAssignmentActive(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        AssignmentResponse response = assignmentService.toggleAssignmentActive(assignmentId, active, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 과제 삭제
     */
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        assignmentService.deleteAssignment(sectionId, assignmentId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 마감 직전 과제 조회
     * @param sectionId 분반 ID
     * @param days 마감일까지 남은 일수 (기본값: 3일)
     * @return 마감 직전 과제 목록 (제출률 포함)
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingAssignmentResponse>> getUpcomingAssignments(
            @PathVariable Long sectionId,
            @RequestParam(required = false, defaultValue = "3") Integer days) {
        List<UpcomingAssignmentResponse> upcomingAssignments =
                assignmentService.getUpcomingAssignments(sectionId, days);
        return ResponseEntity.ok(upcomingAssignments);
    }

    /**
     * 튜터가 학생의 accept된 코드 조회
     * @param sectionId 분반 ID
     * @param assignmentId 과제 ID
     * @param userId 학생 ID
     * @param problemId 문제 ID
     * @param authentication 인증 정보 (튜터)
     * @return 학생의 accept된 코드 정보
     */
    @GetMapping("/{assignmentId}/students/{userId}/problems/{problemId}/accepted-code")
    public ResponseEntity<StudentAcceptedCodeResponse> getStudentAcceptedCode(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long userId,
            @PathVariable Long problemId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        StudentAcceptedCodeResponse response = assignmentService.getStudentAcceptedCode(
                sectionId, assignmentId, userId, problemId, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 섹션의 전체 과제 제출 코드 ZIP 다운로드
     * 구조: 전체과제/{과제명}/{문제}/{학번_이름}/코드파일
     */
    @GetMapping("/export-zip-all")
    public ResponseEntity<byte[]> exportAllAssignmentCodesZip(
            @PathVariable Long sectionId,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        List<Assignment> assignments = assignmentRepository.findAllAssignmentsBySectionId(sectionId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            StringBuilder csv = new StringBuilder();
            csv.append("submissionId,studentName,studentId,studentIdWithSubmittedAt,itemType,itemTitle,problemId,problemTitle,problemTitleWithSubmittedAt,result,submittedAt,dueAt,isOnTime,lateDuration,codeFilePath\n");
            Set<String> usedPaths = new HashSet<>();

            for (Assignment assignment : assignments) {
                List<StudentGradeSummaryDTO> grades = gradeService.getAssignmentGrades(assignment.getId(), sectionId);
                for (StudentGradeSummaryDTO student : grades) {
                    for (StudentGradeSummaryDTO.ProblemGradeDTO pg : student.getProblemGrades()) {
                        if (!Boolean.TRUE.equals(pg.getSubmitted())) continue;

                        StudentAcceptedCodeResponse codeResponse;
                        try {
                            codeResponse = assignmentService.getStudentAcceptedCode(
                                    sectionId, assignment.getId(), student.getUserId(), pg.getProblemId(), tutorId);
                        } catch (Exception ex) {
                            continue;
                        }

                        String safeAssignment = sanitize(assignment.getTitle());
                        String safeStudent = ZipExportPathUtils.studentFolderSegment(student);
                        String safeProblem = sanitize(pg.getProblemTitle());
                        String ext = languageToExt(codeResponse.getLanguage());
                        String problemFolder = pg.getProblemId() + "_" + safeProblem;
                        String baseCodePath = "전체과제/" + safeAssignment + "/" + problemFolder + "/" + safeStudent + "/" + safeProblem + "_" + SubmissionDeadlineComparison.submittedAtKstForZipPath(pg.getSubmittedAt());
                        String codePath = uniquePath(
                                baseCodePath,
                                ext,
                                codeResponse.getSubmissionId(),
                                usedPaths
                        );

                        ZipEntry codeEntry = new ZipEntry(codePath);
                        zos.putNextEntry(codeEntry);
                        String code = codeResponse.getCode() == null ? "" : codeResponse.getCode();
                        zos.write(code.getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();

                        csv.append(codeResponse.getSubmissionId() == null ? "" : codeResponse.getSubmissionId()).append(',')
                                .append(csvEsc(student.getStudentName())).append(',')
                                .append(csvEsc(student.getStudentId())).append(',')
                                .append(csvEsc(withSubmittedAt(student.getStudentId(), pg.getSubmittedAt()))).append(',')
                                .append("assignment").append(',')
                                .append(csvEsc(assignment.getTitle())).append(',')
                                .append(pg.getProblemId()).append(',')
                                .append(csvEsc(pg.getProblemTitle())).append(',')
                                .append(csvEsc(withSubmittedAt(pg.getProblemTitle(), pg.getSubmittedAt()))).append(',')
                                .append(csvEsc(pg.getResult())).append(',')
                                .append(csvEsc(SubmissionDeadlineComparison.formatSubmittedAtKstCsv(pg.getSubmittedAt()))).append(',')
                                .append(csvEsc(SubmissionDeadlineComparison.formatDueKstCsv(assignment.getEndDate()))).append(',')
                                .append(Boolean.TRUE.equals(pg.getIsOnTime()) ? "true" : "false").append(',')
                                .append(csvEsc(SubmissionDeadlineComparison.lateDurationText(pg.getSubmittedAt(), assignment.getEndDate()))).append(',')
                                .append(csvEsc(codePath))
                                .append('\n');
                    }
                }
            }

            zos.putNextEntry(new ZipEntry("submissions.csv"));
            zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();

            String fileName = "section_" + sectionId + "_all_assignments_submission_codes.zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("ZIP 생성 실패: " + e.getMessage(), e);
        }
    }

    private static String csvEsc(String s) {
        String value = s == null ? "" : s;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String sanitize(String s) {
        String value = s == null ? "unknown" : s.trim();
        if (value.isEmpty()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }

    private static String withSubmittedAt(String base, LocalDateTime submittedAt) {
        String value = base == null ? "" : base;
        String submitted = SubmissionDeadlineComparison.formatSubmittedAtKstCsv(submittedAt);
        if (submitted.isEmpty()) return value;
        return value + " (" + submitted + ")";
    }

    private static String languageToExt(String language) {
        if (language == null) return ".txt";
        String l = language.toLowerCase();
        if (l.contains("java")) return ".java";
        if (l.contains("python") || l.contains("py")) return ".py";
        if (l.contains("cpp") || l.contains("c++")) return ".cpp";
        if (l.equals("c")) return ".c";
        if (l.contains("javascript") || l.contains("node") || l.equals("js")) return ".js";
        if (l.contains("typescript") || l.equals("ts")) return ".ts";
        return ".txt";
    }

    private static String uniquePath(String basePath, String ext, Long submissionId, Set<String> usedPaths) {
        String candidate = basePath + ext;
        if (usedPaths.add(candidate)) {
            return candidate;
        }
        int index = 1;
        while (true) {
            String next = basePath + "_dup" + index + ext;
            if (usedPaths.add(next)) {
                return next;
            }
            index++;
        }
    }
}
