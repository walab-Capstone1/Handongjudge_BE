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
        // 문제가 사용되는 과제 개수 조회
        List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByProblemId(problem.getId());
        int assignmentCount = assignmentProblems.size();
        boolean isUsed = assignmentCount > 0;
        
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
        
        log.info("문제 수정 요청 분석: ID={}, metadataUpdated={}, isOnlyMetadataChange={}, title={}, description 길이={}, timeLimit={}, memoryLimit={}, difficulty={}, tags={}", 
                problemId, request.getMetadataUpdated(), isOnlyMetadataChange, 
                request.getTitle(), 
                request.getDescription() != null ? request.getDescription().length() : 0,
                request.getTimeLimit(), request.getMemoryLimit(), 
                request.getDifficulty(), request.getTags() != null ? "있음" : "없음");

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
                // 2. 새 ZIP 파일 준비 및 DOMjudge에 업로드 (기존 문제는 아직 유지)
                // 고유한 식별자를 추가하여 ID 충돌 방지
                String timestamp = String.valueOf(System.currentTimeMillis());
                
                if (request.getNewZipFile() != null && !request.getNewZipFile().isEmpty()) {
                    // 새 ZIP 파일이 제공된 경우 - 고유한 파일명으로 변경
                    String originalFileName = request.getNewZipFile().getOriginalFilename();
                    String baseFileName = originalFileName != null && originalFileName.endsWith(".zip") 
                            ? originalFileName.substring(0, originalFileName.length() - 4) 
                            : "problem";
                    String uniqueFileName = baseFileName + "_" + problemId + "_" + timestamp + ".zip";
                    MultipartFile uniqueZipFile = new RenamedMultipartFile(request.getNewZipFile(), uniqueFileName);
                    
                    log.info("고유 식별자가 추가된 ZIP 파일로 Domjudge 업로드: original={}, unique={}", 
                            originalFileName, uniqueFileName);
                    
                    // DOMjudge에 새 문제로 먼저 업로드 (고유한 이름으로)
                    newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(uniqueZipFile);
                    newZipFileData = saveProblemZipToDatabase(request.getNewZipFile()); // 원본 파일명으로 저장
                    
                    log.info("Domjudge에 새 문제 업로드 완료: newProblemId={}", newDomjudgeProblemId);
                } else {
                    // ZIP 파일 없이 정보만 수정하는 경우
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
                            request.getMemoryLimit(),
                            null  // externalid 제거
                    );

                    // 고유한 파일명으로 MultipartFile 생성
                    String uniqueFileName = "problem_" + problemId + "_" + timestamp + ".zip";
                    MultipartFile modifiedZipFile = createMultipartFile(modifiedZipBytes, uniqueFileName);

                    log.info("수정된 ZIP 파일로 Domjudge 업로드: fileName={}", uniqueFileName);

                    // DOMjudge에 새 문제로 먼저 업로드 (고유한 이름으로)
                    newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(modifiedZipFile);
                    newZipFileData = saveProblemZipToDatabase(modifiedZipFile);
                    
                    log.info("Domjudge에 새 문제 업로드 완료: newProblemId={}", newDomjudgeProblemId);
                }

                // 3. 새 문제 업로드 성공 후 기존 문제 삭제 (실패해도 치명적이지 않음)
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
                                     Double timeLimit, Integer memoryLimit, String externalId) throws IOException {
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

        // 문제가 과제에 연결되어 있는지 확인
        List<com.project.handongjudge.assignment.entity.AssignmentProblem> assignmentProblems = 
                assignmentProblemRepository.findByProblemId(problemId);
        
        if (!assignmentProblems.isEmpty()) {
            throw new IllegalArgumentException("과제에 연결된 문제는 삭제할 수 없습니다. 먼저 과제에서 문제를 제거해주세요.");
        }

        // DOMjudge에서 문제 삭제는 하지 않음 (DOMjudge API에 문제 삭제 기능이 없을 수 있음)
        // 필요시 domjudgeService.deleteProblem(problem.getDomjudgeProblemId()) 호출

        // 데이터베이스에서 문제 삭제
        problemRepository.delete(problem);
    }

    /**
     * 문제가 사용되는 과제 목록 조회
     */
    public List<ProblemAssignmentUsageDto> getAssignmentsByProblemId(Long problemId, Long instructorId) {
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

        return assignmentProblems.stream()
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

                    return ProblemAssignmentUsageDto.builder()
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
    }
}

