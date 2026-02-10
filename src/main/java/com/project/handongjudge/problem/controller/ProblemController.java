package com.project.handongjudge.problem.controller;

import com.project.handongjudge.problem.dto.*;
import com.project.handongjudge.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problemService;


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Long> createProblem(
            @ModelAttribute ProblemCreateRequest request,
            @RequestParam(value = "testcase_0", required = false) MultipartFile testcase0,
            @RequestParam(value = "testcase_1", required = false) MultipartFile testcase1,
            @RequestParam(value = "testcase_2", required = false) MultipartFile testcase2,
            @RequestParam(value = "testcase_3", required = false) MultipartFile testcase3,
            @RequestParam(value = "testcase_4", required = false) MultipartFile testcase4,
            @RequestParam(value = "testcase_5", required = false) MultipartFile testcase5,
            @RequestParam(value = "testcase_6", required = false) MultipartFile testcase6,
            @RequestParam(value = "testcase_7", required = false) MultipartFile testcase7,
            @RequestParam(value = "testcase_8", required = false) MultipartFile testcase8,
            @RequestParam(value = "testcase_9", required = false) MultipartFile testcase9,
            Authentication authentication) throws IOException {

        // 테스트케이스 파일들 수집
        List<MultipartFile> testcaseFiles = new ArrayList<>();
        if (testcase0 != null && !testcase0.isEmpty()) testcaseFiles.add(testcase0);
        if (testcase1 != null && !testcase1.isEmpty()) testcaseFiles.add(testcase1);
        if (testcase2 != null && !testcase2.isEmpty()) testcaseFiles.add(testcase2);
        if (testcase3 != null && !testcase3.isEmpty()) testcaseFiles.add(testcase3);
        if (testcase4 != null && !testcase4.isEmpty()) testcaseFiles.add(testcase4);
        if (testcase5 != null && !testcase5.isEmpty()) testcaseFiles.add(testcase5);
        if (testcase6 != null && !testcase6.isEmpty()) testcaseFiles.add(testcase6);
        if (testcase7 != null && !testcase7.isEmpty()) testcaseFiles.add(testcase7);
        if (testcase8 != null && !testcase8.isEmpty()) testcaseFiles.add(testcase8);
        if (testcase9 != null && !testcase9.isEmpty()) testcaseFiles.add(testcase9);

        request.setTestcaseFiles(testcaseFiles);

        Long instructorId = Long.parseLong(authentication.getName());
        Long problemId = problemService.createProblem(request, instructorId);
        return ResponseEntity.ok(problemId);
    }

    @GetMapping("/{problemId}")
    public ResponseEntity<ProblemResponse> getProblem(@PathVariable Long problemId) {
        return ResponseEntity.ok(problemService.getProblem(problemId));
    }
    // ProblemController.java에 추가
    @GetMapping
    public ResponseEntity<List<ProblemResponse>> getAllProblems(Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(problemService.getAllProblems(instructorId));
    }
    @PostMapping("/{problemId}/copy")
    public ResponseEntity<Long> copyProblem(
            @PathVariable Long problemId,
            @RequestBody(required = false) ProblemCopyRequest request,
            Authentication authentication) throws IOException {
        Long instructorId = Long.parseLong(authentication.getName());
        String newTitle = (request != null) ? request.getNewTitle() : null;
        Long newProblemId = problemService.copyProblem(problemId, newTitle, instructorId);
        return ResponseEntity.ok(newProblemId);
    }
    // Handongjudge_BE/src/main/java/com/project/handongjudge/problem/controller/ProblemController.java
// 다음 메서드들을 ProblemController 클래스 안에 추가:

    /**
     * ZIP 파일 내용 파싱 (미리보기)
     */
    @PostMapping("/parse-zip")
    public ResponseEntity<ProblemParseResponse> parseZipFile(
            @RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        ProblemParseResponse response = problemService.parseZipFile(zipFile);
        return ResponseEntity.ok(response);
    }

    /**
     * 문제의 ZIP 파일 파싱 (수정 페이지용)
     */
    @GetMapping("/{problemId}/parse")
    public ResponseEntity<ProblemParseResponse> parseProblemZip(
            @PathVariable Long problemId,
            Authentication authentication) throws IOException {
        Long instructorId = Long.parseLong(authentication.getName());
        ProblemParseResponse response = problemService.parseProblemZip(problemId, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 문제 수정
     */
    @PutMapping(value = "/{problemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProblem(
            @PathVariable Long problemId,
            @ModelAttribute ProblemUpdateRequest request,
            MultipartHttpServletRequest multipartRequest,
            Authentication authentication) throws IOException {
        
        // 로그: 모든 파라미터 이름 출력
        Enumeration<String> allParamNames = multipartRequest.getParameterNames();
        List<String> allParams = new ArrayList<>();
        while (allParamNames.hasMoreElements()) {
            allParams.add(allParamNames.nextElement());
        }
        System.out.println("=== ProblemController.updateProblem 디버그 ===");
        System.out.println("전체 파라미터 이름 목록: " + allParams);
        
        // 로그: 모든 파일 파라미터 이름 출력
        java.util.Map<String, org.springframework.web.multipart.MultipartFile> fileMap = multipartRequest.getFileMap();
        System.out.println("전체 파일 파라미터 이름 목록: " + fileMap.keySet());
        for (String fileName : fileMap.keySet()) {
            org.springframework.web.multipart.MultipartFile file = fileMap.get(fileName);
            System.out.println("  파일 파라미터: " + fileName + ", 파일명: " + 
                    (file != null ? file.getOriginalFilename() : "null") + 
                    ", 크기: " + (file != null ? file.getSize() : 0) + " bytes");
        }
        
        // testcase_로 시작하는 모든 파라미터를 동적으로 추출
        List<MultipartFile> testcaseFiles = new ArrayList<>();
        
        // 파일 파라미터 이름을 가져와서 testcase_로 시작하는 것만 필터링
        List<String> testcaseParamNames = new ArrayList<>();
        
        for (String paramName : fileMap.keySet()) {
            if (paramName.startsWith("testcase_")) {
                testcaseParamNames.add(paramName);
            }
        }
        
        System.out.println("testcase_로 시작하는 파라미터 이름: " + testcaseParamNames);
        
        // 인덱스 순서대로 정렬
        testcaseParamNames.sort((n1, n2) -> {
            int idx1 = extractIndex(n1);
            int idx2 = extractIndex(n2);
            return Integer.compare(idx1, idx2);
        });
        
        System.out.println("정렬된 testcase_ 파라미터 이름: " + testcaseParamNames);
        
        // 정렬된 순서대로 파일 추가
        for (String paramName : testcaseParamNames) {
            MultipartFile file = multipartRequest.getFile(paramName);
            System.out.println("파라미터 '" + paramName + "' 처리:");
            System.out.println("  getFile() 결과: " + (file != null ? "not null" : "null"));
            if (file != null) {
                System.out.println("  파일명: " + file.getOriginalFilename());
                System.out.println("  크기: " + file.getSize() + " bytes");
                System.out.println("  isEmpty(): " + file.isEmpty());
                System.out.println("  ContentType: " + file.getContentType());
            }
            
            if (file != null && !file.isEmpty()) {
                testcaseFiles.add(file);
                System.out.println("  -> 파일 추가됨");
            } else {
                System.out.println("  -> 파일 추가 안됨 (null이거나 empty)");
            }
        }
        
        System.out.println("최종 수집된 테스트케이스 파일 수: " + testcaseFiles.size());
        System.out.println("==========================================");
        
        request.setTestcaseFiles(testcaseFiles);
        
        Long instructorId = Long.parseLong(authentication.getName());
        problemService.updateProblem(problemId, request, instructorId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 파라미터 이름에서 인덱스 추출 (testcase_ 뒤의 숫자)
     */
    private int extractIndex(String paramName) {
        if (paramName.startsWith("testcase_")) {
            try {
                return Integer.parseInt(paramName.substring("testcase_".length()));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 문제 삭제
     */
    @DeleteMapping("/{problemId}")
    public ResponseEntity<Void> deleteProblem(
            @PathVariable Long problemId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        problemService.deleteProblem(problemId, instructorId);
        return ResponseEntity.ok().build();
    }

    /**
     * 문제가 사용되는 과제 목록 조회 (하위 호환성 유지)
     */
    @GetMapping("/{problemId}/assignments")
    public ResponseEntity<List<ProblemAssignmentUsageDto>> getAssignmentsByProblemId(
            @PathVariable Long problemId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        List<ProblemAssignmentUsageDto> assignments = problemService.getAssignmentsByProblemId(problemId, instructorId);
        return ResponseEntity.ok(assignments);
    }

    /**
     * 문제 사용 현황 조회 (과제, 문제집, 퀴즈 포함)
     */
    @GetMapping("/{problemId}/usage")
    public ResponseEntity<ProblemUsageDto> getProblemUsage(
            @PathVariable Long problemId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        ProblemUsageDto usage = problemService.getProblemUsage(problemId, instructorId);
        return ResponseEntity.ok(usage);
    }
}
