package com.project.handongjudge.problem.service;

import com.project.handongjudge.problem.dto.*;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.entity.ProblemSet;
import com.project.handongjudge.problem.entity.ProblemSetProblem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.problem.repository.ProblemSetProblemRepository;
import com.project.handongjudge.problem.repository.ProblemSetRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemSetService {

    private final ProblemSetRepository problemSetRepository;
    private final ProblemSetProblemRepository problemSetProblemRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    /**
     * 문제집 목록 조회 (작성자별)
     */
    @Transactional(readOnly = true)
    public List<ProblemSetResponse> getAllProblemSets(Long instructorId) {
        List<ProblemSet> problemSets = problemSetRepository.findByCreatedBy_Id(instructorId);
        
        return problemSets.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 문제집 상세 조회
     */
    @Transactional(readOnly = true)
    public ProblemSetDetailResponse getProblemSet(Long problemSetId, Long instructorId) {
        ProblemSet problemSet = problemSetRepository.findByIdAndCreatedBy_Id(problemSetId, instructorId)
                .orElseThrow(() -> new IllegalArgumentException("ProblemSet not found or access denied: " + problemSetId));

        List<ProblemSetProblem> problemSetProblems = problemSetProblemRepository
                .findByProblemSetIdOrderByProblemOrder(problemSetId);

        List<ProblemInSetDto> problems = problemSetProblems.stream()
                .map(psp -> ProblemInSetDto.builder()
                        .id(psp.getProblem().getId())
                        .title(psp.getProblem().getTitle())
                        .difficulty(psp.getProblem().getDifficulty())
                        .order(psp.getProblemOrder())
                        .build())
                .collect(Collectors.toList());

        return ProblemSetDetailResponse.builder()
                .id(problemSet.getId())
                .title(problemSet.getTitle())
                .description(problemSet.getDescription())
                .tags(problemSet.getTags())
                .createdAt(problemSet.getCreatedAt())
                .problems(problems)
                .build();
    }

    /**
     * 문제집 생성
     */
    @Transactional
    public ProblemSetResponse createProblemSet(ProblemSetCreateRequest request, Long instructorId) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new IllegalArgumentException("Instructor not found: " + instructorId));

        ProblemSet problemSet = ProblemSet.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .tags(request.getTags() != null ? request.getTags() : "[]")
                .createdAt(LocalDateTime.now())
                .createdBy(instructor)
                .build();

        problemSet = problemSetRepository.save(problemSet);
        return convertToResponse(problemSet);
    }

    /**
     * 문제집 수정
     */
    @Transactional
    public void updateProblemSet(Long problemSetId, ProblemSetUpdateRequest request, Long instructorId) {
        ProblemSet problemSet = problemSetRepository.findByIdAndCreatedBy_Id(problemSetId, instructorId)
                .orElseThrow(() -> new IllegalArgumentException("ProblemSet not found or access denied: " + problemSetId));

        if (request.getTitle() != null) {
            problemSet.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            problemSet.setDescription(request.getDescription());
        }
        if (request.getTags() != null) {
            problemSet.setTags(request.getTags());
        }

        problemSetRepository.save(problemSet);
    }

    /**
     * 문제집 삭제
     */
    @Transactional
    public void deleteProblemSet(Long problemSetId, Long instructorId) {
        if (!problemSetRepository.existsByIdAndCreatedBy_Id(problemSetId, instructorId)) {
            throw new IllegalArgumentException("ProblemSet not found or access denied: " + problemSetId);
        }

        // 문제집의 모든 문제 관계 삭제
        problemSetProblemRepository.deleteByProblemSetId(problemSetId);
        
        // 문제집 삭제
        problemSetRepository.deleteById(problemSetId);
    }

    /**
     * 문제집에 문제 추가
     */
    @Transactional
    public void addProblemToSet(Long problemSetId, AddProblemToSetRequest request, Long instructorId) {
        ProblemSet problemSet = problemSetRepository.findByIdAndCreatedBy_Id(problemSetId, instructorId)
                .orElseThrow(() -> new IllegalArgumentException("ProblemSet not found or access denied: " + problemSetId));

        Problem problem = problemRepository.findById(request.getProblemId())
                .orElseThrow(() -> new IllegalArgumentException("Problem not found: " + request.getProblemId()));

        // 이미 추가된 문제인지 확인
        if (problemSetProblemRepository.findByProblemSetIdAndProblemId(problemSetId, request.getProblemId()).isPresent()) {
            throw new IllegalArgumentException("Problem already exists in the problem set");
        }

        // 순서 결정
        Integer order = request.getOrder();
        if (order == null) {
            // 순서가 지정되지 않으면 마지막에 추가
            long count = problemSetProblemRepository.countByProblemSetId(problemSetId);
            order = (int) count;
        }

        ProblemSetProblem problemSetProblem = ProblemSetProblem.builder()
                .problemSet(problemSet)
                .problem(problem)
                .problemOrder(order)
                .build();

        problemSetProblemRepository.save(problemSetProblem);
    }

    /**
     * 문제집에서 문제 제거
     */
    @Transactional
    public void removeProblemFromSet(Long problemSetId, Long problemId, Long instructorId) {
        if (!problemSetRepository.existsByIdAndCreatedBy_Id(problemSetId, instructorId)) {
            throw new IllegalArgumentException("ProblemSet not found or access denied: " + problemSetId);
        }

        problemSetProblemRepository.deleteByProblemSetIdAndProblemId(problemSetId, problemId);
    }

    /**
     * ProblemSet을 ProblemSetResponse로 변환
     */
    private ProblemSetResponse convertToResponse(ProblemSet problemSet) {
        long problemCount = problemSetProblemRepository.countByProblemSetId(problemSet.getId());
        
        return ProblemSetResponse.builder()
                .id(problemSet.getId())
                .title(problemSet.getTitle())
                .description(problemSet.getDescription())
                .tags(problemSet.getTags())
                .createdAt(problemSet.getCreatedAt())
                .problemCount(problemCount)
                .build();
    }
}

