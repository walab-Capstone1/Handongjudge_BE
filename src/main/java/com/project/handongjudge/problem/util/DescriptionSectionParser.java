package com.project.handongjudge.problem.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * description.md를 파싱하여 본문, 입력 형식, 출력 형식, 예제 입출력을 분리합니다.
 * 지원 패턴: ## 입력, ## 입력 형식, ## 출력, ## 출력 형식, ## 예제
 */
public final class DescriptionSectionParser {

    private static final Pattern INPUT_SECTION = Pattern.compile(
            "(?m)(?:^|\\n)\\s*##\\s*(?:입력\\s*형식|입력)\\s*[\\n\\r]");
    private static final Pattern OUTPUT_SECTION = Pattern.compile(
            "(?m)(?:^|\\n)\\s*##\\s*(?:출력\\s*형식|출력)\\s*[\\n\\r]");
    private static final Pattern EXAMPLE_SECTION = Pattern.compile(
            "(?m)(?:^|\\n)\\s*##\\s*예제\\s*[\\n\\r]");
    private static final Pattern EXAMPLE_INPUT_BLOCK = Pattern.compile(
            "###\\s*예제\\s*입력\\s*\\d+[\\s\\S]*?```\\s*\\n([\\s\\S]*?)```");
    private static final Pattern EXAMPLE_OUTPUT_BLOCK = Pattern.compile(
            "###\\s*예제\\s*출력\\s*\\d+[\\s\\S]*?```\\s*\\n([\\s\\S]*?)```");

    public static class DescriptionSections {
        private final String main;
        private final String inputFormat;
        private final String outputFormat;
        private final List<Map<String, String>> sampleInputs;

        public DescriptionSections(String main, String inputFormat, String outputFormat,
                                  List<Map<String, String>> sampleInputs) {
            this.main = main != null ? main : "";
            this.inputFormat = inputFormat != null ? inputFormat.trim() : "";
            this.outputFormat = outputFormat != null ? outputFormat.trim() : "";
            this.sampleInputs = sampleInputs != null ? sampleInputs : Collections.emptyList();
        }

        public String getMain() { return main; }
        public String getInputFormat() { return inputFormat; }
        public String getOutputFormat() { return outputFormat; }
        public List<Map<String, String>> getSampleInputs() { return sampleInputs; }
    }

    /**
     * description 마크다운을 파싱하여 섹션별로 분리합니다.
     */
    public static DescriptionSections parse(String description) {
        if (description == null || description.isEmpty()) {
            return new DescriptionSections("", "", "", Collections.emptyList());
        }
        String normalized = description.replace("\r\n", "\n").replace("\r", "\n");

        // 각 섹션 시작 위치 찾기
        int inputStart = findFirstMatch(INPUT_SECTION, normalized);
        int outputStart = findFirstMatch(OUTPUT_SECTION, normalized);
        int exampleStart = findFirstMatch(EXAMPLE_SECTION, normalized);

        List<Integer> sectionStarts = new ArrayList<>();
        if (inputStart >= 0) sectionStarts.add(inputStart);
        if (outputStart >= 0) sectionStarts.add(outputStart);
        if (exampleStart >= 0) sectionStarts.add(exampleStart);
        Collections.sort(sectionStarts);

        // 본문: 첫 번째 섹션 전까지
        String main = normalized;
        if (!sectionStarts.isEmpty()) {
            main = normalized.substring(0, sectionStarts.get(0)).trim();
        }

        // 입력 형식 (다음 섹션: outputStart, exampleStart)
        String inputFormat = extractSectionContent(normalized, inputStart, outputStart, exampleStart);

        // 출력 형식 (다음 섹션: inputStart, exampleStart)
        String outputFormat = extractSectionContent(normalized, outputStart, inputStart, exampleStart);

        // 예제: 예제 섹션에서 ### 예제 입력 N / ### 예제 출력 N + ``` 블록 추출
        List<Map<String, String>> sampleInputs = extractSampleInputs(normalized, exampleStart);

        return new DescriptionSections(main, inputFormat, outputFormat, sampleInputs);
    }

    private static int findFirstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static String extractSectionContent(String normalized, int sectionStart,
                                               int outputStart, int exampleStart) {
        if (sectionStart < 0) return "";
        int end = normalized.length();
        if (outputStart > sectionStart && outputStart < end) end = outputStart;
        if (exampleStart > sectionStart && exampleStart < end) end = exampleStart;
        String content = normalized.substring(sectionStart, end);
        // ## 제목 줄 제거
        int firstNewline = content.indexOf('\n');
        if (firstNewline >= 0) {
            content = content.substring(firstNewline + 1);
        }
        return content.trim();
    }

    private static List<Map<String, String>> extractSampleInputs(String normalized, int exampleStart) {
        List<Map<String, String>> result = new ArrayList<>();
        if (exampleStart < 0) return result;

        String exampleSection = normalized.substring(exampleStart);
        Matcher inputMatcher = EXAMPLE_INPUT_BLOCK.matcher(exampleSection);
        Matcher outputMatcher = EXAMPLE_OUTPUT_BLOCK.matcher(exampleSection);

        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        while (inputMatcher.find()) {
            inputs.add(inputMatcher.group(1).trim());
        }
        while (outputMatcher.find()) {
            outputs.add(outputMatcher.group(1).trim());
        }

        int maxLen = Math.max(inputs.size(), outputs.size());
        for (int i = 0; i < maxLen; i++) {
            Map<String, String> pair = new LinkedHashMap<>();
            pair.put("input", i < inputs.size() ? inputs.get(i) : "");
            pair.put("output", i < outputs.size() ? outputs.get(i) : "");
            result.add(pair);
        }
        return result;
    }
}
