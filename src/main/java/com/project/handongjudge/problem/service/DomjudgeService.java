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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
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


    public void addProblemToContest(Long contestId, String domjudgeProblemId) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems/" + domjudgeProblemId;

        // 최소 요청 body 구성
        Map<String, Object> body = new HashMap<>();
        body.put("label", "A"); // 실제 서비스에서는 A~Z 자동 생성하는 로직 추가 가능

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);
    }


    public String uploadProblemToDomjudge(MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(zipFile.getInputStream(), zipFile.getOriginalFilename()));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // JSON 응답으로 받기
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                DOMJUDGE_API_URL + "/api/v4/problems?strict=false", // 반드시 strict=false 포함
                requestEntity,
                JsonNode.class
        );

        JsonNode responseBody = response.getBody();
        if (responseBody == null || !responseBody.has("problem_id")) {
            throw new RuntimeException("DOMjudge 응답에서 problem_id를 찾을 수 없습니다.");
        }
        System.out.println("Raw response: " + responseBody.toPrettyString());

        // 실제 문제 ID 추출 (예: "sum")
        return responseBody.get("problem_id").asText();
    }




    public void registerProblemToContest(Long contestId, Long domjudgeProblemId) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems/" + domjudgeProblemId;

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        restTemplate.put(url, requestEntity);
    }
}
