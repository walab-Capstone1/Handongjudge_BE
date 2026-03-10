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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    // DomjudgeService.java - createContest 메서드 수정
    public void createContest(Long sectionId, Integer sectionNumber, String courseTitle) {
        try {
            // 1. contest.json 내용 구성
            Map<String, Object> contestJson = new HashMap<>();
            contestJson.put("id", sectionId.toString()); // external ID = sectionId

            // sectionNumber가 null이면 제목만 사용
            String contestName = (sectionNumber != null)
                    ? courseTitle + " " + sectionNumber + "분반"
                    : courseTitle;
            contestJson.put("name", contestName);

            contestJson.put("short_name", sectionId.toString()); // short_name = sectionId

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

            // 시작 시간: 지금 바로 시작
            String startTime = ZonedDateTime
                    .now(ZoneId.of("Asia/Seoul"))
                    .format(formatter);  // ← plusDays(1) 제거!

            contestJson.put("start_time", startTime);
            contestJson.put("duration", "8760:00:00"); // 1년
            contestJson.put("activate_time", startTime); // ← 자동 활성화 시간 추가
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

            log.info("DOMjudge contest 생성 결과: {}", response.getStatusCode());
            log.info("Response: {}", response.getBody());

        } catch (HttpClientErrorException e) {
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
        requestBody.put("lazy_eval_results", 2);
        // EVAL_DEFAULT (0): 전역 설정 사용
        //        EVAL_LAZY (1): 첫 실패 시 중단
        //        EVAL_FULL (2): 모든 테스트 케이스 실행
        //        EVAL_DEMAND (3): 요청 시에만 실행

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    requestEntity,
                    String.class
            );
        } catch (HttpClientErrorException e) {
            // 이미 Contest에 연결된 문제인 경우 무시 (400 Bad Request: "Problem already linked to contest")
            if (e.getStatusCode().value() == 400 && 
                e.getResponseBodyAsString() != null && 
                e.getResponseBodyAsString().contains("already linked to contest")) {
                log.info("문제가 이미 Contest에 연결되어 있습니다. 무시합니다. contestId: {}, problemId: {}", contestId, domjudgeProblemId);
                return;
            }
            // 다른 오류는 그대로 throw
            throw e;
        }
    }

    public void removeProblemFromContest(Long contestId, String domjudgeProblemId) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems/" + domjudgeProblemId;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("label", domjudgeProblemId); // 반드시 포함

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        restTemplate.exchange(
                url,
                HttpMethod.DELETE,
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

        try {
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
        } catch (HttpServerErrorException e) {
            int status = e.getStatusCode().value();
            log.error("DOMjudge 문제 업로드 실패: status={}, response={}", status, e.getResponseBodyAsString());
            if (status == 502 || status == 503 || status == 504) {
                throw new RuntimeException(
                    "채점 서버(DOMjudge)에 일시적인 문제가 있습니다. 잠시 후 다시 시도해 주세요. (오류 코드: " + status + ")",
                    e
                );
            }
            throw new RuntimeException("DOMjudge 문제 업로드 실패: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            log.error("DOMjudge 연결 실패 (타임아웃 또는 연결 거부): {}", e.getMessage());
            throw new RuntimeException(
                "채점 서버(DOMjudge)에 연결할 수 없습니다. 서버 상태를 확인한 후 잠시 후 다시 시도해 주세요.",
                e
            );
        }
    }

    /**
     * DOMjudge에서 기존 문제 업데이트
     * @param domjudgeProblemId 업데이트할 문제의 DOMjudge ID
     * @param zipFile 업데이트할 ZIP 파일
     * @return 업데이트된 문제의 DOMjudge ID (기존 ID와 동일)
     * @throws IOException 파일 처리 오류
     */
    public String updateProblemInDomjudge(String domjudgeProblemId, MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()
        ));
        // problem ID를 문자열로 body에 추가
        body.add("problem", domjudgeProblemId);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // POST 방식으로 업데이트 (uploadProblemToDomjudge와 유사한 방식)
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    DOMJUDGE_API_URL + "/api/v4/problems",
                    requestEntity,
                    JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            if (responseBody == null || !responseBody.has("messages")) {
                throw new RuntimeException("DOMjudge 응답에서 메시지를 찾을 수 없습니다.");
            }

            // 업데이트된 문제 ID 반환 (기존 ID와 동일)
            String updatedProblemId = responseBody.path("problem_id").asText();
            if (updatedProblemId == null || updatedProblemId.isEmpty()) {
                // 응답에 problem_id가 없으면 기존 ID 반환
                return domjudgeProblemId;
            }

            log.info("DOMjudge 문제 업데이트 완료: problemId={}", updatedProblemId);
            return updatedProblemId;
        } catch (HttpClientErrorException e) {
            String errorResponse = e.getResponseBodyAsString();
            log.error("DOMjudge 문제 업데이트 실패: problemId={}, status={}, response={}", 
                    domjudgeProblemId, e.getStatusCode(), errorResponse);
            throw new RuntimeException("DOMjudge 문제 업데이트 실패: " + errorResponse, e);
        }
    }

    /**
     * Contest 내에서 문제 업데이트
     * @param contestId Contest ID (Section ID)
     * @param domjudgeProblemId 업데이트할 문제의 DOMjudge ID (기존 ID)
     * @param zipFile 업데이트할 ZIP 파일
     * @return 업데이트된 문제의 DOMjudge ID (기존 ID와 동일)
     * @throws IOException 파일 처리 오류
     */
    public String updateProblemInContest(Long contestId, String domjudgeProblemId, MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()
        ));
        // problem ID를 문자열로 body에 추가 (기존 ID)
        body.add("problem", domjudgeProblemId);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            // POST 방식으로 Contest 내 문제 업데이트
            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems";
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            if (responseBody == null || !responseBody.has("messages")) {
                throw new RuntimeException("DOMjudge 응답에서 메시지를 찾을 수 없습니다.");
            }

            // 업데이트된 문제 ID 반환 (기존 ID와 동일)
            String updatedProblemId = responseBody.path("problem_id").asText();
            if (updatedProblemId == null || updatedProblemId.isEmpty()) {
                // 응답에 problem_id가 없으면 기존 ID 반환
                return domjudgeProblemId;
            }

            log.info("DOMjudge Contest 내 문제 업데이트 완료: contestId={}, problemId={}", contestId, updatedProblemId);
            return updatedProblemId;
        } catch (HttpClientErrorException e) {
            String errorResponse = e.getResponseBodyAsString();
            log.error("DOMjudge Contest 내 문제 업데이트 실패: contestId={}, problemId={}, status={}, response={}", 
                    contestId, domjudgeProblemId, e.getStatusCode(), errorResponse);
            throw new RuntimeException("DOMjudge Contest 내 문제 업데이트 실패: " + errorResponse, e);
        }
    }

    /**
     * DOMjudge에서 문제 삭제
     * @param domjudgeProblemId 삭제할 문제의 DOMjudge ID
     */
    public void deleteProblemFromDomjudge(String domjudgeProblemId) {
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = DOMJUDGE_API_URL + "/api/v4/problems/" + domjudgeProblemId;

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );

            log.info("DOMjudge 문제 삭제 완료: problemId={}", domjudgeProblemId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("DOMjudge에서 문제를 찾을 수 없음 (이미 삭제되었을 수 있음): problemId={}", domjudgeProblemId);
                // 404는 이미 삭제된 것으로 간주하고 정상 처리
                return;
            }
            log.error("DOMjudge 문제 삭제 실패: problemId={}, status={}, response={}", 
                    domjudgeProblemId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("DOMjudge 문제 삭제 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("DOMjudge 문제 삭제 중 예외 발생: problemId={}", domjudgeProblemId, e);
            throw new RuntimeException("DOMjudge 문제 삭제 실패: " + e.getMessage(), e);
        }
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

            //String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements/" + submissionId;
            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements?submission_id=" + submissionId;

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
            if (responseBody == null||responseBody.isEmpty()) {
                log.warn("Response body is null for submission: {}", submissionId);
                return null;
            }

            // judgement_type_id가 null이거나 없는 경우 처리
//            if (!responseBody.has("judgement_type_id") || responseBody.get("judgement_type_id").isNull()) {
//                log.debug("Judgement not ready yet for submission: {}", submissionId);
//                return null;
//            }
//
//            String result = responseBody.get("judgement_type_id").asText();
//            log.info("Result received for submission {}: {}", submissionId, result);
//            return result;

            // 응답이 배열이므로 첫 번째 요소를 가져옵니다
            if (responseBody.isArray() && responseBody.size() > 0) {
                JsonNode judgement = responseBody.get(0); // 첫 번째 판정

                // 이제 판정 객체에서 judgement_type_id를 확인합니다
                if (judgement.has("judgement_type_id") && !judgement.get("judgement_type_id").isNull()) {
                    String result = judgement.get("judgement_type_id").asText();
                    log.info("Result received for submission {}: {}", submissionId, result);
                    return result;
                }
            }

            // 여기에 도달하면 판정이 아직 준비되지 않은 것입니다
            log.debug("Judgement not ready yet for submission: {}", submissionId);
            return null;

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

    /**
     * submission_id로 judgement 한 번만 조회하여 result(judgement_type_id)와 judgement id를 함께 반환.
     * 제출하기(getResult)와 동일한 API를 사용하며, 테스트하기에서는 여기서 result + judgementId를 한 번에 사용.
     */
    private JudgementInfo getJudgementInfo(String cid, String submissionId) {
        try {
            HttpHeaders headers = createAuthHeaders();
            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements?submission_id=" + submissionId;
            HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class
            );
            JsonNode responseBody = response.getBody();
            if (responseBody == null || !responseBody.isArray() || responseBody.size() == 0) {
                return null;
            }
            JsonNode judgement = responseBody.get(0);
            if (!judgement.has("judgement_type_id") || judgement.get("judgement_type_id").isNull()) {
                return null;
            }
            String result = judgement.get("judgement_type_id").asText();
            if (!judgement.has("id") || judgement.get("id").isNull()) {
                return null;
            }
            String judgementId = judgement.get("id").asText();
            return new JudgementInfo(result, judgementId);
        } catch (Exception e) {
            log.debug("Could not get judgement info for submission {}: {}", submissionId, e.getMessage());
            return null;
        }
    }

    private static class JudgementInfo {
        final String result;
        final String judgementId;

        JudgementInfo(String result, String judgementId) {
            this.result = result;
            this.judgementId = judgementId;
        }
    }

    /**
     * 테스트하기용: judgement 한 번 조회 후 CE면 output 호출 없이 result만 반환, 그 외는 output 조회.
     */
    public SubmissionOutputResponseDTO getResultOutput(String cid, String submissionId) throws JsonProcessingException {
        JudgementInfo info = getJudgementInfo(cid, submissionId);
        if (info == null) {
            log.debug("Judgement not ready yet for submission: {}", submissionId);
            return null;
        }

        // CE(컴파일 에러)는 run이 없으므로 output 호출 없이 result만 반환
        if ("CE".equals(info.result)) {
            log.info("CE for submission {}, returning without output call: {}", submissionId, info.result);
            return SubmissionOutputResponseDTO.builder()
                    .result(info.result)
                    .outputList(Collections.emptyList())
                    .build();
        }

        // AC, WA, TLE 등: judgement id로 output 조회
        try {
            HttpHeaders headers = createAuthHeaders();
            String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements/" + info.judgementId + "/output";

            log.debug("Request URL: " + url);

            HttpEntity<Object> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    JsonNode.class
            );

            JsonNode responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Response body is null for submission: {}", submissionId);
                return SubmissionOutputResponseDTO.builder()
                        .result(info.result)
                        .outputList(Collections.emptyList())
                        .build();
            }

            JsonNode runsResponse = responseBody.get("runs");
            List<Output> outputList = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();

            if (runsResponse != null && runsResponse.isArray()) {
                for (JsonNode runNode : runsResponse) {
                    Output output = mapper.treeToValue(runNode, Output.class);
                    outputList.add(output);
                }
            }

            return SubmissionOutputResponseDTO.builder()
                    .result(info.result)
                    .outputList(outputList)
                    .build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                // output이 없으면 이미 가진 result로 반환
                log.info("Output 404 for submission {}, returning result only: {}", submissionId, info.result);
                return SubmissionOutputResponseDTO.builder()
                        .result(info.result)
                        .outputList(Collections.emptyList())
                        .build();
            }
            log.error("HTTP error while getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting result for submission {}: {}", submissionId, e.getMessage());
            throw e;
        }
    }

}
