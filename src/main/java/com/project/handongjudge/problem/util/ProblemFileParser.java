package com.project.handongjudge.problem.util;

import com.project.handongjudge.problem.dto.ProblemFileParseResult;
import com.project.handongjudge.problem.dto.TestCaseDto;
import com.project.handongjudge.problem.dto.BulkParseItemResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HandongJudge 포맷 ZIP 파서.
 * 구조: {title}/description.md, {title}/problem.ini, {title}/testcases/*.in, *.out
 */
@Slf4j
public class ProblemFileParser {

    private static final String DESCRIPTION_FILE = "description.md";
    private static final String PROBLEM_INI = "problem.ini";
    private static final String TESTCASES_DIR = "testcases/";

    /**
     * 여러 문제 폴더가 포함된 ZIP을 파싱하여 각 폴더별 결과를 반환.
     */
    public static List<BulkParseItemResult> parseBulkZip(MultipartFile zipFile) throws IOException {
        List<BulkParseItemResult> results = new ArrayList<>();
        if (zipFile == null || zipFile.isEmpty()) {
            return results;
        }

        byte[] zipBytes = zipFile.getBytes();
        Map<String, Map<String, byte[]>> folders = new LinkedHashMap<>();

        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8.name())) {

            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.contains("__MACOSX") || name.contains(".DS_Store")) continue;

                // 첫 번째 경로 세그먼트 = 폴더명
                int slash = name.indexOf('/');
                if (slash <= 0) continue;
                String folderName = name.substring(0, slash);
                String rest = name.substring(slash + 1);

                folders.computeIfAbsent(folderName, k -> new HashMap<>());
                byte[] content = readEntryContent(zis);
                folders.get(folderName).put(rest, content);
            }
        }

        for (Map.Entry<String, Map<String, byte[]>> e : folders.entrySet()) {
            BulkParseItemResult item = parseFolder(e.getKey(), e.getValue());
            results.add(item);
        }

        return results;
    }

    /**
     * 폴더 선택(webkitdirectory)으로 업로드된 파일 목록 파싱.
     * 경로 형식: {folderName}/description.md, {folderName}/problem.ini, {folderName}/testcases/1.in 등
     */
    public static BulkParseItemResult parseFolderFromFiles(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return BulkParseItemResult.builder()
                    .filename("")
                    .success(false)
                    .parseResult(null)
                    .validationErrors(Collections.singletonList("파일이 없습니다."))
                    .build();
        }
        Map<String, byte[]> fileMap = new HashMap<>();
        String folderName = null;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            String path = f.getOriginalFilename();
            if (path == null || path.isEmpty()) continue;
            path = path.replace('\\', '/');
            if (path.contains("__MACOSX") || path.contains(".DS_Store")) continue;
            int slash = path.indexOf('/');
            if (slash <= 0) {
                fileMap.put(path, f.getBytes());
                if (folderName == null) folderName = "";
            } else {
                if (folderName == null) folderName = path.substring(0, slash);
                String rest = path.substring(slash + 1);
                fileMap.put(rest, f.getBytes());
            }
        }
        if (folderName == null || folderName.isEmpty()) {
            folderName = "problem";
        }
        return parseFolder(folderName, fileMap);
    }

    /**
     * 단일 문제 폴더 파싱 (bulk ZIP 내 한 폴더).
     * 일부만 있어도 해당 부분 반환 (부분 파싱 허용).
     */
    private static BulkParseItemResult parseFolder(String folderName, Map<String, byte[]> files) {
        List<String> warnings = new ArrayList<>();

        // description.md
        String description = "";
        String inputFormat = "";
        String outputFormat = "";
        List<Map<String, String>> sampleInputs = new ArrayList<>();
        if (files.containsKey(DESCRIPTION_FILE)) {
            String rawDescription = new String(files.get(DESCRIPTION_FILE), StandardCharsets.UTF_8);
            DescriptionSectionParser.DescriptionSections sections = DescriptionSectionParser.parse(rawDescription);
            description = sections.getMain();
            inputFormat = sections.getInputFormat();
            outputFormat = sections.getOutputFormat();
            sampleInputs = sections.getSampleInputs();
        } else {
            warnings.add("description.md 없음");
        }

        // problem.ini
        Double timeLimit = 1.0;
        Integer memoryLimit = 256;
        if (files.containsKey(PROBLEM_INI)) {
            try {
                String ini = new String(files.get(PROBLEM_INI), StandardCharsets.UTF_8);
                String[] lines = ini.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim().toLowerCase();
                    String val = line.substring(eq + 1).trim();
                    if ("timelimit".equals(key)) {
                        timeLimit = Double.parseDouble(val);
                    } else if ("memorylimit".equals(key)) {
                        memoryLimit = Integer.parseInt(val);
                    }
                }
            } catch (Exception ex) {
                warnings.add("problem.ini 파싱 실패: " + ex.getMessage());
            }
        } else {
            warnings.add("problem.ini 없음");
        }

        // testcases/
        List<TestCaseDto> testcases = new ArrayList<>();
        Map<String, String> inFiles = new HashMap<>();
        Map<String, String> outFiles = new HashMap<>();

        String prefix = TESTCASES_DIR;
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            if (!f.getKey().startsWith(prefix)) continue;
            String sub = f.getKey().substring(prefix.length());
            if (sub.contains("/")) continue; // 하위 디렉터리 무시
            String content = new String(f.getValue(), StandardCharsets.UTF_8);
            String base = sub;
            int dot = sub.lastIndexOf('.');
            if (dot > 0) {
                base = sub.substring(0, dot);
                String ext = sub.substring(dot + 1).toLowerCase();
                if ("in".equals(ext)) {
                    inFiles.put(base, content);
                } else if ("out".equals(ext) || "ans".equals(ext)) {
                    outFiles.put(base, content);
                }
            }
        }

        for (String base : new HashSet<>(inFiles.keySet())) {
            if (outFiles.containsKey(base)) {
                testcases.add(TestCaseDto.builder()
                        .name(base)
                        .input(inFiles.get(base))
                        .output(outFiles.get(base))
                        .type("secret")
                        .build());
            }
        }
        if (testcases.isEmpty()) {
            warnings.add("유효한 테스트케이스(.in/.out 쌍) 없음");
        }

        // 일부만 있어도 해당 부분 반환
        ProblemFileParseResult parseResult = ProblemFileParseResult.builder()
                .title(folderName)
                .description(description)
                .inputFormat(inputFormat)
                .outputFormat(outputFormat)
                .sampleInputs(sampleInputs)
                .timeLimit(timeLimit)
                .memoryLimit(memoryLimit)
                .testcases(testcases)
                .build();

        return BulkParseItemResult.builder()
                .filename(folderName)
                .success(true)
                .parseResult(parseResult)
                .validationErrors(warnings)
                .build();
    }

    private static byte[] readEntryContent(ZipArchiveInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
