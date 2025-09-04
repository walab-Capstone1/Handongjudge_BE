package com.project.handongjudge.domjudge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.problem.util.MultipartInputStreamFileResource;
import com.project.handongjudge.submission.dto.SubmissionOutputResponseDTO;
import com.project.handongjudge.submission.entity.Output;
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
import com.project.handongjudge.problem.repository.ProblemRepository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private final ObjectMapper objectMapper;
    private final ProblemRepository problemRepository;
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
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("label", domjudgeProblemId); // 반드시 포함

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        restTemplate.exchange(
                url,
                HttpMethod.PUT,
                requestEntity,
                String.class
        );
    }

    public String uploadProblemToDomjudge(MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()
        )); 
        // 
        //body.add("problem", problemRepository.findLastProblemId() + 1); // generate problem id : from problemRepository, get last problem id + 1
        

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

        String domjudgeProblemId = responseBody.path("problem_id").asText();

        return domjudgeProblemId;
    }

    private String extractProblemId(String infoMessage) {
        Pattern pattern = Pattern.compile("Saved problem (\\d+)");
        Matcher matcher = pattern.matcher(infoMessage);
        if (matcher.find()) {
            return matcher.group(1);
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

    public String createTeam(Long userId, Long cid, String userName) {
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String cid_str = cid.toString();
            
            // URL에 cid를 파라미터로 추가
            String url = DOMJUDGE_API_URL + "/api/v4/teams";
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("cid", cid_str);
            String urlWithCid = builder.toUriString();
            
            log.info("Creating team for userId: {}, cid: {}, URL: {}", userId, cid, urlWithCid);

            // Request body에 나머지 데이터 추가
            Map<String, Object> requestBody = new HashMap<>();
            String id = userId.toString() + "-" + cid_str;
            String name = userName + "-" + cid_str;    
            
            requestBody.put("id", id);
            requestBody.put("name", name);
            requestBody.put("type", "contest");
            
            log.info("Request body: {}", requestBody);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(urlWithCid, requestEntity, JsonNode.class);
            
            JsonNode responseBody = response.getBody();
            log.info("DOMjudge response status: {}, body: {}", response.getStatusCode(), responseBody);
            
            if (responseBody == null) {
                throw new RuntimeException("DOMjudge 응답이 null입니다.");
            }
            
            // 성공적인 응답에서 team ID 추출
            if (responseBody.has("id")) {
                String teamID = responseBody.get("id").asText();
                log.info("Team created successfully with ID: {}", teamID);
                return teamID;
            } else {
                // 에러 응답인 경우 메시지 확인
                if (responseBody.has("messages")) {
                    JsonNode messages = responseBody.get("messages");
                    log.error("DOMjudge team creation failed. Messages: {}", messages);
                    throw new RuntimeException("DOMjudge team 생성 실패: " + messages.toString());
                } else {
                    log.error("DOMjudge team creation failed. Full response: {}", responseBody);
                    throw new RuntimeException("DOMjudge team 생성 실패: " + responseBody.toString());
                }
            }
            
        } catch (HttpClientErrorException e) {
            String errorResponse = e.getResponseBodyAsString();
            log.error("DOMjudge HTTP Client Error: Status={}, Response={}", e.getStatusCode(), errorResponse);
            throw new RuntimeException("DOMjudge team 생성 HTTP 에러: " + e.getStatusCode() + " - " + errorResponse, e);
        } catch (Exception e) {
            log.error("DOMjudge team 생성 중 예외 발생", e);
            throw new RuntimeException("DOMjudge team 생성 실패: " + e.getMessage(), e);
        }
    }



    public String submitCode(String cid, String teamId, String problemId, String language, File codeFile) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/submissions?strict=false";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("problem_id", problemId);
        body.add("problem", problemId);
        body.add("language", language);
        body.add("language_id", language);
        body.add("team_id", teamId);
        body.add("code", new FileSystemResource(codeFile));


        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);
        JsonNode responseBody = response.getBody();
        if (responseBody != null && responseBody.has("submitid")) {
            return responseBody.get("submitid").asText(); // 정수인데 문자열로 반환
        } else {
            throw new RuntimeException("submitid not found in response: " + responseBody);
        }
    }


    public String getResult(String cid, String submissionId) {
        try {
            HttpHeaders headers = createAuthHeaders();

            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements/" + submissionId;

            log.debug("Request URL: " + url);

            HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class
            );

            log.debug("Response Status: " + response.getStatusCode());
            log.debug("Response Body: " + response.getBody());

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Response body is null for submission: {}", submissionId);
                return null;
            }

            // judgement_type_id가 null이거나 없는 경우 처리
            if (!responseBody.has("judgement_type_id") || responseBody.get("judgement_type_id").isNull()) {
                log.debug("Judgement not ready yet for submission: {}", submissionId);
                return null;
            }

            String result = responseBody.get("judgement_type_id").asText();
            log.info("Result received for submission {}: {}", submissionId, result);
            return result;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Judgement not found for submission: {}", submissionId);
                return null;
            }
            log.error("HTTP error while getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        }
    }

    // Result with Outputs
    public SubmissionOutputResponseDTO getResultOutput(String cid, String submissionId) throws JsonProcessingException {
        try {
            HttpHeaders headers = createAuthHeaders();

            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements/" + submissionId + "/output";

            log.debug("Request URL: " + url);

            HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class
            );

            log.debug("Response Status: " + response.getStatusCode());
            log.debug("Response Body: " + response.getBody());

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Response body is null for submission: {}", submissionId);
                return null;
            }

            // judgement_type_id가 null이거나 없는 경우 처리
            if (!responseBody.has("result") || responseBody.get("result").isNull()) {
                log.debug("Judgement not ready yet for submission: {}", submissionId);
                return null;
            }

            String result = responseBody.get("result").asText();
            log.info("Result received for submission {}: {}", submissionId, result);


            // Output parsing
            JsonNode runsResponse = responseBody.get("runs");
            List<Output> outputList = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();

            if(runsResponse != null && runsResponse.isArray()){
                for(JsonNode runNode : runsResponse){
                    Output output = mapper.treeToValue(runNode, Output.class);
                    outputList.add(output);
                }
            }


            return SubmissionOutputResponseDTO.builder()
                    .result(result)
                    .outputList(outputList)
                    .build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Judgement not found for submission: {}", submissionId);
                return null;
            }
            log.error("HTTP error while getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        }
    }

}
