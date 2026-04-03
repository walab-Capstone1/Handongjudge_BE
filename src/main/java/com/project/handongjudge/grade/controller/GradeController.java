package com.project.handongjudge.grade.controller;

import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.service.AssignmentService;
import com.project.handongjudge.grade.dto.*;
import com.project.handongjudge.common.ZipExportPathUtils;
import com.project.handongjudge.common.time.SubmissionDeadlineComparison;
import com.project.handongjudge.grade.service.GradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/api/sections/{sectionId}/assignments/{assignmentId}/grades")
public class GradeController {

    private final GradeService gradeService;
    private final AssignmentService assignmentService;
    private final AssignmentRepository assignmentRepository;

    /**
     * 학생별 문제 성적 입력/수정
     */
    @PostMapping
    public ResponseEntity<GradeResponseDTO> saveGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody GradeRequestDTO request,
            Authentication authentication) {
        
        Long tutorId = Long.parseLong(authentication.getName());
        request.setAssignmentId(assignmentId);
        
        GradeResponseDTO response = gradeService.saveGrade(request, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 일괄 성적 입력
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<GradeResponseDTO>> saveBulkGrades(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody BulkGradeRequestDTO request,
            Authentication authentication) {
        
        Long tutorId = Long.parseLong(authentication.getName());
        request.setAssignmentId(assignmentId);
        
        List<GradeResponseDTO> responses = gradeService.saveBulkGrades(request, tutorId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 과제별 모든 학생 성적 조회
     */
    @GetMapping
    public ResponseEntity<List<StudentGradeSummaryDTO>> getAssignmentGrades(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId) {
        
        List<StudentGradeSummaryDTO> grades = 
                gradeService.getAssignmentGrades(assignmentId, sectionId);
        return ResponseEntity.ok(grades);
    }

    /**
     * 과제 제출 코드 ZIP 다운로드 (메타 CSV + 코드 파일).
     * 경로: {과제명}/{문제}/{학번_이름}/코드파일
     */
    @GetMapping("/export-zip")
    public ResponseEntity<byte[]> exportAssignmentCodesZip(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            Authentication authentication) {
        Long tutorId = Long.parseLong(authentication.getName());
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        List<StudentGradeSummaryDTO> grades = gradeService.getAssignmentGrades(assignmentId, sectionId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            StringBuilder csv = new StringBuilder();
            csv.append("studentName,studentId,studentIdWithSubmittedAt,itemType,itemTitle,problemId,problemTitle,problemTitleWithSubmittedAt,result,submittedAt,dueAt,isOnTime,lateDuration,codeFilePath\n");
            Set<String> usedPaths = new HashSet<>();

            for (StudentGradeSummaryDTO student : grades) {
                for (StudentGradeSummaryDTO.ProblemGradeDTO pg : student.getProblemGrades()) {
                    if (!Boolean.TRUE.equals(pg.getSubmitted())) continue;

                    StudentAcceptedCodeResponse codeResponse;
                    try {
                        codeResponse = assignmentService.getStudentAcceptedCode(
                                sectionId, assignmentId, student.getUserId(), pg.getProblemId(), tutorId);
                    } catch (Exception ex) {
                        continue;
                    }

                    String safeAssignment = sanitize(assignment.getTitle());
                    String safeStudent = ZipExportPathUtils.studentFolderSegment(student);
                    String safeProblem = sanitize(pg.getProblemTitle());
                    String ext = languageToExt(codeResponse.getLanguage());
                    String problemFolder = pg.getProblemId() + "_" + safeProblem;
                    String baseCodePath = safeAssignment + "/" + problemFolder + "/" + safeStudent + "/" + safeProblem + "_" + SubmissionDeadlineComparison.submittedAtKstForZipPath(pg.getSubmittedAt());
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

                    csv.append(csvEsc(student.getStudentName())).append(',')
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

            zos.putNextEntry(new ZipEntry("submissions.csv"));
            zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();

            String fileName = "assignment_" + assignmentId + "_submission_codes.zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("ZIP 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 학생별 과제 성적 조회
     */
    @GetMapping("/students/{userId}")
    public ResponseEntity<StudentGradeSummaryDTO> getStudentGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long userId) {
        
        StudentGradeSummaryDTO grade = gradeService.getStudentGrade(assignmentId, userId);
        return ResponseEntity.ok(grade);
    }

    /**
     * 문제별 학생 성적 조회
     */
    @GetMapping("/problems/{problemId}/students/{userId}")
    public ResponseEntity<GradeResponseDTO> getProblemGrade(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            @PathVariable Long userId) {
        
        GradeResponseDTO grade = gradeService.getProblemGrade(assignmentId, problemId, userId);
        return ResponseEntity.ok(grade);
    }

    /**
     * 성적 삭제
     */
    @DeleteMapping("/{gradeId}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long gradeId) {
        gradeService.deleteGrade(gradeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 과제의 문제 배점 설정
     */
    @PutMapping("/problems/{problemId}/points")
    public ResponseEntity<Void> setProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @PathVariable Long problemId,
            @RequestBody Map<String, Integer> request,
            Authentication authentication) {
        
        // 권한 확인은 Service에서 처리
        Integer points = request.get("points");
        
        if (points == null) {
            return ResponseEntity.badRequest().build();
        }
        
        gradeService.setProblemPoints(assignmentId, problemId, points);
        return ResponseEntity.ok().build();
    }

    /**
     * 과제의 모든 문제 배점 일괄 설정
     */
    @PutMapping("/points/bulk")
    public ResponseEntity<Void> setBulkProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long assignmentId,
            @RequestBody Map<Long, Integer> problemPoints,
            Authentication authentication) {
        
        // 권한 확인은 Service에서 처리
        gradeService.setBulkProblemPoints(assignmentId, problemPoints);
        return ResponseEntity.ok().build();
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

