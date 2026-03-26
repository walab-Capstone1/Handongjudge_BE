package com.project.handongjudge.quiz.controller;

import com.project.handongjudge.assignment.dto.StudentAcceptedCodeResponse;
import com.project.handongjudge.grade.dto.StudentGradeSummaryDTO;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.repository.QuizRepository;
import com.project.handongjudge.assignment.dto.StudentProgressResponse;
import com.project.handongjudge.quiz.dto.*;
import com.project.handongjudge.quiz.entity.Quiz;
import com.project.handongjudge.quiz.service.QuizService;
import com.project.handongjudge.quiz.service.QuizSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections/{sectionId}/quizzes")
public class QuizController {

    private final QuizService quizService;
    private final QuizSessionService quizSessionService;
    private final QuizRepository quizRepository;

    /**
     * 코딩 테스트 생성
     */
    @PostMapping
    public ResponseEntity<QuizResponse> createQuiz(
            @PathVariable Long sectionId,
            @RequestBody QuizRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.createQuiz(sectionId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 섹션별 코딩 테스트 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<QuizListDto>> getQuizzesBySection(
            @PathVariable Long sectionId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<QuizListDto> quizzes = quizService.getQuizzesBySection(sectionId, userId);
        return ResponseEntity.ok(quizzes);
    }

    /**
     * 코딩 테스트 상세 정보 조회
     */
    @GetMapping("/{quizId}")
    public ResponseEntity<QuizResponse> getQuizInfo(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.getQuizInfo(quizId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코딩 테스트 문제 목록 조회
     */
    @GetMapping("/{quizId}/problems")
    public ResponseEntity<List<QuizProblemDto>> getQuizProblems(
            @PathVariable Long sectionId,
            @PathVariable Long quizId
    ) {
        List<QuizProblemDto> problems = quizService.getQuizProblems(quizId);
        return ResponseEntity.ok(problems);
    }

    /**
     * 코딩 테스트 수정
     */
    @PutMapping("/{quizId}")
    public ResponseEntity<QuizResponse> updateQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody QuizRequest request,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizResponse response = quizService.updateQuiz(sectionId, quizId, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 코딩 테스트 삭제
     */
    @DeleteMapping("/{quizId}")
    public ResponseEntity<Void> deleteQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        quizService.deleteQuiz(quizId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 퀴즈 학생 진행 현황 일괄 조회
     */
    @GetMapping("/{quizId}/student-progress")
    public ResponseEntity<List<StudentProgressResponse>> getQuizStudentProgress(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<StudentProgressResponse> progress = quizService.getQuizStudentProgress(quizId, sectionId, userId);
        return ResponseEntity.ok(progress);
    }

    /**
     * 퀴즈 문제별 제출 통계 조회
     */
    @GetMapping("/{quizId}/submission-stats")
    public ResponseEntity<QuizSubmissionStatsResponse> getQuizSubmissionStats(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        QuizSubmissionStatsResponse stats = quizService.getQuizSubmissionStats(quizId, sectionId, userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 퀴즈에서 문제 제거
     */
    @DeleteMapping("/{quizId}/problems/{problemId}")
    public ResponseEntity<Void> removeProblemFromQuiz(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        quizService.removeProblemFromQuiz(quizId, problemId, instructorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 퀴즈 성적 조회
     */
    @GetMapping("/{quizId}/grades")
    public ResponseEntity<List<com.project.handongjudge.grade.dto.StudentGradeSummaryDTO>> getQuizGrades(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        List<com.project.handongjudge.grade.dto.StudentGradeSummaryDTO> grades = 
                quizService.getQuizGrades(quizId, sectionId, userId);
        return ResponseEntity.ok(grades);
    }

    /**
     * 퀴즈 제출 코드 ZIP 다운로드 (메타 CSV + 코드 파일)
     */
    @GetMapping("/{quizId}/grades/export-zip")
    public ResponseEntity<byte[]> exportQuizCodesZip(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        List<StudentGradeSummaryDTO> grades = quizService.getQuizGrades(quizId, sectionId, tutorId);

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
                        codeResponse = quizService.getStudentAcceptedCode(
                                sectionId, quizId, student.getUserId(), pg.getProblemId(), tutorId);
                    } catch (Exception ex) {
                        continue;
                    }

                    String safeQuiz = sanitize(quiz.getTitle());
                    String safeStudent = sanitize(student.getStudentId());
                    String safeProblem = sanitize(pg.getProblemTitle());
                    String ext = languageToExt(codeResponse.getLanguage());
                    String problemFolder = pg.getProblemId() + "_" + safeProblem;
                    String baseCodePath = safeQuiz + "/" + problemFolder + "/" + safeStudent + "/" + safeProblem + "_" + fileTime(pg.getSubmittedAt());
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
                            .append("quiz").append(',')
                            .append(csvEsc(quiz.getTitle())).append(',')
                            .append(pg.getProblemId()).append(',')
                            .append(csvEsc(pg.getProblemTitle())).append(',')
                            .append(csvEsc(withSubmittedAt(pg.getProblemTitle(), pg.getSubmittedAt()))).append(',')
                            .append(csvEsc(pg.getResult())).append(',')
                            .append(csvEsc(toStr(pg.getSubmittedAt()))).append(',')
                            .append(csvEsc(toStr(quiz.getEndTime()))).append(',')
                            .append(Boolean.TRUE.equals(pg.getIsOnTime()) ? "true" : "false").append(',')
                            .append(csvEsc(toLateText(pg.getSubmittedAt(), quiz.getEndTime()))).append(',')
                            .append(csvEsc(codePath))
                            .append('\n');
                }
            }

            zos.putNextEntry(new ZipEntry("submissions.csv"));
            zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();

            String fileName = "quiz_" + quizId + "_submission_codes.zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("ZIP 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 섹션의 전체 코딩테스트 제출 코드 ZIP 다운로드
     * 구조: 전체코딩테스트/{코딩테스트명}/{문제}/{학번}/코드파일
     */
    @GetMapping("/grades/export-zip-all")
    public ResponseEntity<byte[]> exportAllQuizCodesZip(
            @PathVariable Long sectionId,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        List<Quiz> quizzes = quizRepository.findBySectionIdOrderByStartTimeDesc(sectionId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            StringBuilder csv = new StringBuilder();
            csv.append("submissionId,studentName,studentId,studentIdWithSubmittedAt,itemType,itemTitle,problemId,problemTitle,problemTitleWithSubmittedAt,result,submittedAt,dueAt,isOnTime,lateDuration,codeFilePath\n");
            Set<String> usedPaths = new HashSet<>();

            for (Quiz quiz : quizzes) {
                List<StudentGradeSummaryDTO> grades = quizService.getQuizGrades(quiz.getId(), sectionId, tutorId);
                for (StudentGradeSummaryDTO student : grades) {
                    for (StudentGradeSummaryDTO.ProblemGradeDTO pg : student.getProblemGrades()) {
                        if (!Boolean.TRUE.equals(pg.getSubmitted())) continue;

                        StudentAcceptedCodeResponse codeResponse;
                        try {
                            codeResponse = quizService.getStudentAcceptedCode(
                                    sectionId, quiz.getId(), student.getUserId(), pg.getProblemId(), tutorId);
                        } catch (Exception ex) {
                            continue;
                        }

                        String safeQuiz = sanitize(quiz.getTitle());
                        String safeStudent = sanitize(student.getStudentId());
                        String safeProblem = sanitize(pg.getProblemTitle());
                        String ext = languageToExt(codeResponse.getLanguage());
                        String problemFolder = pg.getProblemId() + "_" + safeProblem;
                        String baseCodePath = "전체코딩테스트/" + safeQuiz + "/" + problemFolder + "/" + safeStudent + "/" + safeProblem + "_" + fileTime(pg.getSubmittedAt());
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
                                .append("quiz").append(',')
                                .append(csvEsc(quiz.getTitle())).append(',')
                                .append(pg.getProblemId()).append(',')
                                .append(csvEsc(pg.getProblemTitle())).append(',')
                                .append(csvEsc(withSubmittedAt(pg.getProblemTitle(), pg.getSubmittedAt()))).append(',')
                                .append(csvEsc(pg.getResult())).append(',')
                                .append(csvEsc(toStr(pg.getSubmittedAt()))).append(',')
                                .append(csvEsc(toStr(quiz.getEndTime()))).append(',')
                                .append(Boolean.TRUE.equals(pg.getIsOnTime()) ? "true" : "false").append(',')
                                .append(csvEsc(toLateText(pg.getSubmittedAt(), quiz.getEndTime()))).append(',')
                                .append(csvEsc(codePath))
                                .append('\n');
                    }
                }
            }

            zos.putNextEntry(new ZipEntry("submissions.csv"));
            zos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();

            String fileName = "section_" + sectionId + "_all_quizzes_submission_codes.zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("ZIP 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 퀴즈 단일 성적 저장/수정
     */
    @PostMapping("/{quizId}/grades")
    public ResponseEntity<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO> saveQuizGrade(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody com.project.handongjudge.quiz.dto.QuizGradeRequestDTO request,
            Authentication authentication
    ) {
        request.setQuizId(quizId);
        Long tutorId = Long.parseLong(authentication.getName());
        com.project.handongjudge.quiz.dto.QuizGradeResponseDTO response = quizService.saveQuizGrade(request, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 일괄 성적 저장
     */
    @PostMapping("/{quizId}/grades/bulk")
    public ResponseEntity<List<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO>> saveBulkQuizGrades(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody com.project.handongjudge.quiz.dto.QuizBulkGradeRequestDTO request,
            Authentication authentication
    ) {
        request.setQuizId(quizId);
        Long tutorId = Long.parseLong(authentication.getName());
        List<com.project.handongjudge.quiz.dto.QuizGradeResponseDTO> responses = quizService.saveBulkQuizGrades(request, tutorId);
        return ResponseEntity.ok(responses);
    }

    /**
     * 퀴즈 문제 배점 설정
     */
    @PutMapping("/{quizId}/grades/problems/{problemId}/points")
    public ResponseEntity<Void> setQuizProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long problemId,
            @RequestBody Map<String, Integer> body,
            Authentication authentication
    ) {
        Integer points = body.get("points");
        if (points == null) {
            return ResponseEntity.badRequest().build();
        }
        Long tutorId = Long.parseLong(authentication.getName());
        quizService.setQuizProblemPoints(quizId, problemId, points);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 문제 배점 일괄 설정
     */
    @PutMapping("/{quizId}/grades/points/bulk")
    public ResponseEntity<Void> setBulkQuizProblemPoints(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<Long, Integer> problemPoints,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        quizService.setBulkQuizProblemPoints(quizId, problemPoints);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 제출 기록 목록 조회 (튜터용)
     */
    @GetMapping("/{quizId}/submissions")
    public ResponseEntity<QuizSubmissionListResponse> getQuizSubmissions(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        QuizSubmissionListResponse response = quizService.getQuizSubmissions(
                sectionId, quizId, problemId, userId, result, page, size, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 제출 코드 조회 (튜터용 - 학생 제출 코드 상세 조회)
     */
    @GetMapping("/{quizId}/submissions/{submissionId}/code")
    public ResponseEntity<com.project.handongjudge.mypage.dto.SubmissionCodeDto> getQuizSubmissionCode(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long submissionId,
            Authentication authentication
    ) {
        Long tutorId = Long.parseLong(authentication.getName());
        com.project.handongjudge.mypage.dto.SubmissionCodeDto response =
                quizService.getQuizSubmissionCodeForTutor(sectionId, quizId, submissionId, tutorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 제출 코드 조회 (학생의 accept된 코드)
     */
    @GetMapping("/{quizId}/students/{userId}/problems/{problemId}/accepted-code")
    public ResponseEntity<StudentAcceptedCodeResponse> getStudentAcceptedCode(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @PathVariable Long userId,
            @PathVariable Long problemId,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        StudentAcceptedCodeResponse response = quizService.getStudentAcceptedCode(
                sectionId, quizId, userId, problemId, instructorId);
        return ResponseEntity.ok(response);
    }

    // ===== 시험 세션 관리 (중복 접속 방지) =====

    /**
     * 시험 페이지 진입 시도
     * 응답: { status: "OK" } or { status: "CONFLICT" }
     */
    @PostMapping("/{quizId}/session/enter")
    public ResponseEntity<Map<String, String>> enterQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        QuizSessionService.EnterResult result = quizSessionService.enter(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("status", result.name()));
    }

    /**
     * 기존 세션 강제 인계 (사용자가 "여기서 계속하기" 선택 시)
     */
    @PostMapping("/{quizId}/session/takeover")
    public ResponseEntity<Map<String, String>> takeoverQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        quizSessionService.takeover(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * Heartbeat - 세션 TTL 연장 및 유효성 확인
     * 응답: { valid: true } or { valid: false } (다른 곳에서 탈취됨)
     */
    @PostMapping("/{quizId}/session/heartbeat")
    public ResponseEntity<Map<String, Boolean>> heartbeatQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        boolean valid = quizSessionService.heartbeat(quizId, userId, clientSessionId);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /**
     * 시험 페이지 이탈 시 세션 해제
     */
    @PostMapping("/{quizId}/session/exit")
    public ResponseEntity<Void> exitQuizSession(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        String clientSessionId = body.get("sessionId");
        quizSessionService.exit(quizId, userId, clientSessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 퀴즈 상태 수동 변경 (시작/정지/종료)
     */
    @PatchMapping("/{quizId}/status")
    public ResponseEntity<QuizResponse> updateQuizStatus(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        String statusStr = request.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Quiz.QuizStatus status;
        try {
            status = Quiz.QuizStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        QuizResponse response = quizService.updateQuizStatus(quizId, status, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 퀴즈 비활성화/활성화 토글
     */
    @PatchMapping("/{quizId}/active")
    public ResponseEntity<QuizResponse> toggleQuizActive(
            @PathVariable Long sectionId,
            @PathVariable Long quizId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication
    ) {
        Long instructorId = Long.parseLong(authentication.getName());
        Boolean active = request.get("active");
        QuizResponse response = quizService.toggleQuizActive(quizId, active, instructorId);
        return ResponseEntity.ok(response);
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

    private static String toStr(LocalDateTime dt) {
        return dt == null ? "" : dt.toString();
    }

    private static String toLateText(LocalDateTime submittedAt, LocalDateTime dueAt) {
        if (submittedAt == null || dueAt == null) return "";
        Duration d = Duration.between(dueAt, submittedAt);
        if (d.isNegative() || d.isZero()) return "";
        long minutes = d.toMinutes();
        long days = minutes / (60 * 24);
        long hours = (minutes % (60 * 24)) / 60;
        long mins = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("일 ");
        if (hours > 0) sb.append(hours).append("시간 ");
        if (mins > 0 || sb.length() == 0) sb.append(mins).append("분");
        return sb.toString().trim();
    }

    private static String withSubmittedAt(String base, LocalDateTime submittedAt) {
        String value = base == null ? "" : base;
        String submitted = toStr(submittedAt);
        if (submitted.isEmpty()) return value;
        return value + " (" + submitted + ")";
    }

    private static String fileTime(LocalDateTime dt) {
        if (dt == null) return "unknown_time";
        return dt.toString().replace(":", "-").replace(".", "-");
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

