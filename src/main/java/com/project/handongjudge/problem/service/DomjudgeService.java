package com.project.handongjudge.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.problem.util.MultipartInputStreamFileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DomjudgeService {

    private static final String DOMJUDGE_API_URL = "http://localhost:12345";
    private static final String DOMJUDGE_USERNAME = "admin";
    private static final String DOMJUDGE_PASSWORD = "vhLJKHIoP2rG5S6F";
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
            contestJson.put("name", "Section " + sectionNumber + " Contest");
            contestJson.put("shortname", "sec" + sectionId);
            contestJson.put("start_time", ZonedDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
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

        } catch (Exception e) {
            throw new RuntimeException("DOMjudge contest 생성 실패", e);
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

    public Long uploadProblemToDomjudge(String title, MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", title);
        body.add("problem_archive", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                DOMJUDGE_API_URL + "/api/v4/problems",
                requestEntity,
                JsonNode.class
        );

        return response.getBody().get("id").asLong();
    }

    public void registerProblemToContest(Long contestId, Long domjudgeProblemId) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems/" + domjudgeProblemId;

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        restTemplate.put(url, requestEntity);
    }
}
