package com.project.handongjudge.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.problem.util.MultipartInputStreamFileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
public class DomjudgeService {
    @Value("${domjudge.api.url}")
    private String DOMJUDGE_API_URL;

    @Value("${domjudge.username}")
    private String DOMJUDGE_USERNAME;

    @Value("${domjudge.password}")
    private String DOMJUDGE_PASSWORD;
    // private static final String DOMJUDGE_API_URL = "http://localhost:12345";
    // private static final String DOMJUDGE_USERNAME = "admin";
    // private static final String DOMJUDGE_PASSWORD = "vhLJKHIoP2rG5S6F";
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = DOMJUDGE_USERNAME + ":" + DOMJUDGE_PASSWORD;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        return headers;
    }
    public void createContest(Long sectionId, Integer sectionNumber) {
        try {
            // 1. contest.json 내용 구성
            Map<String, Object> contestJson = new HashMap<>();
            contestJson.put("id", sectionId.toString()); // 선택사항
            contestJson.put("name", "Section " + sectionNumber + " Contest");
            contestJson.put("short_name", sectionId.toString());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

            String startTime = ZonedDateTime
                    .now(ZoneId.of("Asia/Seoul"))
                    .plusDays(1)
                    .withHour(10).withMinute(0).withSecond(0)
                    .format(formatter);

            contestJson.put("start_time", startTime);
            contestJson.put("duration", "2:00:00");
            contestJson.put("public", true);


            // 2. JSON -> byte[]
            byte[] jsonBytes = objectMapper.writeValueAsBytes(contestJson);

            // 3. multipart/form-data 구성
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("json", new ByteArrayResource(jsonBytes) {
                @Override
                public String getFilename() {
                    return "contest.json";
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBasicAuth(DOMJUDGE_USERNAME, DOMJUDGE_PASSWORD, StandardCharsets.UTF_8);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 4. POST 요청
            String url = DOMJUDGE_API_URL + "/api/v4/contests";
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            System.out.println("DOMjudge contest 생성 결과: " + response.getStatusCode());
            System.out.println(response.getBody());

        }  catch (HttpClientErrorException e) {
        String response = e.getResponseBodyAsString();
        log.error("Domjudge API 에러: {}", response);
        throw new RuntimeException("DOMjudge contest 생성 실패\n" + response, e);
    } catch (Exception e) {
        log.error("Domjudge API Unknown 에러", e);
        throw new RuntimeException("DOMjudge contest 생성 실패 (Unknown)", e);
    }

}


    public void addProblemToContest(Long contestId, Long domjudgeProblemId, String label) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems";

        // request body에는 문제의 domjudge problemId, label 등 포함
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("problem", domjudgeProblemId);
        requestBody.put("label", label);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        restTemplate.postForEntity(url, requestEntity, String.class);
    }

    public Long uploadProblemToDomjudge(MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()
        ));
        //body.add("problem", zipFile.getOriginalFilename());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                DOMJUDGE_API_URL + "/api/v4/problems",
                requestEntity,
                JsonNode.class
        );

        JsonNode responseBody = response.getBody();
        if (responseBody == null || !responseBody.has("messages")) {
            throw new RuntimeException("DOMjudge 응답에서 메시지를 찾을 수 없습니다.");
        }

        // "Saved problem 9" -> 9 추출
        String infoMessage = responseBody.path("messages").path("info").get(0).asText();
        Long domjudgeProblemId = extractProblemId(infoMessage);

        return domjudgeProblemId;
    }

    private Long extractProblemId(String infoMessage) {
        Pattern pattern = Pattern.compile("Saved problem (\\d+)");
        Matcher matcher = pattern.matcher(infoMessage);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        } else {
            throw new RuntimeException("문제 ID를 DOMjudge 응답에서 추출할 수 없습니다: " + infoMessage);
        }
    }



    public void registerProblemToContest(Long contestId, Long domjudgeProblemId) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems/" + domjudgeProblemId;

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        restTemplate.put(url, requestEntity);
    }
}
