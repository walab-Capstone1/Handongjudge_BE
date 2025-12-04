package com.project.handongjudge.problem.service;

import com.project.handongjudge.domjudge.service.DomjudgeService;
import com.project.handongjudge.problem.dto.ProblemCreateRequest;
import com.project.handongjudge.problem.dto.ProblemResponse;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        MultipartFile descriptionFile = request.getDescriptionFile();
        MultipartFile zipFile = request.getZipFile();

        // Description 우선순위 처리
        String description = "";

        // 1. 별도 업로드한 description 파일이 최우선
        if (descriptionFile != null && !descriptionFile.isEmpty()) {
            description = new String(descriptionFile.getBytes(), "UTF-8");
            log.info("별도 업로드 description 파일 사용: {} ({} bytes)",
                    descriptionFile.getOriginalFilename(), description.length());
        }
        // 2. ZIP 파일 내부에서 description 추출 시도
        else if (zipFile != null && !zipFile.isEmpty()) {
            try {
                description = ProblemFileUtil.extractDescriptionFromZip(zipFile);
                if (!description.isEmpty()) {
                    log.info("ZIP 파일에서 description 추출 성공 ({} bytes)", description.length());
                } else {
                    log.info("ZIP 파일에 description 파일이 없습니다.");
                }
            } catch (IOException e) {
                log.error("ZIP 파일에서 description 추출 실패: {}", e.getMessage());
                // ZIP에서 추출 실패해도 문제 생성은 계속 진행
                description = "";
            }
        } else {
            log.info("description 파일과 ZIP 파일이 모두 제공되지 않았습니다.");
        }

        // *** 새로 추가: ZIP 파일에서 제한 설정 추출 ***
        Double timeLimit = null;
        Integer memoryLimit = null;
        String specialRun = null;
        String specialCompare = null;

        if (zipFile != null && !zipFile.isEmpty()) {
            try {
                Map<String, Object> limits = ProblemFileUtil.extractProblemLimits(zipFile);

                timeLimit = (Double) limits.get("timeLimit");
                memoryLimit = (Integer) limits.get("memoryLimit");
                specialRun = (String) limits.get("specialRun");
                specialCompare = (String) limits.get("specialCompare");

                log.info("ZIP에서 추출된 제한 설정 - Time: {}초, Memory: {}MB, SpecialRun: {}, SpecialCompare: {}",
                        timeLimit, memoryLimit, specialRun, specialCompare);

            } catch (IOException e) {
                log.error("ZIP 파일에서 제한 설정 추출 실패: {}", e.getMessage());
                // 제한 설정 추출 실패해도 문제 생성은 계속 진행
            }
        }

        // DOMjudge에 문제 업로드
        String domjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFile);

// ✨ ZIP 파일 저장
        String savedZipPath = saveProblemZip(zipFile, domjudgeProblemId);

// Instructor 조회
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        Problem problem = Problem.builder()
                .title(title)
                .description(description)
                .domjudgeProblemId(domjudgeProblemId)
                .timeLimit(timeLimit)
                .memoryLimit(memoryLimit)
                .createdAt(LocalDateTime.now())
                .createdBy(instructor)
                .zipFilePath(savedZipPath)  // ✨ ZIP 파일 경로 저장
                .build();
        problemRepository.save(problem);
        log.info("문제 생성 완료: ID={}, Title={}, Description Length={}, Time Limit={}초, Memory Limit={}MB",
                problem.getId(), title, description.length(), timeLimit, memoryLimit);

        return problem.getId();
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
        // instructorId로 필터링하여 해당 instructor가 만든 문제만 조회
        List<Problem> problems = problemRepository.findByCreatedBy_Id(instructorId);
        return problems.stream()
                .map(this::convertToProblemResponse)
                .collect(Collectors.toList());
    }

    /**
     * 문제 ZIP 파일을 저장
     */
    private String saveProblemZip(MultipartFile zipFile, String domjudgeProblemId) throws IOException {
        // 저장 디렉토리 생성
        Path storageDir = Paths.get(zipStoragePath);
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        // 파일명: domjudgeProblemId.zip
        String filename = domjudgeProblemId + ".zip";
        Path filePath = storageDir.resolve(filename);

        // 파일 저장
        Files.copy(zipFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return filePath.toString();
    }

    /**
     * 문제 복사 (권한 체크 포함)
     */
    // Handongjudge_BE/src/main/java/com/project/handongjudge/problem/service/ProblemService.java

    public Long copyProblem(Long sourceProblemId, String newTitle, Long instructorId) throws IOException {
        // 원본 문제 조회
        Problem sourceProblem = problemRepository.findById(sourceProblemId)
                .orElseThrow(() -> new IllegalArgumentException("원본 문제를 찾을 수 없습니다: " + sourceProblemId));

        // 권한 체크
        if (sourceProblem.getCreatedBy() == null) {
            throw new IllegalArgumentException("원본 문제의 생성자 정보가 없어 복사할 수 없습니다.");
        }
        if (!sourceProblem.getCreatedBy().getId().equals(instructorId)) {
            throw new IllegalArgumentException("이 문제를 복사할 권한이 없습니다.");
        }

        // 저장된 ZIP 파일 확인 (상대 경로를 절대 경로로 변환)
        if (sourceProblem.getZipFilePath() == null) {
            throw new IllegalArgumentException("원본 문제의 ZIP 파일 경로가 없습니다. 복사할 수 없습니다.");
        }

        // 상대 경로를 절대 경로로 변환
        Path zipPath = Paths.get(sourceProblem.getZipFilePath());
        if (!zipPath.isAbsolute()) {
            // 상대 경로인 경우 현재 작업 디렉토리 기준으로 절대 경로로 변환
            zipPath = Paths.get(System.getProperty("user.dir")).resolve(zipPath).normalize();
        }

        if (!Files.exists(zipPath)) {
            throw new IllegalArgumentException(
                    "원본 문제의 ZIP 파일을 찾을 수 없습니다. 경로: " + zipPath + " (원본: " + sourceProblem.getZipFilePath() + ")"
            );
        }

        // 저장된 ZIP 파일을 MultipartFile로 변환
        MultipartFile originalZipFile = new PathMultipartFile(zipPath);

        // ✅ 새로운 고유한 ZIP 파일 이름 생성 (타임스탬프 기반)
        String timestamp = String.valueOf(System.currentTimeMillis());
        String newZipFileName = "copy_" + timestamp + ".zip";
        MultipartFile zipFileWithNewName = new RenamedMultipartFile(originalZipFile, newZipFileName);

        // 새 문제 제목
        String problemTitle = (newTitle != null && !newTitle.trim().isEmpty())
                ? newTitle
                : sourceProblem.getTitle() + " (복사본)";

        // ✅ DOMJudge에 새 문제로 업로드 (새로운 파일 이름으로)
        String newDomjudgeProblemId = domjudgeService.uploadProblemToDomjudge(zipFileWithNewName);

        // 새 ZIP 파일 저장
        String newZipPath = saveProblemZip(zipFileWithNewName, newDomjudgeProblemId);

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
                .zipFilePath(newZipPath)
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
}

