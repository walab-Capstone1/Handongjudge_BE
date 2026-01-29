package com.project.handongjudge.problem.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.problem.dto.*;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.assignment.entity.Assignment;
import com.project.handongjudge.assignment.repository.AssignmentRepository;
import com.project.handongjudge.assignment.service.AssignmentProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.project.handongjudge.problem.util.ProblemFileUtil;
import lombok.extern.slf4j.Slf4j;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final DomjudgeService domjudgeService;
    private final AssignmentProblemService assignmentProblemService;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final com.project.handongjudge.assignment.repository.AssignmentProblemRepository assignmentProblemRepository;
    private final com.project.handongjudge.problem.repository.ProblemSetProblemRepository problemSetProblemRepository;
    private final com.project.handongjudge.quiz.repository.QuizProblemRepository quizProblemRepository;
    private final com.project.handongjudge.problem.repository.ProblemSetRepository problemSetRepository;
    private final com.project.handongjudge.quiz.repository.QuizRepository quizRepository;

    @Value("${problem.zip.storage.path:./problem-zips}")
    private String zipStoragePath;





    public Long createProblem(ProblemCreateRequest request, Long instructorId) throws IOException {
        // ZIP 파일은 프론트엔드에서 파싱용으로만 사용하고, 
        // 실제 API는 항상 필드 기반으로 ZIP을 생성합니다.
        // 이를 통해 ZIP 내부 테스트케이스 수정/삭제가 가능합니다.
        return createProblemFromForm(request, instructorId);
    }

    /**
     * 기존 ZIP 파일로 문제 생성
     */
    private Long createProblemFromZip(ProblemCreateRequest request, Long instructorId, MultipartFile zipFile) throws IOException {
        String description = ProblemFileUtil.extractDescriptionFromZip(zipFile);
        Map<String, Object> limits = ProblemFileUtil.extractProblemLimits(zipFile);

        // 고유한 externalid 생성 (문제 생성 시에는 타임스탬프만 사용)
        String externalId = "problem-" + System.currentTimeMillis();
        
        // ZIP 파일에 externalid 추가 (문제 생성 시에는 테스트케이스 파일 없음)
        MultipartFile zipFileWithExternalId = setExternalIdInZipFile(zipFile, externalId, null);

        // DOMjudge에 문제 업로드
        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFileWithExternalId);

        // ZIP 파일을 데이터베이스에 저장 (externalid가 추가된 버전)
        byte[] zipFileData = saveProblemZipToDatabase(zipFileWithExternalId);

        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        Problem problem = Problem.builder()
                .title(request.getTitle())
                .description(description)
                .domjudgeProblemId(domjudgeProblemId)
                .timeLimit((Double) limits.get("timeLimit"))
                .memoryLimit((Integer) limits.get("memoryLimit"))
                .createdAt(LocalDateTime.now())
                .createdBy(instructor)
                .zipFilePath(null) // 더 이상 사용하지 않음
                .zipFileData(zipFileData) // DB에 저장
                .build();

        problemRepository.save(problem);
        return problem.getId();
    }

    /**
     * 폼 데이터로 문제 생성 (ZIP 생성 포함)
     */
    private Long createProblemFromForm(ProblemCreateRequest request, Long instructorId) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        // JSON 파싱
        List<String> tags = mapper.readValue(request.getTags(), new TypeReference<List<String>>(){});
        List<Map<String, String>> sampleInputs = mapper.readValue(request.getSampleInputs(),
                new TypeReference<List<Map<String, String>>>(){});

        // 전체 설명 생성 (description + inputFormat + outputFormat + samples)
        String fullDescription = buildFullDescription(
                request.getDescription(),
                request.getInputFormat(),
                request.getOutputFormat(),
                sampleInputs
        );

        // 고유한 externalid 생성 (문제 생성 시에는 타임스탬프만 사용)
        String externalId = "problem-" + System.currentTimeMillis();

        // DOMjudge 형식의 ZIP 파일 생성
        byte[] zipBytes = createDomjudgeZip(
                request.getTitle(),
                fullDescription,
                request.getTimeLimit(),
                request.getMemoryLimit(),
                request.getTestcaseFiles(),
                externalId
        );

        // MultipartFile로 변환
        MultipartFile generatedZipFile = createMultipartFile(zipBytes, request.getTitle() + ".zip");

        // DOMjudge에 업로드
        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(generatedZipFile);

        // ZIP 파일을 데이터베이스에 저장
        byte[] zipFileData = saveProblemZipToDatabase(generatedZipFile);

        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        Problem problem = Problem.builder()
                .title(request.getTitle())
                .description(fullDescription)
                .domjudgeProblemId(domjudgeProblemId)
                .timeLimit(Double.parseDouble(request.getTimeLimit()))
                .memoryLimit(Integer.parseInt(request.getMemoryLimit()))
                .createdAt(LocalDateTime.now())
                .createdBy(instructor)
                .zipFilePath(null) // 더 이상 사용하지 않음
                .zipFileData(zipFileData) // DB에 저장
                .build();

        problemRepository.save(problem);
        log.info("문제 생성 완료: ID={}, Title={}", problem.getId(), request.getTitle());

        return problem.getId();
    }

    /**
     * 전체 설명 생성
     */
    private String buildFullDescription(String description, String inputFormat,
                                        String outputFormat, List<Map<String, String>> sampleInputs) {
        StringBuilder sb = new StringBuilder(description);

        if (inputFormat != null && !inputFormat.trim().isEmpty()) {
            sb.append("\n\n## 입력 형식\n").append(inputFormat);
        }

        if (outputFormat != null && !outputFormat.trim().isEmpty()) {
            sb.append("\n\n## 출력 형식\n").append(outputFormat);
        }

        if (sampleInputs != null && !sampleInputs.isEmpty()) {
            sb.append("\n\n## 예제");
            for (int i = 0; i < sampleInputs.size(); i++) {
                Map<String, String> sample = sampleInputs.get(i);
                String input = sample.get("input");
                String output = sample.get("output");

                if ((input != null && !input.isEmpty()) || (output != null && !output.isEmpty())) {
                    sb.append("\n\n### 예제 입력 ").append(i + 1);
                    sb.append("\n```\n").append(input != null ? input : "").append("\n```");
                    sb.append("\n\n### 예제 출력 ").append(i + 1);
                    sb.append("\n```\n").append(output != null ? output : "").append("\n```");
                }
            }
        }

        return sb.toString();
    }

    /**
     * DOMjudge 형식의 ZIP 파일 생성
     */
    private byte[] createDomjudgeZip(String title, String description, String timeLimit,
                                     String memoryLimit, List<MultipartFile> testcaseFiles, String externalId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 1. problem.yaml 생성
            zos.putNextEntry(new ZipEntry("problem.yaml"));
            String yaml = createProblemYaml(title, timeLimit, memoryLimit);
            zos.write(yaml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. problem_statement/problem.md 생성
            zos.putNextEntry(new ZipEntry("problem_statement/problem.md"));
            zos.write(description.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. domjudge-problem.ini 생성
            zos.putNextEntry(new ZipEntry("domjudge-problem.ini"));
            String ini = createProblemIni(timeLimit, memoryLimit, externalId);
            zos.write(ini.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. 테스트케이스 파일 추가
            if (testcaseFiles != null && !testcaseFiles.isEmpty()) {
                log.info("ZIP 파일에 테스트케이스 추가 시작: 총 {}개", testcaseFiles.size());
                for (MultipartFile testcase : testcaseFiles) {
                    String filename = testcase.getOriginalFilename();
                    // data/sample/ 또는 data/secret/ 폴더에 배치
                    String folder = filename.contains("sample") ? "data/sample/" : "data/secret/";
                    String zipEntryPath = folder + filename;
                    zos.putNextEntry(new ZipEntry(zipEntryPath));
                    byte[] fileBytes = testcase.getBytes();
                    zos.write(fileBytes);
                    zos.closeEntry();
                    log.info("테스트케이스 추가됨: {} -> {}, 크기: {} bytes", filename, zipEntryPath, fileBytes.length);
                }
                log.info("ZIP 파일에 테스트케이스 추가 완료");
            } else {
                log.warn("ZIP 파일 생성 시 테스트케이스 파일이 없습니다. testcaseFiles={}", 
                        testcaseFiles == null ? "null" : "empty");
            }
        }

        return baos.toByteArray();
    }

    /**
     * problem.yaml 내용 생성
     */
    private String createProblemYaml(String title, String timeLimit, String memoryLimit) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: ").append(title).append("\n");
        yaml.append("author: HandongJudge\n");
        yaml.append("source: HandongJudge\n");
        yaml.append("limits:\n");

        if (timeLimit != null && !timeLimit.isEmpty()) {
            yaml.append("  time: ").append(timeLimit).append("\n");
        }

        if (memoryLimit != null && !memoryLimit.isEmpty()) {
            yaml.append("  memory: ").append(memoryLimit).append("\n");
        }

        return yaml.toString();
    }

    /**
     * domjudge-problem.ini 내용 생성
     */
    private String createProblemIni(String timeLimit, String memoryLimit, String externalId) {
        StringBuilder ini = new StringBuilder();

        if (externalId != null && !externalId.isEmpty()) {
            ini.append("externalid=").append(externalId).append("\n");
        }

        if (timeLimit != null && !timeLimit.isEmpty()) {
            ini.append("timelimit=").append(timeLimit).append("\n");
        }

        if (memoryLimit != null && !memoryLimit.isEmpty()) {
            ini.append("memorylimit=").append(memoryLimit).append("\n");
        }

        return ini.toString();
    }

    // convertToProblemResponse 메소드도 수정
    private ProblemResponse convertToProblemResponse(Problem problem) {
        // 문제가 사용되는 과제 개수 조회
        List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByProblemId(problem.getId());
        int assignmentCount = assignmentProblems.size();
        
        // 문제가 사용되는 문제집 개수 조회
        List<com.project.handongjudge.problem.entity.ProblemSetProblem> problemSetProblems = 
                problemSetProblemRepository.findByProblemId(problem.getId());
        int problemSetCount = problemSetProblems.size();
        
        // 문제가 사용되는 퀴즈 개수 조회
        List<com.project.handongjudge.quiz.entity.QuizProblem> quizProblems = 
                quizProblemRepository.findByProblemId(problem.getId());
        int quizCount = quizProblems.size();
        
        // 과제, 문제집, 퀴즈 중 하나라도 사용 중이면 isUsed = true
        boolean isUsed = assignmentCount > 0 || problemSetCount > 0 || quizCount > 0;
        
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .memoryLimit(problem.getMemoryLimit())     // 새로 추가
                .timeLimit(problem.getTimeLimit())         // 새로 추가
                .isUsed(isUsed)
                .assignmentCount(assignmentCount)
                .createdAt(problem.getCreatedAt())
                .build();
    }

    public List<Problem> getProblemsByAssignmentId(Long assignmentId) {
        List<Problem> problems = problemRepository.findByAssignmentId(assignmentId);
        return problems;
    }

    public ProblemResponse getProblem(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("Problem not found"));
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .timeLimit(problem.getTimeLimit())         // 추가
                .memoryLimit(problem.getMemoryLimit())     // 추가
                .createdAt(problem.getCreatedAt())
                .build();
    }

    public void addProblem(Long problemId, Long assignmentId) {
        assignmentProblemService.addProblemToAssignment(assignmentId, problemId);
    }
    // ProblemService.java에 추가
    public List<ProblemResponse> getAllProblems(Long instructorId) {
        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        List<Problem> problems;
        // 시스템 관리자는 모든 문제 조회 가능
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            problems = problemRepository.findAll();
        } else {
            // 일반 교수는 자신이 만든 문제만 조회
            problems = problemRepository.findByCreatedBy_Id(instructorId);
        }
        
        return problems.stream()
                .map(this::convertToProblemResponse)
                .collect(Collectors.toList());
    }


    /**
     * 문제 ZIP 파일을 데이터베이스에 저장
     */
    private byte[] saveProblemZipToDatabase(MultipartFile zipFile) throws IOException {
        return zipFile.getBytes();
    }

    /**
     * 문제 복사 (권한 체크 포함)
     */
    // Handongjudge_BE/src/main/java/com/project/handongjudge/problem/service/ProblemService.java

    public Long copyProblem(Long sourceProblemId, String newTitle, Long instructorId) throws IOException {
        // 원본 문제 조회
        Problem sourceProblem = problemRepository.findById(sourceProblemId)
                .orElseThrow(() -> new IllegalArgumentException("원본 문제를 찾을 수 없습니다: " + sourceProblemId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 체크: 문제 생성자이거나 시스템 관리자인지 확인
        boolean isAuthorized = (sourceProblem.getCreatedBy() != null && sourceProblem.getCreatedBy().getId().equals(instructorId)) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("이 문제를 복사할 권한이 없습니다.");
        }

        // ZIP 파일 데이터 확인
        byte[] originalZipData = null;

        // 1. DB에 저장된 경우 (zipFileData)
        if (sourceProblem.getZipFileData() != null && sourceProblem.getZipFileData().length > 0) {
            originalZipData = sourceProblem.getZipFileData();
        }
        // 2. 기존 파일 경로에 있는 경우 (마이그레이션용)
        else if (sourceProblem.getZipFilePath() != null) {
            Path zipPath = Paths.get(sourceProblem.getZipFilePath());
            if (!zipPath.isAbsolute()) {
                zipPath = Paths.get(System.getProperty("user.dir")).resolve(zipPath).normalize();
            }
            if (Files.exists(zipPath)) {
                originalZipData = Files.readAllBytes(zipPath);
            }
        }

        if (originalZipData == null || originalZipData.length == 0) {
            throw new IllegalArgumentException("원본 문제의 ZIP 파일을 찾을 수 없습니다.");
        }

        // byte[]를 MultipartFile로 변환
        MultipartFile originalZipFile = new ByteArrayMultipartFile(originalZipData, "problem.zip");

        // 새 문제 제목
        String problemTitle = (newTitle != null && !newTitle.trim().isEmpty())
                ? newTitle
                : sourceProblem.getTitle() + " (복사본)";

        // 고유한 externalid 생성 (문제 복사 시에는 타임스탬프만 사용)
        String externalId = "problem-" + System.currentTimeMillis();
        log.info("문제 복사 시 생성된 externalid: {}", externalId);

        // ZIP 파일에 새 externalid 설정 (기존 externalid 제거 후 새로 설정)
        MultipartFile zipFileWithNewExternalId = setExternalIdInZipFile(originalZipFile, externalId, null);

        // DOMJudge에 새 문제로 업로드
        String newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFileWithNewExternalId);

        // 새 ZIP 파일을 데이터베이스에 저장 (새 externalid가 설정된 버전)
        byte[] newZipFileData = saveProblemZipToDatabase(zipFileWithNewExternalId);

        // 새 Problem 엔티티 생성
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        Problem newProblem = Problem.builder()
                .title(problemTitle)
                .description(sourceProblem.getDescription())
                .domjudgeProblemId(newDomjudgeProblemId)
                .timeLimit(sourceProblem.getTimeLimit())
                .memoryLimit(sourceProblem.getMemoryLimit())
                .createdAt(LocalDateTime.now())
                .createdBy(instructor)
                .zipFilePath(null) // 더 이상 사용하지 않음
                .zipFileData(newZipFileData) // DB에 저장
                .build();

        Problem savedProblem = problemRepository.save(newProblem);
        log.info("문제 복사 완료: 원본 ID={}, 새 ID={}, Title={}",
                sourceProblemId, savedProblem.getId(), problemTitle);

        return savedProblem.getId();
    }

    /**
     * Path를 MultipartFile로 변환하는 내부 클래스
     */
    private static class PathMultipartFile implements MultipartFile {
        private final Path path;
        private final String name;

        public PathMultipartFile(Path path) {
            this.path = path;
            this.name = path.getFileName().toString();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    /**
     * MultipartFile의 파일명만 변경하는 래퍼 클래스
     */
    private static class RenamedMultipartFile implements MultipartFile {
        private final MultipartFile delegate;
        private final String newName;

        public RenamedMultipartFile(MultipartFile delegate, String newName) {
            this.delegate = delegate;
            this.newName = newName;
        }

        @Override
        public String getName() {
            return newName;
        }

        @Override
        public String getOriginalFilename() {
            return newName;
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public long getSize() {
            return delegate.getSize();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return delegate.getBytes();
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            delegate.transferTo(dest);
        }
    }

    /**
     * byte[]를 MultipartFile로 변환하는 헬퍼 클래스
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String filename) {
            this.content = content;
            this.name = filename;
            this.originalFilename = filename;
            this.contentType = "application/zip";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
    /**
     * ZIP 파일 내용 파싱
     */
    public ProblemParseResponse parseZipFile(MultipartFile zipFile) throws IOException {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("ZIP 파일이 필요합니다.");
        }

        // Description 추출
        String description = ProblemFileUtil.extractDescriptionFromZip(zipFile);

        // Limits 추출
        Map<String, Object> limits = ProblemFileUtil.extractProblemLimits(zipFile);

        // 제목 추출 (problem.yaml의 name 우선, 없으면 파일명)
        String title = ProblemFileUtil.extractTitleFromZip(zipFile);
        if (title == null || title.isEmpty()) {
            String filename = zipFile.getOriginalFilename();
            title = filename != null && filename.endsWith(".zip")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;
        }

        // 테스트케이스 추출
        List<TestCaseInfo> testCases = ProblemFileUtil.extractTestCasesFromZip(zipFile);

        return ProblemParseResponse.builder()
                .title(title)
                .description(description)
                .timeLimit((Double) limits.get("timeLimit"))
                .memoryLimit((Integer) limits.get("memoryLimit"))
                .author((String) limits.get("author"))
                .source((String) limits.get("source"))
                .difficulty(limits.get("difficulty") != null ? limits.get("difficulty").toString() : null)
                .testCases(testCases)
                .build();
    }

    /**
     * 문제의 ZIP 파일을 파싱하여 ProblemParseResponse 반환
     */
    public ProblemParseResponse parseProblemZip(Long problemId, Long instructorId) throws IOException {
        // 문제 조회
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다: " + problemId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 체크: 문제 생성자이거나 시스템 관리자인지 확인
        boolean isAuthorized = (problem.getCreatedBy() != null && problem.getCreatedBy().getId().equals(instructorId)) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("이 문제를 조회할 권한이 없습니다.");
        }

        // ZIP 파일 데이터 가져오기
        byte[] zipFileData = null;

        if (problem.getZipFileData() != null && problem.getZipFileData().length > 0) {
            zipFileData = problem.getZipFileData();
        } else if (problem.getZipFilePath() != null) {
            // 마이그레이션용: 파일 경로에서 읽기
            Path zipPath = Paths.get(problem.getZipFilePath());
            if (!zipPath.isAbsolute()) {
                zipPath = Paths.get(System.getProperty("user.dir")).resolve(zipPath).normalize();
            }
            if (Files.exists(zipPath)) {
                zipFileData = Files.readAllBytes(zipPath);
            }
        }

        if (zipFileData == null || zipFileData.length == 0) {
            throw new IllegalArgumentException("문제의 ZIP 파일을 찾을 수 없습니다.");
        }

        // byte[]를 MultipartFile로 변환
        MultipartFile zipFile = new ByteArrayMultipartFile(zipFileData, "problem.zip");

        // ZIP 파일 파싱
        ProblemParseResponse response = parseZipFile(zipFile);
        
        // Problem 엔티티에서 tags 가져오기 (현재는 엔티티에 필드가 없으므로 null)
        // TODO: Problem 엔티티에 tags 필드 추가 시 구현
        // String tags = problem.getTags();
        // response.setTags(tags);
        
        return response;
    }
    /**
     * 문제 수정
     * 안전한 순서: 업로드 → DB 갱신 → 기존 문제 삭제
     * 
     * @param problemId 문제 ID
     * @param request 수정 요청 데이터
     * @param instructorId 강사 ID
     * @throws IOException 파일 처리 오류
     */
    @Transactional
    public void updateProblem(Long problemId, ProblemUpdateRequest request, Long instructorId) throws IOException {
        // 1. 기존 문제 조회 및 기존 DOMjudge ID 저장
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다: " + problemId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 체크: 문제 생성자이거나 시스템 관리자인지 확인
        boolean isAuthorized = (problem.getCreatedBy() != null && problem.getCreatedBy().getId().equals(instructorId)) ||
                user.getRole() == User.Role.SUPER_ADMIN;
        
        if (!isAuthorized) {
            throw new IllegalArgumentException("이 문제를 수정할 권한이 없습니다.");
        }

        // 기존 DOMjudge 문제 ID 저장 (나중에 삭제하기 위해)
        String oldDomjudgeProblemId = problem.getDomjudgeProblemId();

        // metadataUpdated 플래그 확인 (프론트엔드에서 명시적으로 전송)
        boolean isOnlyMetadataChange = false;
        if (request.getMetadataUpdated() != null) {
            // "true" 문자열이면 메타데이터만 업데이트 (얕은 업데이트)
            isOnlyMetadataChange = "true".equalsIgnoreCase(request.getMetadataUpdated().trim());
        } else {
            // 플래그가 없으면 기존 로직 사용 (하위 호환성)
            isOnlyMetadataChange = isOnlyDifficultyOrTagsChange(problem, request);
        }
        
        // 테스트케이스 수신 여부 로그
        List<MultipartFile> testcaseFiles = request.getTestcaseFiles();
        int testcaseCount = (testcaseFiles != null) ? testcaseFiles.size() : 0;
        if (testcaseFiles != null && !testcaseFiles.isEmpty()) {
            log.info("수신된 테스트케이스 파일 수: {}, 파일명 목록:", testcaseCount);
            for (int i = 0; i < testcaseFiles.size(); i++) {
                MultipartFile file = testcaseFiles.get(i);
                log.info("  testcase[{}]: name={}, size={} bytes", 
                        i, 
                        file.getOriginalFilename() != null ? file.getOriginalFilename() : "null",
                        file.getSize());
            }
        } else {
            log.warn("테스트케이스 파일이 전송되지 않았습니다. testcaseFiles={}", 
                    testcaseFiles == null ? "null" : "empty");
        }
        
        log.info("문제 수정 요청 분석: ID={}, metadataUpdated={}, isOnlyMetadataChange={}, title={}, description 길이={}, timeLimit={}, memoryLimit={}, difficulty={}, tags={}, testcaseCount={}", 
                problemId, request.getMetadataUpdated(), isOnlyMetadataChange, 
                request.getTitle(), 
                request.getDescription() != null ? request.getDescription().length() : 0,
                request.getTimeLimit(), request.getMemoryLimit(), 
                request.getDifficulty(), request.getTags() != null ? "있음" : "없음",
                testcaseCount);

        byte[] newZipFileData = null;
        String newDomjudgeProblemId = null;

        try {
            // 난이도/태그만 변경되는 경우 DOMjudge 작업 건너뛰기
            if (isOnlyMetadataChange) {
                log.info("난이도/태그만 변경됨: ID={}, DOMjudge 작업 건너뛰기", problemId);
                // DOMjudge ID는 그대로 유지
                newDomjudgeProblemId = oldDomjudgeProblemId;
                newZipFileData = problem.getZipFileData(); // 기존 ZIP 파일 데이터 유지
            } else {
                // 1) 기존 Domjudge ID 기억 (이미 oldDomjudgeProblemId에 저장됨)
                if (oldDomjudgeProblemId == null || oldDomjudgeProblemId.isEmpty()) {
                    throw new IllegalArgumentException("기존 Domjudge 문제 ID가 없습니다. 문제를 먼저 생성해야 합니다.");
                }

                // 2) 문제가 연결된 모든 Contest(Section) 찾기 (업데이트 전에 미리 찾기)
                List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                        assignmentProblemRepository.findByProblemId(problemId);
                
                Set<Long> contestIds = new java.util.HashSet<>();
                for (com.project.handongjudge.assignment.entity.AssignmentProblem ap : assignmentProblems) {
                    if (ap.getAssignment() != null && ap.getAssignment().getSection() != null) {
                        contestIds.add(ap.getAssignment().getSection().getId());
                    }
                }

                log.info("문제가 연결된 Contest 수: {}", contestIds.size());

                // 고유한 externalid 생성: "과제ID-문제ID-타임스탬프" 형식
                Long assignmentId = null;
                if (!assignmentProblems.isEmpty() && assignmentProblems.get(0).getAssignment() != null) {
                    assignmentId = assignmentProblems.get(0).getAssignment().getId();
                }
                String externalId;
                if (assignmentId != null) {
                    externalId = assignmentId + "-" + problemId + "-" + System.currentTimeMillis();
                } else {
                    // 과제에 연결되지 않은 경우 문제ID와 타임스탬프만 사용
                    externalId = "problem-" + problemId + "-" + System.currentTimeMillis();
                }
                log.info("생성된 externalid: {}", externalId);

                // 3) ZIP 파일 준비 - 항상 필드 기반으로 새로 생성
                // ZIP 파일은 프론트엔드에서 파싱용으로만 사용하고,
                // 실제 API는 항상 필드 기반으로 ZIP을 생성합니다.
                // 이를 통해 ZIP 내부 테스트케이스 수정/삭제가 가능합니다.
                
                String description = request.getDescription();
                if (description == null || description.isEmpty()) {
                    description = problem.getDescription(); // 기존 description 사용
                }
                
                String timeLimitStr = request.getTimeLimit() != null ? 
                    String.valueOf(request.getTimeLimit()) : 
                    (problem.getTimeLimit() != null ? String.valueOf(problem.getTimeLimit()) : "1");
                
                String memoryLimitStr = request.getMemoryLimit() != null ? 
                    String.valueOf(request.getMemoryLimit()) : 
                    (problem.getMemoryLimit() != null ? String.valueOf(problem.getMemoryLimit()) : "256");
                
                // DOMjudge 형식의 ZIP 파일 생성
                byte[] zipBytes = createDomjudgeZip(
                        request.getTitle() != null ? request.getTitle() : problem.getTitle(),
                        description,
                        timeLimitStr,
                        memoryLimitStr,
                        request.getTestcaseFiles(),
                        externalId
                );

                // MultipartFile로 변환
                String fileName = sanitizeFilename(request.getTitle() != null ? request.getTitle() : problem.getTitle());
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "problem_" + problemId;
                }
                fileName = fileName + ".zip";
                
                MultipartFile zipFileToUpload = createMultipartFile(zipBytes, fileName);
                newZipFileData = saveProblemZipToDatabase(zipFileToUpload);

                // 4) Domjudge에 새 문제로 업로드 (새 ID 생성)
                log.info("Domjudge에 새 문제 업로드 시작");
                newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFileToUpload);
                log.info("Domjudge에 새 문제 업로드 완료: newProblemId={}", newDomjudgeProblemId);

                // 5) 새 문제 업로드 성공 후 기존 문제 삭제 (실패해도 치명적이지 않음)
                if (oldDomjudgeProblemId != null && !oldDomjudgeProblemId.equals(newDomjudgeProblemId)) {
                    try {
                        log.info("기존 Domjudge 문제 삭제 시도: oldProblemId={}", oldDomjudgeProblemId);
                        domjudgeService.deleteProblemFromDomjudge(oldDomjudgeProblemId);
                        log.info("기존 DOMjudge 문제 삭제 완료: oldProblemId={}", oldDomjudgeProblemId);
                    } catch (Exception e) {
                        // 삭제 실패는 로그만 남기고 계속 진행 (이미 새 문제는 업로드되었으므로)
                        log.warn("기존 DOMjudge 문제 삭제 실패 (무시 가능, 나중에 수동 정리 가능): oldProblemId={}, error={}", 
                                oldDomjudgeProblemId, e.getMessage());
                    }
                }

                // 6) 연결된 모든 Contest에 새 문제 재링크
                for (Long contestId : contestIds) {
                    try {
                        log.info("Contest에 새 문제 재링크: contestId={}, newProblemId={}", contestId, newDomjudgeProblemId);
                        // 기존 문제 제거
                        try {
                            domjudgeService.removeProblemFromContest(contestId, oldDomjudgeProblemId);
                        } catch (Exception e) {
                            log.warn("기존 문제 Contest에서 제거 실패 (이미 제거되었을 수 있음): contestId={}, oldProblemId={}, error={}", 
                                    contestId, oldDomjudgeProblemId, e.getMessage());
                        }
                        // 새 문제 추가
                        domjudgeService.addProblemToContest(contestId, newDomjudgeProblemId);
                        log.info("Contest에 새 문제 재링크 완료: contestId={}, newProblemId={}", contestId, newDomjudgeProblemId);
                    } catch (Exception e) {
                        log.error("Contest에 새 문제 재링크 실패: contestId={}, newProblemId={}, error={}", 
                                contestId, newDomjudgeProblemId, e.getMessage(), e);
                        // 재링크 실패는 로그만 남기고 계속 진행
                    }
                }
            }

            // 3. DB에 새 정보 저장 (트랜잭션 보장)
            // 메타데이터만 업데이트하는 경우 description, timeLimit, memoryLimit은 null로 전달하여 기존 값 유지
            String descriptionToUpdate = isOnlyMetadataChange ? null : request.getDescription();
            Double timeLimitToUpdate = isOnlyMetadataChange ? null : request.getTimeLimit();
            Integer memoryLimitToUpdate = isOnlyMetadataChange ? null : request.getMemoryLimit();
            
            updateProblemFields(problem, request.getTitle(), descriptionToUpdate,
                    timeLimitToUpdate, memoryLimitToUpdate,
                    request.getDifficulty(), request.getTags(),
                    newDomjudgeProblemId, newZipFileData);

            problemRepository.save(problem);
            
            if (isOnlyMetadataChange) {
                log.info("문제 수정 완료 (난이도/태그만): ID={}, Title={}, DOMjudge 작업 없음", problemId, request.getTitle());
            } else {
                log.info("문제 수정 완료: ID={}, Title={}, 새 DOMjudge ID={}", problemId, request.getTitle(), newDomjudgeProblemId);
            }

        } catch (Exception e) {
            // 업로드나 DB 저장 실패 시 롤백
            log.error("문제 수정 실패: ID={}, error={}", problemId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 난이도/태그만 변경되는지 확인
     * @param problem 기존 문제
     * @param request 수정 요청
     * @return 난이도/태그만 변경되는 경우 true
     */
    private boolean isOnlyDifficultyOrTagsChange(Problem problem, ProblemUpdateRequest request) {
        // 새 ZIP 파일이 있으면 DOMjudge 작업 필요
        if (request.getNewZipFile() != null && !request.getNewZipFile().isEmpty()) {
            log.debug("새 ZIP 파일이 제공됨: DOMjudge 작업 필요");
            return false;
        }

        // 제목이 실제로 변경되었는지 확인 (null이 아니고 실제로 다를 때만)
        if (request.getTitle() != null && problem.getTitle() != null 
                && !request.getTitle().trim().equals(problem.getTitle().trim())) {
            log.debug("제목이 변경됨: request='{}', problem='{}'", request.getTitle(), problem.getTitle());
            return false;
        }

        // 설명이 실제로 변경되었는지 확인
        String requestDesc = request.getDescription() != null ? request.getDescription().trim() : "";
        String problemDesc = problem.getDescription() != null ? problem.getDescription().trim() : "";
        if (!requestDesc.equals(problemDesc)) {
            log.debug("설명이 변경됨: request 길이={}, problem 길이={}", requestDesc.length(), problemDesc.length());
            return false;
        }

        // 시간 제한이 실제로 변경되었는지 확인
        if (request.getTimeLimit() != null && problem.getTimeLimit() != null
                && !request.getTimeLimit().equals(problem.getTimeLimit())) {
            log.debug("시간 제한이 변경됨: request={}, problem={}", request.getTimeLimit(), problem.getTimeLimit());
            return false;
        }

        // 메모리 제한이 실제로 변경되었는지 확인
        if (request.getMemoryLimit() != null && problem.getMemoryLimit() != null
                && !request.getMemoryLimit().equals(problem.getMemoryLimit())) {
            log.debug("메모리 제한이 변경됨: request={}, problem={}", request.getMemoryLimit(), problem.getMemoryLimit());
            return false;
        }

        // 위 항목들이 모두 변경되지 않았고, 난이도나 태그만 변경된 경우
        boolean difficultyChanged = request.getDifficulty() != null 
                && !request.getDifficulty().equals(problem.getDifficulty());
        
        boolean tagsChanged = request.getTags() != null && request.getTags().trim().length() > 0;

        if (difficultyChanged || tagsChanged) {
            log.debug("난이도/태그만 변경됨: difficultyChanged={}, tagsChanged={}", difficultyChanged, tagsChanged);
        }

        return difficultyChanged || tagsChanged;
    }

    /**
     * Problem 필드 업데이트 헬퍼 메서드
     */
    private void updateProblemFields(Problem problem, String title, String description,
                                     Double timeLimit, Integer memoryLimit,
                                     String difficulty, String tags,
                                     String domjudgeProblemId, byte[] zipFileData) {
        try {
            if (title != null) {
                java.lang.reflect.Field titleField = Problem.class.getDeclaredField("title");
                titleField.setAccessible(true);
                titleField.set(problem, title);
            }

            if (description != null) {
                java.lang.reflect.Field descField = Problem.class.getDeclaredField("description");
                descField.setAccessible(true);
                descField.set(problem, description);
            }

            if (timeLimit != null) {
                java.lang.reflect.Field timeLimitField = Problem.class.getDeclaredField("timeLimit");
                timeLimitField.setAccessible(true);
                timeLimitField.set(problem, timeLimit);
            }

            if (memoryLimit != null) {
                java.lang.reflect.Field memoryLimitField = Problem.class.getDeclaredField("memoryLimit");
                memoryLimitField.setAccessible(true);
                memoryLimitField.set(problem, memoryLimit);
            }

            if (difficulty != null) {
                java.lang.reflect.Field difficultyField = Problem.class.getDeclaredField("difficulty");
                difficultyField.setAccessible(true);
                difficultyField.set(problem, difficulty);
            }

            // tags는 Problem 엔티티에 필드가 없을 수 있으므로 일단 주석 처리
            // TODO: Problem 엔티티에 tags 필드 추가 시 구현
            // if (tags != null) {
            //     java.lang.reflect.Field tagsField = Problem.class.getDeclaredField("tags");
            //     tagsField.setAccessible(true);
            //     tagsField.set(problem, tags);
            // }

            if (domjudgeProblemId != null) {
                java.lang.reflect.Field domjudgeIdField = Problem.class.getDeclaredField("domjudgeProblemId");
                domjudgeIdField.setAccessible(true);
                domjudgeIdField.set(problem, domjudgeProblemId);
            }

            if (zipFileData != null) {
                java.lang.reflect.Field zipDataField = Problem.class.getDeclaredField("zipFileData");
                zipDataField.setAccessible(true);
                zipDataField.set(problem, zipFileData);
            }

        } catch (Exception e) {
            throw new RuntimeException("문제 필드 업데이트 실패", e);
        }
    }

    /**
     * 기존 ZIP 파일을 수정하여 새 ZIP 생성
     */
    private byte[] createModifiedZip(byte[] existingZipData, String newDescription,
                                     Double timeLimit, Integer memoryLimit, String externalId,
                                     List<MultipartFile> newTestcaseFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(existingZipData));
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            ZipEntry entry;
            boolean descriptionReplaced = false;
            boolean limitsReplaced = false;

            // 기존 ZIP 엔트리 복사 (description과 limits는 수정)
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // problem_statement 폴더의 .md 파일 교체
                if (entryName.toLowerCase().contains("problem_statement") && entryName.endsWith(".md")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(newDescription.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    descriptionReplaced = true;
                }
                // domjudge-problem.ini 교체 (externalid 포함)
                else if (entryName.endsWith("domjudge-problem.ini") || entryName.endsWith("problem.ini")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    String iniContent = createIniContent(timeLimit, memoryLimit, externalId);
                    zos.write(iniContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    limitsReplaced = true;
                }
                // problem.yaml 파일 처리: externalid 제거
                else if (entryName.equals("problem.yaml") || entryName.endsWith("/problem.yaml")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    // 기존 YAML 내용 읽기
                    ByteArrayOutputStream yamlBuffer = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        yamlBuffer.write(buffer, 0, len);
                    }
                    String yamlContent = yamlBuffer.toString(StandardCharsets.UTF_8.name());
                    // externalid 줄 제거
                    String cleanedYaml = removeExternalIdFromYaml(yamlContent);
                    zos.write(cleanedYaml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                // 나머지 파일은 그대로 복사
                else {
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }

            // description 파일이 없었다면 새로 추가
            if (!descriptionReplaced && newDescription != null) {
                zos.putNextEntry(new ZipEntry("problem_statement/problem.md"));
                zos.write(newDescription.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // limits 파일이 없었다면 새로 추가
            if (!limitsReplaced && (timeLimit != null || memoryLimit != null || (externalId != null && !externalId.isEmpty()))) {
                zos.putNextEntry(new ZipEntry("domjudge-problem.ini"));
                String iniContent = createIniContent(timeLimit, memoryLimit, externalId);
                zos.write(iniContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // 새로운 테스트케이스 파일 추가
            if (newTestcaseFiles != null && !newTestcaseFiles.isEmpty()) {
                for (MultipartFile testcase : newTestcaseFiles) {
                    String filename = testcase.getOriginalFilename();
                    if (filename == null) continue;
                    
                    // data/sample/ 또는 data/secret/ 폴더에 배치
                    String folder = filename.contains("sample") ? "data/sample/" : "data/secret/";
                    zos.putNextEntry(new ZipEntry(folder + filename));
                    zos.write(testcase.getBytes());
                    zos.closeEntry();
                }
            }
        }

        return baos.toByteArray();
    }

    /**
     * INI 파일 내용 생성
     */
    private String createIniContent(Double timeLimit, Integer memoryLimit, String externalId) {
        StringBuilder sb = new StringBuilder();
        if (externalId != null && !externalId.isEmpty()) {
            sb.append("externalid=").append(externalId).append("\n");
        }
        if (timeLimit != null) {
            sb.append("timelimit=").append(timeLimit).append("\n");
        }
        if (memoryLimit != null) {
            sb.append("memorylimit=").append(memoryLimit).append("\n");
        }
        return sb.toString();
    }

    /**
     * YAML 내용에서 externalid 제거 (DOMjudge 업로드 시 충돌 방지)
     */
    private String removeExternalIdFromYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isEmpty()) {
            return yamlContent;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = yamlContent.split("\n");
        
        for (String line : lines) {
            // externalid로 시작하는 줄 제거 (대소문자 무관, 앞뒤 공백 무시)
            String trimmedLine = line.trim();
            if (trimmedLine.toLowerCase().startsWith("externalid:")) {
                log.debug("problem.yaml에서 externalid 제거: {}", trimmedLine);
                continue; // 이 줄을 건너뛰기
            }
            result.append(line).append("\n");
        }
        
        return result.toString();
    }

    /**
     * ZIP 파일에 externalid 설정 및 테스트케이스 추가 (기존 externalid 제거 후 새로 설정)
     */
    private MultipartFile setExternalIdInZipFile(MultipartFile zipFile, String externalId, List<MultipartFile> newTestcaseFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream());
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            ZipEntry entry;
            boolean iniFileFound = false;
            
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                if (entryName.equals("problem.yaml") || entryName.endsWith("/problem.yaml")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    // 기존 YAML 내용 읽기
                    ByteArrayOutputStream yamlBuffer = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        yamlBuffer.write(buffer, 0, len);
                    }
                    String yamlContent = yamlBuffer.toString(StandardCharsets.UTF_8.name());
                    
                    // externalid 제거 (YAML에서는 externalid 사용 안 함)
                    String cleanedYaml = removeExternalIdFromYaml(yamlContent);
                    zos.write(cleanedYaml.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                // domjudge-problem.ini 파일 처리: externalid 설정
                else if (entryName.endsWith("domjudge-problem.ini") || entryName.endsWith("problem.ini")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    // 기존 INI 내용 읽기
                    ByteArrayOutputStream iniBuffer = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        iniBuffer.write(buffer, 0, len);
                    }
                    String existingIniContent = iniBuffer.toString(StandardCharsets.UTF_8.name());
                    
                    // 기존 externalid 제거
                    String cleanedIni = removeExternalIdFromIni(existingIniContent);
                    
                    // 새 externalid 추가
                    StringBuilder newIniContent = new StringBuilder();
                    if (externalId != null && !externalId.isEmpty()) {
                        newIniContent.append("externalid=").append(externalId).append("\n");
                    }
                    newIniContent.append(cleanedIni);
                    
                    zos.write(newIniContent.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    iniFileFound = true;
                } else {
                    // 나머지 파일은 그대로 복사
                    zos.putNextEntry(new ZipEntry(entryName));
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
            
            // domjudge-problem.ini 파일이 없었다면 새로 생성
            if (!iniFileFound && externalId != null && !externalId.isEmpty()) {
                zos.putNextEntry(new ZipEntry("domjudge-problem.ini"));
                String iniContent = "externalid=" + externalId + "\n";
                zos.write(iniContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // 새로운 테스트케이스 파일 추가
            if (newTestcaseFiles != null && !newTestcaseFiles.isEmpty()) {
                for (MultipartFile testcase : newTestcaseFiles) {
                    String filename = testcase.getOriginalFilename();
                    if (filename == null) continue;
                    
                    // data/sample/ 또는 data/secret/ 폴더에 배치
                    String folder = filename.contains("sample") ? "data/sample/" : "data/secret/";
                    zos.putNextEntry(new ZipEntry(folder + filename));
                    zos.write(testcase.getBytes());
                    zos.closeEntry();
                }
            }
        }
        
        return createMultipartFile(baos.toByteArray(), zipFile.getOriginalFilename());
    }
    
    /**
     * INI 파일 내용에서 externalid 제거
     */
    private String removeExternalIdFromIni(String iniContent) {
        if (iniContent == null || iniContent.isEmpty()) {
            return iniContent;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = iniContent.split("\n");
        
        for (String line : lines) {
            // externalid로 시작하는 줄 제거 (대소문자 무관, 앞뒤 공백 무시)
            String trimmedLine = line.trim();
            if (trimmedLine.toLowerCase().startsWith("externalid=")) {
                log.debug("domjudge-problem.ini에서 externalid 제거: {}", trimmedLine);
                continue; // 이 줄을 건너뛰기
            }
            result.append(line).append("\n");
        }
        
        return result.toString();
    }

    /**
     * 파일명을 안전한 형식으로 변환 (ASCII 문자만 허용)
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return null;
        }
        
        // 한글 및 특수문자를 언더스코어로 변환
        String sanitized = filename
            .replaceAll("[^a-zA-Z0-9_-]", "_")  // 영문/숫자/하이픈/언더스코어만 허용
            .replaceAll("_{2,}", "_")            // 연속된 언더스코어를 하나로
            .replaceAll("^_|_$", "");            // 앞뒤 언더스코어 제거
        
        // 빈 문자열이면 null 반환
        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * byte[]를 MultipartFile로 변환
     */
    private MultipartFile createMultipartFile(byte[] content, String filename) {
        return new MultipartFile() {
            @Override
            public String getName() { return "file"; }

            @Override
            public String getOriginalFilename() { return filename; }

            @Override
            public String getContentType() { return "application/zip"; }

            @Override
            public boolean isEmpty() { return content.length == 0; }

            @Override
            public long getSize() { return content.length; }

            @Override
            public byte[] getBytes() { return content; }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                Files.write(dest.toPath(), content);
            }
        };
    }

    /**
     * 문제 삭제
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteProblem(Long problemId, Long instructorId) {
        // 문제 조회
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다: " + problemId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 확인: 문제를 만든 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = problem.getCreatedBy().getId().equals(instructorId) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 문제를 삭제할 권한이 없습니다");
        }

        // 문제가 과제에 연결되어 있는지 확인하고 자동 언링크
        List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByProblemId(problemId);
        
        if (!assignmentProblems.isEmpty()) {
            // 모든 과제에서 문제 자동 언링크
            assignmentProblemRepository.deleteAll(assignmentProblems);
        }

        // 문제가 문제집에 연결되어 있는지 확인하고 자동 언링크
        List<com.project.handongjudge.problem.entity.ProblemSetProblem> problemSetProblems = 
                problemSetProblemRepository.findByProblemId(problemId);
        
        if (!problemSetProblems.isEmpty()) {
            // 모든 문제집에서 문제 자동 언링크
            problemSetProblemRepository.deleteAll(problemSetProblems);
        }

        // 문제가 퀴즈에 연결되어 있는지 확인하고 자동 언링크
        List<com.project.handongjudge.quiz.entity.QuizProblem> quizProblems = 
                quizProblemRepository.findByProblemId(problemId);
        
        if (!quizProblems.isEmpty()) {
            // 모든 퀴즈에서 문제 자동 언링크
            quizProblemRepository.deleteAll(quizProblems);
        }

        // DOMjudge에서 문제 삭제는 하지 않음 (DOMjudge API에 문제 삭제 기능이 없을 수 있음)
        // 필요시 domjudgeService.deleteProblem(problem.getDomjudgeProblemId()) 호출

        // 데이터베이스에서 문제 삭제
        problemRepository.delete(problem);
    }

    /**
     * 문제가 사용되는 과제 목록 조회 (기존 메서드 - 하위 호환성 유지)
     */
    public List<ProblemAssignmentUsageDto> getAssignmentsByProblemId(Long problemId, Long instructorId) {
        ProblemUsageDto usage = getProblemUsage(problemId, instructorId);
        return usage.getAssignments().stream()
                .map(a -> ProblemAssignmentUsageDto.builder()
                        .assignmentId(a.getAssignmentId())
                        .assignmentTitle(a.getAssignmentTitle())
                        .assignmentNumber(a.getAssignmentNumber())
                        .assignmentStartDate(a.getAssignmentStartDate())
                        .assignmentEndDate(a.getAssignmentEndDate())
                        .sectionId(a.getSectionId())
                        .courseTitle(a.getCourseTitle())
                        .sectionNumber(a.getSectionNumber())
                        .year(a.getYear())
                        .semester(a.getSemester())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 문제 사용 현황 조회 (과제, 문제집, 퀴즈 포함)
     */
    public ProblemUsageDto getProblemUsage(Long problemId, Long instructorId) {
        // 문제 조회
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다: " + problemId));

        // 사용자 조회
        User user = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + instructorId));

        // 권한 확인: 문제를 만든 교수이거나 시스템 관리자인지 확인
        boolean isAuthorized = (problem.getCreatedBy() != null && problem.getCreatedBy().getId().equals(instructorId)) ||
                user.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthorized) {
            throw new IllegalArgumentException("해당 문제를 조회할 권한이 없습니다");
        }

        // 문제가 사용되는 과제 목록 조회
        List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByProblemId(problemId);

        List<ProblemUsageDto.AssignmentUsage> assignments = assignmentProblems.stream()
                .filter(ap -> {
                    // Assignment와 Section이 존재하는지 확인 (CASCADE로 삭제된 경우 필터링)
                    com.project.handongjudge.assignment.entity.Assignment assignment = ap.getAssignment();
                    if (assignment == null) return false;
                    
                    com.project.handongjudge.section.entity.Section section = assignment.getSection();
                    if (section == null) return false;
                    
                    com.project.handongjudge.course.entity.Course course = section.getCourse();
                    return course != null;
                })
                .map(ap -> {
                    com.project.handongjudge.assignment.entity.Assignment assignment = ap.getAssignment();
                    com.project.handongjudge.section.entity.Section section = assignment.getSection();
                    com.project.handongjudge.course.entity.Course course = section.getCourse();

                    return ProblemUsageDto.AssignmentUsage.builder()
                            .assignmentId(assignment.getId())
                            .assignmentTitle(assignment.getTitle())
                            .assignmentNumber(assignment.getAssignmentNumber())
                            .assignmentStartDate(assignment.getStartDate())
                            .assignmentEndDate(assignment.getEndDate())
                            .sectionId(section.getId())
                            .courseTitle(course.getTitle())
                            .sectionNumber(section.getSectionNumber())
                            .year(section.getYear())
                            .semester(section.getSemester())
                            .build();
                })
                .collect(Collectors.toList());

        // 문제가 사용되는 문제집 목록 조회
        List<com.project.handongjudge.problem.entity.ProblemSetProblem> problemSetProblems = 
                problemSetProblemRepository.findByProblemId(problemId);

        List<ProblemUsageDto.ProblemSetUsage> problemSets = problemSetProblems.stream()
                .filter(psp -> {
                    // ProblemSet이 존재하는지 확인
                    return psp.getProblemSet() != null;
                })
                .map(psp -> {
                    com.project.handongjudge.problem.entity.ProblemSet problemSet = psp.getProblemSet();
                    return ProblemUsageDto.ProblemSetUsage.builder()
                            .problemSetId(problemSet.getId())
                            .problemSetTitle(problemSet.getTitle())
                            .description(problemSet.getDescription())
                            .createdAt(problemSet.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        // 문제가 사용되는 퀴즈 목록 조회
        List<com.project.handongjudge.quiz.entity.QuizProblem> quizProblems = 
                quizProblemRepository.findByProblemId(problemId);

        List<ProblemUsageDto.QuizUsage> quizzes = quizProblems.stream()
                .filter(qp -> {
                    // Quiz와 Section이 존재하는지 확인
                    com.project.handongjudge.quiz.entity.Quiz quiz = qp.getQuiz();
                    if (quiz == null) return false;
                    
                    com.project.handongjudge.section.entity.Section section = quiz.getSection();
                    if (section == null) return false;
                    
                    com.project.handongjudge.course.entity.Course course = section.getCourse();
                    return course != null;
                })
                .map(qp -> {
                    com.project.handongjudge.quiz.entity.Quiz quiz = qp.getQuiz();
                    com.project.handongjudge.section.entity.Section section = quiz.getSection();
                    com.project.handongjudge.course.entity.Course course = section.getCourse();

                    return ProblemUsageDto.QuizUsage.builder()
                            .quizId(quiz.getId())
                            .quizTitle(quiz.getTitle())
                            .startTime(quiz.getStartTime())
                            .endTime(quiz.getEndTime())
                            .sectionId(section.getId())
                            .courseTitle(course.getTitle())
                            .sectionNumber(section.getSectionNumber())
                            .year(section.getYear())
                            .semester(section.getSemester())
                            .build();
                })
                .collect(Collectors.toList());

        return ProblemUsageDto.builder()
                .assignments(assignments)
                .problemSets(problemSets)
                .quizzes(quizzes)
                .build();
    }
}

