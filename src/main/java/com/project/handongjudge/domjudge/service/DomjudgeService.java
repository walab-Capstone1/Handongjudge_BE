package com.project.handongjudge.domjudge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.handongjudge.problem.util.MultipartInputStreamFileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
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


    public void addProblemToContest(Long contestId, String domjudgeProblemId) { // label 제거됨
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + contestId + "/problems";

        // request body에는 문제의 domjudge problemId, label 등 포함
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("problem", domjudgeProblemId);
        requestBody.put("label", "A");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        restTemplate.postForEntity(url, requestEntity, String.class);
    }

    public String uploadProblemToDomjudge(MultipartFile zipFile) throws IOException {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("zip", new MultipartInputStreamFileResource(
                zipFile.getInputStream(), zipFile.getOriginalFilename()
        )); 
        // 
        body.add("problem", problemRepository.findLastProblemId() + 1); // generate problem id : from problemRepository, get last problem id + 1
        

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
        String domjudgeProblemId = extractProblemId(infoMessage);

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



    // submit code to domjudge
    public String submitCode(String cid, String teamId, String problemId, String language, File codeFile) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/submissions";
        // /api/v4/contests/{cid}/judgements

        //parameter : cid
        // strict : false
        


        // request body : 
        // problem, problemId, language, language_id, team_id, user_id, time, entry_point,id,files,code
        
        // example
    //    curl -X 'POST' \
    //   'http://192.168.68.2:12345/api/v4/contests/1234/submissions?strict=false' \
    //   -H 'accept: application/json' \
    //   -H 'Authorization: Basic YWRtaW46SGFuZG9uZ2p1ZGdlMTIzNA==' \
    //   -H 'Content-Type: multipart/form-data' \
    //   -F 'language_id=' \
    //   -F 'entry_point=' \
    //   -F 'code=@solution.cpp' \
    //   -F 'time=' \
    //   -F 'user_id=' \
    //   -F 'language=cpp' \
    //   -F 'id=' \
    //   -F 'problem_id=simpleadd' \
    //   -F 'team_id=4-2'


        // request body에는 문제의 domjudge problemId, label 등 포함
        Map<String, Object> requestBody = new HashMap<>();
            
            requestBody.put("problem_id", problemId);
           
            requestBody.put("language_id", language);
            requestBody.put("team_id", teamId);
            
           
           
            
            
            requestBody.put("code", codeFile);


        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

        // response example; {
    //   "language_id": "cpp",
    //   "time": "2025-08-07T02:37:41.587+02:00",
    //   "contest_time": "49:35:56.587",
    //   "team_id": "4-2",
    //   "problem_id": "simpleadd",
    //   "files": [
    //     {
    //       "href": "contests/1234/submissions/5/files",
    //       "mime": "application/zip",
    //       "filename": "submission.zip"
    //     }
    //   ],
    //   "submitid": 5,
    //   "id": "5",
    //   "entry_point": null,
    //   "import_error": null
    // }
        // return submissionId
        return response.getBody().get("id").asText();   // TODO: 추후 수정 ( submissionId -> submission)
    }

    public String getResult(String cid, String submissionId) { // submissionId -> submissionId
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = DOMJUDGE_API_URL + "/api/v4/contests/" + cid + "/judgements";
        ///api/v4/contests/{cid}/judgements

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("ids", submissionId)
                .queryParam("submission_id", submissionId)
                .queryParam("strict", "false");
        String urlWithParams = builder.toUriString();

        // request body : 
        // ids : submissionId
        // submission_id : submissionId
        // strict : false

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(urlWithParams, JsonNode.class);

        // response example; 
        //         [
        //   {
        //     "start_time": "2025-08-07T02:37:45.973+02:00",
        //     "start_contest_time": "49:36:00.973",
        //     "end_time": "2025-08-07T02:37:49.155+02:00",
        //     "end_contest_time": "49:36:04.155",
        //     "max_run_time": 0.001,
        //     "submission_id": "5",
        //     "id": "5",
        //     "valid": true,
        //     "judgement_type_id": "AC"
        //   }
        // ]
        String result = response.getBody().get(0).get("judgement_type_id").asText();    // TODO: 추후 수정 ( judgement_type_id -> result)
        return result;
    }
}
