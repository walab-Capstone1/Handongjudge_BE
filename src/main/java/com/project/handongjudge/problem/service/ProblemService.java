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

    @Value("${problem.zip.storage.path:./problem-zips}")
    private String zipStoragePath;





    public Long createProblem(ProblemCreateRequest request, Long instructorId) throws IOException {
        String title = request.getTitle();
        MultipartFile zipFile = request.getZipFile();

        // 1. ZIP 파일이 제공된 경우 - 기존 로직 사용
        if (zipFile != null && !zipFile.isEmpty()) {
            return createProblemFromZip(request, instructorId, zipFile);
        }

        // 2. ZIP 파일이 없는 경우 - 새로 생성
        return createProblemFromForm(request, instructorId);
    }

    /**
     * 기존 ZIP 파일로 문제 생성
     */
    private Long createProblemFromZip(ProblemCreateRequest request, Long instructorId, MultipartFile zipFile) throws IOException {
        String description = ProblemFileUtil.extractDescriptionFromZip(zipFile);
        Map<String, Object> limits = ProblemFileUtil.extractProblemLimits(zipFile);

        // DOMjudge에 문제 업로드
        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFile);

        // ZIP 파일을 데이터베이스에 저장
        byte[] zipFileData = saveProblemZipToDatabase(zipFile);

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

        // DOMjudge 형식의 ZIP 파일 생성
        byte[] zipBytes = createDomjudgeZip(
                request.getTitle(),
                fullDescription,
                request.getTimeLimit(),
                request.getMemoryLimit(),
                request.getTestcaseFiles()
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
                                     String memoryLimit, List<MultipartFile> testcaseFiles) throws IOException {
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
            String ini = createProblemIni(timeLimit, memoryLimit);
            zos.write(ini.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 4. 테스트케이스 파일 추가
            if (testcaseFiles != null && !testcaseFiles.isEmpty()) {
                for (MultipartFile testcase : testcaseFiles) {
                    String filename = testcase.getOriginalFilename();
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
    private String createProblemIni(String timeLimit, String memoryLimit) {
        StringBuilder ini = new StringBuilder();

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
        return ProblemResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .memoryLimit(problem.getMemoryLimit())     // 새로 추가
                .timeLimit(problem.getTimeLimit())         // 새로 추가

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

        // 새로운 고유한 ZIP 파일 이름 생성
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newZipFileName = "copy_" + timestamp + ".zip";
        MultipartFile zipFileWithNewName = new RenamedMultipartFile(originalZipFile, newZipFileName);

        // 새 문제 제목
        String problemTitle = (newTitle != null && !newTitle.trim().isEmpty())
                ? newTitle
                : sourceProblem.getTitle() + " (복사본)";

        // DOMJudge에 새 문제로 업로드
        String newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFileWithNewName);

        // 새 ZIP 파일을 데이터베이스에 저장
        byte[] newZipFileData = saveProblemZipToDatabase(zipFileWithNewName);

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
     * 문제 수정
     */
    public void updateProblem(Long problemId, ProblemUpdateRequest request, Long instructorId) throws IOException {
        // 기존 문제 조회
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

        byte[] newZipFileData = null;
        String newDomjudgeProblemId = null;

        // 1. 새 ZIP 파일이 제공된 경우
        if (request.getNewZipFile() != null && !request.getNewZipFile().isEmpty()) {
            newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(request.getNewZipFile());
            newZipFileData = saveProblemZipToDatabase(request.getNewZipFile());
        }
        // 2. ZIP 파일 없이 정보만 수정
        else {
            // 기존 ZIP 파일 데이터 가져오기
            byte[] existingZipData = null;

            if (problem.getZipFileData() != null && problem.getZipFileData().length > 0) {
                existingZipData = problem.getZipFileData();
            } else if (problem.getZipFilePath() != null) {
                // 마이그레이션용: 파일 경로에서 읽기
                Path existingZipPath = Paths.get(problem.getZipFilePath());
                if (!existingZipPath.isAbsolute()) {
                    existingZipPath = Paths.get(System.getProperty("user.dir")).resolve(existingZipPath).normalize();
                }
                if (Files.exists(existingZipPath)) {
                    existingZipData = Files.readAllBytes(existingZipPath);
                }
            }

            if (existingZipData == null || existingZipData.length == 0) {
                throw new IllegalArgumentException("원본 ZIP 파일을 찾을 수 없습니다.");
            }

            // 기존 ZIP을 수정하여 새 ZIP 생성
            byte[] modifiedZipBytes = createModifiedZip(
                    existingZipData,
                    request.getDescription(),
                    request.getTimeLimit(),
                    request.getMemoryLimit()
            );

            // MultipartFile로 변환
            MultipartFile modifiedZipFile = createMultipartFile(modifiedZipBytes, "modified.zip");

            // DOMjudge에 업로드
            newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(modifiedZipFile);
            newZipFileData = saveProblemZipToDatabase(modifiedZipFile);
        }

        // 문제 정보 업데이트
        updateProblemFields(problem, request.getTitle(), request.getDescription(),
                request.getTimeLimit(), request.getMemoryLimit(),
                newDomjudgeProblemId, newZipFileData);

        problemRepository.save(problem);
        log.info("문제 수정 완료: ID={}, Title={}", problemId, request.getTitle());
    }

    /**
     * Problem 필드 업데이트 헬퍼 메서드
     */
    private void updateProblemFields(Problem problem, String title, String description,
                                     Double timeLimit, Integer memoryLimit,
                                     String domjudgeProblemId, byte[] zipFileData) {
        try {
            java.lang.reflect.Field titleField = Problem.class.getDeclaredField("title");
            titleField.setAccessible(true);
            titleField.set(problem, title);

            java.lang.reflect.Field descField = Problem.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(problem, description);

            java.lang.reflect.Field timeLimitField = Problem.class.getDeclaredField("timeLimit");
            timeLimitField.setAccessible(true);
            timeLimitField.set(problem, timeLimit);

            java.lang.reflect.Field memoryLimitField = Problem.class.getDeclaredField("memoryLimit");
            memoryLimitField.setAccessible(true);
            memoryLimitField.set(problem, memoryLimit);

            java.lang.reflect.Field domjudgeIdField = Problem.class.getDeclaredField("domjudgeProblemId");
            domjudgeIdField.setAccessible(true);
            domjudgeIdField.set(problem, domjudgeProblemId);

            java.lang.reflect.Field zipDataField = Problem.class.getDeclaredField("zipFileData");
            zipDataField.setAccessible(true);
            zipDataField.set(problem, zipFileData);

        } catch (Exception e) {
            throw new RuntimeException("문제 필드 업데이트 실패", e);
        }
    }

    /**
     * 기존 ZIP 파일을 수정하여 새 ZIP 생성
     */
    private byte[] createModifiedZip(byte[] existingZipData, String newDescription,
                                     Double timeLimit, Integer memoryLimit) throws IOException {
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
                // domjudge-problem.ini 교체
                else if (entryName.endsWith("domjudge-problem.ini") || entryName.endsWith("problem.ini")) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    String iniContent = createIniContent(timeLimit, memoryLimit);
                    zos.write(iniContent.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    limitsReplaced = true;
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
            if (!limitsReplaced && (timeLimit != null || memoryLimit != null)) {
                zos.putNextEntry(new ZipEntry("domjudge-problem.ini"));
                String iniContent = createIniContent(timeLimit, memoryLimit);
                zos.write(iniContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

    /**
     * INI 파일 내용 생성
     */
    private String createIniContent(Double timeLimit, Integer memoryLimit) {
        StringBuilder sb = new StringBuilder();
        if (timeLimit != null) {
            sb.append("timelimit=").append(timeLimit).append("\n");
        }
        if (memoryLimit != null) {
            sb.append("memorylimit=").append(memoryLimit).append("\n");
        }
        return sb.toString();
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
}

