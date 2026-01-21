package com.project.handongjudge.problem.controller;

import com.project.handongjudge.problem.dto.*;
import com.project.handongjudge.problem.service.ProblemSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem-sets")
public class ProblemSetController {

    private final ProblemSetService problemSetService;

    /**
     * 문제집 목록 조회 (작성자별 필터링)
     */
    @GetMapping
    public ResponseEntity<List<ProblemSetResponse>> getAllProblemSets(Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        List<ProblemSetResponse> problemSets = problemSetService.getAllProblemSets(instructorId);
        return ResponseEntity.ok(problemSets);
    }

    /**
     * 문제집 상세 조회
     */
    @GetMapping("/{problemSetId}")
    public ResponseEntity<ProblemSetDetailResponse> getProblemSet(
            @PathVariable Long problemSetId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        ProblemSetDetailResponse response = problemSetService.getProblemSet(problemSetId, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 문제집 생성
     */
    @PostMapping
    public ResponseEntity<ProblemSetResponse> createProblemSet(
            @RequestBody ProblemSetCreateRequest request,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        ProblemSetResponse response = problemSetService.createProblemSet(request, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 문제집 수정
     */
    @PutMapping("/{problemSetId}")
    public ResponseEntity<Void> updateProblemSet(
            @PathVariable Long problemSetId,
            @RequestBody ProblemSetUpdateRequest request,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        problemSetService.updateProblemSet(problemSetId, request, instructorId);
        return ResponseEntity.ok().build();
    }

    /**
     * 문제집 삭제
     */
    @DeleteMapping("/{problemSetId}")
    public ResponseEntity<Void> deleteProblemSet(
            @PathVariable Long problemSetId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        problemSetService.deleteProblemSet(problemSetId, instructorId);
        return ResponseEntity.ok().build();
    }

    /**
     * 문제집에 문제 추가
     */
    @PostMapping("/{problemSetId}/problems")
    public ResponseEntity<Void> addProblemToSet(
            @PathVariable Long problemSetId,
            @RequestBody AddProblemToSetRequest request,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        problemSetService.addProblemToSet(problemSetId, request, instructorId);
        return ResponseEntity.ok().build();
    }

    /**
     * 문제집에서 문제 제거
     */
    @DeleteMapping("/{problemSetId}/problems/{problemId}")
    public ResponseEntity<Void> removeProblemFromSet(
            @PathVariable Long problemSetId,
            @PathVariable Long problemId,
            Authentication authentication) {
        Long instructorId = Long.parseLong(authentication.getName());
        problemSetService.removeProblemFromSet(problemSetId, problemId, instructorId);
        return ResponseEntity.ok().build();
    }
}

