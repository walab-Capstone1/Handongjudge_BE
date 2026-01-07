package com.project.handongjudge.problem.util;

import com.project.handongjudge.problem.dto.TestCaseInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class ProblemFileUtil {

    // 지원하는 파일 확장자에 .html 추가 (우선순위: tex -> md -> html -> txt)
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(".tex", ".md", ".html", ".txt");

    // problem_statement 폴더명 (대소문자 무관)
    private static final String PROBLEM_STATEMENT_FOLDER = "problem_statement";

    /**
     * ZIP 파일에서 problem_statement 폴더의 description 파일을 추출합니다.
     * 우선순위: .tex -> .md -> .html -> .txt
     * problem_statement 폴더가 없으면 루트나 다른 경로에서도 찾기
     */
    public static String extractDescriptionFromZip(MultipartFile zipFile) throws IOException {
        if (zipFile == null || zipFile.isEmpty()) {
            log.debug("ZIP 파일이 null이거나 비어있습니다.");
            return "";
        }

        log.info("ZIP 파일에서 description 추출 시작: {}", zipFile.getOriginalFilename());

        byte[] zipBytes = zipFile.getBytes();
        String bestMatch = null;
        String bestContent = null;
        int bestPriority = Integer.MAX_VALUE;
        boolean foundInProblemStatement = false;

        // 첫 번째 패스: problem_statement 폴더에서 찾기
        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8.name())) {

            ZipArchiveEntry entry;
            while ((entry = zipInputStream.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                log.debug("ZIP 엔트리 확인: {}", entryName);

                // problem_statement 폴더 내의 파일인지 확인
                if (isProblemStatementFile(entryName)) {
                    foundInProblemStatement = true;
                    String extension = getFileExtension(entryName);
                    int priority = SUPPORTED_EXTENSIONS.indexOf(extension);

                    if (priority != -1 && priority < bestPriority) {
                        bestMatch = entryName;
                        bestPriority = priority;
                        bestContent = readZipEntryContent(zipInputStream);
                        log.info("우선순위 {} 파일 발견: {}", priority + 1, entryName);
                    }
                }
            }
        }

        // problem_statement에서 찾지 못한 경우, 루트나 다른 경로에서 찾기
        if (bestContent == null) {
            log.info("problem_statement 폴더에서 파일을 찾지 못했습니다. 다른 경로에서 검색합니다.");

            try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(
                    new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8.name())) {

                ZipArchiveEntry entry;
                while ((entry = zipInputStream.getNextZipEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String entryName = entry.getName();
                    String extension = getFileExtension(entryName);

                    // 지원하는 확장자이고, 숨김 파일이 아닌 경우
                    if (SUPPORTED_EXTENSIONS.contains(extension) &&
                            !entryName.contains("__MACOSX") &&
                            !entryName.contains(".DS_Store")) {

                        int priority = SUPPORTED_EXTENSIONS.indexOf(extension);
                        if (priority < bestPriority) {
                            bestMatch = entryName;
                            bestPriority = priority;
                            bestContent = readZipEntryContent(zipInputStream);
                            log.info("대체 경로에서 우선순위 {} 파일 발견: {}", priority + 1, entryName);
                        }
                    }
                }
            }
        }

        if (bestContent != null) {
            log.info("ZIP에서 description 추출 성공: {} ({} bytes)",
                    bestMatch, bestContent.length());
            return bestContent;
        } else {
            log.warn("ZIP 파일에서 description 파일을 찾을 수 없습니다.");
            return "";
        }
    }

    /**
     * ZIP 파일에서 문제 제한 설정을 추출합니다.
     * domjudge-problem.ini, problem.yaml, problem.json 등을 지원합니다.
     */
    public static Map<String, Object> extractProblemLimits(MultipartFile zipFile) throws IOException {
        Map<String, Object> limits = new HashMap<>();

        if (zipFile == null || zipFile.isEmpty()) {
            log.debug("ZIP 파일이 null이거나 비어있습니다.");
            return limits;
        }

        log.info("ZIP 파일에서 문제 제한 설정 추출 시작: {}", zipFile.getOriginalFilename());

        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipFile.getBytes()), StandardCharsets.UTF_8.name())) {

            ZipArchiveEntry entry;
            while ((entry = zipInputStream.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                String fileName = entryName.toLowerCase();

                // domjudge-problem.ini 파일 처리
                if (fileName.endsWith("domjudge-problem.ini") || fileName.endsWith("problem.ini")) {
                    limits.putAll(parseIniFile(zipInputStream));
                    log.info("INI 설정 파일에서 제한 설정 추출: {}", entryName);
                    break;
                }
                // problem.yaml 파일 처리 (간단한 파싱)
                else if (fileName.endsWith("problem.yaml") || fileName.endsWith("problem.yml")) {
                    limits.putAll(parseYamlFile(zipInputStream));
                    log.info("YAML 설정 파일에서 제한 설정 추출: {}", entryName);
                    break;
                }
            }

        } catch (IOException e) {
            log.error("ZIP 파일에서 제한 설정 추출 중 오류 발생: {}", e.getMessage(), e);
            // 에러가 발생해도 빈 맵 반환 (문제 생성은 계속 진행)
        }

        log.info("추출된 제한 설정: {}", limits);
        return limits;
    }

    /**
     * INI 파일 형식 파싱
     */
    private static Map<String, Object> parseIniFile(ZipArchiveInputStream zipInputStream) throws IOException {
        Map<String, Object> limits = new HashMap<>();
        Properties props = new Properties();
        props.load(zipInputStream);

        // 시간 제한 (초)
        String timeLimit = props.getProperty("timelimit");
        if (timeLimit != null && !timeLimit.trim().isEmpty()) {
            try {
                limits.put("timeLimit", Double.parseDouble(timeLimit.trim()));
            } catch (NumberFormatException e) {
                log.warn("Invalid timelimit value: {}", timeLimit);
            }
        }

        // 메모리 제한 (MB)
        String memoryLimit = props.getProperty("memorylimit");
        if (memoryLimit != null && !memoryLimit.trim().isEmpty()) {
            try {
                limits.put("memoryLimit", Integer.parseInt(memoryLimit.trim()));
            } catch (NumberFormatException e) {
                log.warn("Invalid memorylimit value: {}", memoryLimit);
            }
        }

        // 특별한 실행 방식
        String specialRun = props.getProperty("special_run");
        if (specialRun != null && !specialRun.trim().isEmpty()) {
            limits.put("specialRun", specialRun.trim());
        }

        // 특별한 비교 방식
        String specialCompare = props.getProperty("special_compare");
        if (specialCompare != null && !specialCompare.trim().isEmpty()) {
            limits.put("specialCompare", specialCompare.trim());
        }

        return limits;
    }

    /**
     * YAML 파일 형식 파싱 (간단한 버전)
     */
    /**
     * YAML 파일 형식 파싱 (실제 구조에 맞춘 버전)
     */
    private static Map<String, Object> parseYamlFile(ZipArchiveInputStream zipInputStream) throws IOException {
        Map<String, Object> limits = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipInputStream, StandardCharsets.UTF_8))) {

            String line;
            boolean inLimitsSection = false;

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                // 빈 줄이나 주석 무시
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                // limits 섹션 시작
                if (trimmedLine.equals("limits:")) {
                    inLimitsSection = true;
                    continue;
                }

                // limits 섹션 내부에서만 파싱
                if (inLimitsSection) {
                    // 다른 섹션이 시작되면 limits 섹션 종료 (들여쓰기 없는 경우)
                    if (trimmedLine.endsWith(":") && !line.startsWith(" ") && !line.startsWith("\t")) {
                        inLimitsSection = false;
                        continue;
                    }

                    // 들여쓰기된 라인만 처리 (limits 하위 항목)
                    if (line.startsWith("  ") || line.startsWith("\t")) {
                        // time 파싱
                        if (trimmedLine.startsWith("time:")) {
                            String value = extractYamlValue(trimmedLine);
                            try {
                                Double timeValue = Double.parseDouble(value);
                                limits.put("timeLimit", timeValue);
                                log.debug("YAML에서 시간 제한 추출: {}초", timeValue);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid time value in YAML: {}", value);
                            }
                        }

                        // memory 파싱
                        else if (trimmedLine.startsWith("memory:")) {
                            String value = extractYamlValue(trimmedLine);
                            try {
                                Integer memoryValue = Integer.parseInt(value);
                                limits.put("memoryLimit", memoryValue);
                                log.debug("YAML에서 메모리 제한 추출: {}MB", memoryValue);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid memory value in YAML: {}", value);
                            }
                        }

                        // code 파싱 (추가 정보)
                        else if (trimmedLine.startsWith("code:")) {
                            String value = extractYamlValue(trimmedLine);
                            try {
                                Integer codeLimit = Integer.parseInt(value);
                                limits.put("codeLimit", codeLimit);
                                log.debug("YAML에서 코드 크기 제한 추출: {}KB", codeLimit);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid code limit value in YAML: {}", value);
                            }
                        }

                        // output 파싱 (추가 정보)
                        else if (trimmedLine.startsWith("output:")) {
                            String value = extractYamlValue(trimmedLine);
                            try {
                                Integer outputLimit = Integer.parseInt(value);
                                limits.put("outputLimit", outputLimit);
                                log.debug("YAML에서 출력 크기 제한 추출: {}MB", outputLimit);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid output limit value in YAML: {}", value);
                            }
                        }
                    }
                }

                // limits 섹션 밖에서도 유용한 정보들 추출
                else {
                    // difficulty 파싱
                    if (trimmedLine.startsWith("difficulty:")) {
                        String value = extractYamlValue(trimmedLine);
                        try {
                            Integer difficulty = Integer.parseInt(value);
                            limits.put("difficulty", difficulty);
                            log.debug("YAML에서 난이도 추출: {}", difficulty);
                        } catch (NumberFormatException e) {
                            // 난이도가 문자열일 수도 있음
                            limits.put("difficulty", value);
                            log.debug("YAML에서 난이도 추출: {}", value);
                        }
                    }

                    // author 파싱
                    else if (trimmedLine.startsWith("author:")) {
                        String author = extractYamlValue(trimmedLine);
                        limits.put("author", author);
                        log.debug("YAML에서 작성자 추출: {}", author);
                    }

                    // source 파싱
                    else if (trimmedLine.startsWith("source:")) {
                        String source = extractYamlValue(trimmedLine);
                        limits.put("source", source);
                        log.debug("YAML에서 출처 추출: {}", source);
                    }
                }
            }
        }

        return limits;
    }

    /**
     * YAML 라인에서 값 추출 (개선된 버전)
     */
    private static String extractYamlValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) return "";

        String value = line.substring(colonIndex + 1).trim();

        // 따옴표 제거
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        return value;
    }
    /**
     * ZIP 엔트리가 problem_statement 폴더의 지원 파일인지 확인
     */
    private static boolean isProblemStatementFile(String entryName) {
        // 경로를 정규화 (슬래시 통일)
        String normalizedPath = entryName.replace('\\', '/');
        String[] pathParts = normalizedPath.split("/");

        // problem_statement 폴더가 경로에 포함되어 있는지 확인 (대소문자 무관)
        boolean inProblemStatementFolder = false;
        for (String part : pathParts) {
            if (PROBLEM_STATEMENT_FOLDER.equalsIgnoreCase(part)) {
                inProblemStatementFolder = true;
                break;
            }
        }

        if (!inProblemStatementFolder) {
            return false;
        }

        // 지원하는 확장자인지 확인
        String extension = getFileExtension(entryName);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    /**
     * 파일명에서 확장자 추출 (소문자)
     */
    private static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }

    /**
     * ZIP 엔트리의 내용을 UTF-8 문자열로 읽기 (개선된 버전)
     */
    private static String readZipEntryContent(ZipArchiveInputStream zipInputStream) throws IOException {
        StringBuilder content = new StringBuilder();

        // 바이트 배열로 먼저 읽기
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zipInputStream.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }

        byte[] bytes = baos.toByteArray();

        // UTF-8로 디코딩 시도
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // UTF-8 실패 시 다른 인코딩 시도
            try {
                return new String(bytes, "ISO-8859-1");
            } catch (Exception e2) {
                log.warn("인코딩 변환 실패, 기본 인코딩 사용");
                return new String(bytes);
            }
        }
    }

    /**
     * 파일 타입 결정 (확장자 기반)
     */
    public static String determineFileType(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unknown";
        }

        String extension = getFileExtension(filename.toLowerCase());
        switch (extension) {
            case ".tex":
                return "latex";
            case ".md":
                return "markdown";
            case ".txt":
                return "text";
            default:
                return "unknown";
        }
    }
    // ProblemFileUtil.java에 추가할 메서드들

    /**
     * ZIP 파일에서 문제 제목 추출 (problem.yaml의 name 필드 우선)
     */
    /**
     * ZIP 파일에서 문제 제목 추출 (problem.yaml의 name 필드 우선)
     */
    public static String extractTitleFromZip(MultipartFile zipFile) throws IOException {
        if (zipFile == null || zipFile.isEmpty()) {
            return null;
        }

        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipFile.getBytes()), StandardCharsets.UTF_8.name())) {

            ZipArchiveEntry entry;
            while ((entry = zipInputStream.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                String fileName = entryName.toLowerCase();

                // problem.yaml 파일에서 name 추출
                if (fileName.endsWith("problem.yaml") || fileName.endsWith("problem.yml")) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zipInputStream, StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmedLine = line.trim();
                            if (trimmedLine.startsWith("name:")) {
                                String title = extractYamlValue(trimmedLine);
                                if (title != null && !title.isEmpty()) {
                                    log.info("ZIP에서 제목 추출: {}", title);
                                    return title;
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("ZIP 파일에서 제목 추출 중 오류 발생: {}", e.getMessage(), e);
        }

        // problem.yaml에서 찾지 못한 경우 파일명에서 추출
        String filename = zipFile.getOriginalFilename();
        if (filename != null && filename.endsWith(".zip")) {
            return filename.substring(0, filename.length() - 4);
        }
        return filename;
    }

    /**
     * ZIP 파일에서 테스트케이스 추출
     * DOMjudge 형식: data/sample/*.in, data/sample/*.ans 또는 data/secret/*.in, data/secret/*.ans
     */
    /**
     * ZIP 파일에서 테스트케이스 추출 (개선된 버전)
     * 다양한 경로 구조 지원
     */
    public static List<TestCaseInfo> extractTestCasesFromZip(MultipartFile zipFile) throws IOException {
        List<TestCaseInfo> testCases = new ArrayList<>();

        if (zipFile == null || zipFile.isEmpty()) {
            return testCases;
        }

        log.info("ZIP 파일에서 테스트케이스 추출 시작: {}", zipFile.getOriginalFilename());

        // 입력/출력 파일을 임시로 저장할 맵
        Map<String, TestCaseInfo> testCaseMap = new HashMap<>();

        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipFile.getBytes()), StandardCharsets.UTF_8.name())) {

            ZipArchiveEntry entry;
            while ((entry = zipInputStream.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                String normalizedPath = entryName.replace('\\', '/');

                // 숨김 파일이나 시스템 파일 무시
                if (normalizedPath.contains("__MACOSX") ||
                        normalizedPath.contains(".DS_Store") ||
                        normalizedPath.contains(".git")) {
                    continue;
                }

                // data/sample/, data/secret/, 또는 루트의 .in/.ans 파일
                boolean isTestCaseFile = false;
                String type = "secret"; // 기본값
                String baseName = "";
                String extension = "";

                if (normalizedPath.startsWith("data/sample/")) {
                    isTestCaseFile = true;
                    type = "sample";
                } else if (normalizedPath.startsWith("data/secret/")) {
                    isTestCaseFile = true;
                    type = "secret";
                } else if (normalizedPath.endsWith(".in") ||
                        normalizedPath.endsWith(".ans") ||
                        normalizedPath.endsWith(".out")) {
                    // 루트나 다른 경로의 테스트케이스 파일
                    isTestCaseFile = true;
                    type = "secret"; // 경로가 없으면 secret로 간주
                }

                if (isTestCaseFile) {
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    int lastDotIndex = fileName.lastIndexOf('.');

                    if (lastDotIndex > 0) {
                        baseName = fileName.substring(0, lastDotIndex);
                        extension = fileName.substring(lastDotIndex + 1).toLowerCase();
                    } else {
                        baseName = fileName;
                    }

                    // 테스트케이스 키 생성 (타입_베이스명)
                    String testCaseKey = type + "_" + baseName;

                    TestCaseInfo testCase = testCaseMap.getOrDefault(testCaseKey,
                            TestCaseInfo.builder()
                                    .name(baseName)
                                    .type(type)
                                    .build());
                    testCaseMap.put(testCaseKey, testCase);

                    // 입력 파일 (.in)
                    if (extension.equals("in")) {
                        String input = readZipEntryContent(zipInputStream);
                        testCase.setInput(input);
                        log.debug("테스트케이스 입력 파일 발견: {} ({} bytes)", entryName, input.length());
                    }
                    // 출력 파일 (.ans, .out)
                    else if (extension.equals("ans") || extension.equals("out")) {
                        String output = readZipEntryContent(zipInputStream);
                        testCase.setOutput(output);
                        log.debug("테스트케이스 출력 파일 발견: {} ({} bytes)", entryName, output.length());
                    }
                }
            }
        } catch (IOException e) {
            log.error("ZIP 파일에서 테스트케이스 추출 중 오류 발생: {}", e.getMessage(), e);
        }

        // 맵을 리스트로 변환 (입력과 출력이 모두 있는 것만)
        for (TestCaseInfo testCase : testCaseMap.values()) {
            if (testCase.getInput() != null && testCase.getOutput() != null &&
                    !testCase.getInput().isEmpty() && !testCase.getOutput().isEmpty()) {
                testCases.add(testCase);
            }
        }

        log.info("추출된 테스트케이스 개수: {}", testCases.size());
        return testCases;
    }

}