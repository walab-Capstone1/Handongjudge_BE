package com.project.handongjudge.problem.util;

import com.project.handongjudge.problem.dto.ProblemFileParseResult;
import com.project.handongjudge.problem.dto.TestCaseDto;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * HandongJudge 파싱 결과를 DOMjudge ZIP 형식으로 변환.
 */
@Slf4j
public class ProblemFileToDomjudgeConverter {

    public static byte[] toDomjudgeZip(ProblemFileParseResult result) throws java.io.IOException {
        return toDomjudgeZip(result, "problem-" + System.currentTimeMillis());
    }

    public static byte[] toDomjudgeZip(ProblemFileParseResult result, String externalId) throws java.io.IOException {
        String title = result.getTitle() != null ? result.getTitle() : "Untitled";
        String description = result.getDescription() != null ? result.getDescription() : "";
        String timeLimit = result.getTimeLimit() != null ? String.valueOf(result.getTimeLimit()) : "1";
        String memoryLimit = result.getMemoryLimit() != null ? String.valueOf(result.getMemoryLimit()) : "256";
        List<TestCaseDto> testcases = result.getTestcases();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // problem.yaml
            zos.putNextEntry(new ZipEntry("problem.yaml"));
            zos.write(createProblemYaml(title, timeLimit, memoryLimit).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // problem_statement/problem.md
            zos.putNextEntry(new ZipEntry("problem_statement/problem.md"));
            zos.write(description.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // domjudge-problem.ini
            zos.putNextEntry(new ZipEntry("domjudge-problem.ini"));
            zos.write(createDomjudgeIni(timeLimit, memoryLimit, externalId).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // testcases
            if (testcases != null && !testcases.isEmpty()) {
                for (TestCaseDto tc : testcases) {
                    String base = tc.getName() != null ? tc.getName() : "test";
                    boolean isSample = "sample".equalsIgnoreCase(tc.getType());
                    String folder = isSample ? "data/sample/" : "data/secret/";

                    if (tc.getInput() != null) {
                        zos.putNextEntry(new ZipEntry(folder + base + ".in"));
                        zos.write(tc.getInput().getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                    if (tc.getOutput() != null) {
                        zos.putNextEntry(new ZipEntry(folder + base + ".ans"));
                        zos.write(tc.getOutput().getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    private static String createProblemYaml(String title, String timeLimit, String memoryLimit) {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(title).append("\n");
        sb.append("author: HandongJudge\n");
        sb.append("source: HandongJudge\n");
        sb.append("limits:\n");
        sb.append("  time: ").append(timeLimit).append("\n");
        sb.append("  memory: ").append(memoryLimit).append("\n");
        return sb.toString();
    }

    private static String createDomjudgeIni(String timeLimit, String memoryLimit, String externalId) {
        StringBuilder sb = new StringBuilder();
        if (externalId != null && !externalId.isEmpty()) {
            sb.append("externalid=").append(externalId).append("\n");
        }
        sb.append("timelimit=").append(timeLimit).append("\n");
        sb.append("memorylimit=").append(memoryLimit).append("\n");
        return sb.toString();
    }
}
