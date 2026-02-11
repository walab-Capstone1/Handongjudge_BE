package com.project.handongjudge.progress.service;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.progress.dto.ProgressSaveRequest;
import com.project.handongjudge.progress.entity.CodeProgress;
import com.project.handongjudge.progress.repository.CodeProgressRepository;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {

    private final CodeProgressRepository codeProgressRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;

    @Transactional
    public void saveProgress(Long userId, ProgressSaveRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        
        Problem problem = problemRepository.findById(request.getProblemId())
            .orElseThrow(() -> new IllegalArgumentException("문제를 찾을 수 없습니다"));
        
        Section section = sectionRepository.findById(request.getSectionId())
            .orElseThrow(() -> new IllegalArgumentException("섹션을 찾을 수 없습니다"));

        // 기존 진행 상황 찾기
        CodeProgress codeProgress = codeProgressRepository
            .findByUserIdAndProblemIdAndSectionIdAndLanguage(
                userId,
                request.getProblemId(),
                request.getSectionId(),
                request.getLanguage()
            )
            .orElse(null);

        if (codeProgress == null) {
            // 새로 생성
            codeProgress = CodeProgress.builder()
                .user(user)
                .problem(problem)
                .section(section)
                .language(request.getLanguage())
                .code(request.getCodeString())
                .savedAt(LocalDateTime.now())
                .build();
        } else {
            // 기존 것 업데이트
            codeProgress.setCode(request.getCodeString());
            codeProgress.setSavedAt(LocalDateTime.now());
        }

        codeProgressRepository.save(codeProgress);
        log.info("진행 상황 저장 완료 - userId: {}, problemId: {}, language: {}", 
            userId, request.getProblemId(), request.getLanguage());
    }

    @Transactional(readOnly = true)
    public String loadProgress(Long userId, Long problemId, Long sectionId, String language) {
        return codeProgressRepository
            .findByUserIdAndProblemIdAndSectionIdAndLanguage(userId, problemId, sectionId, language)
            .map(CodeProgress::getCode)
            .orElse(null);
    }
}
