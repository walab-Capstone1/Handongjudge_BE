package com.project.handongjudge.submission.service;

import com.project.handongjudge.submission.dto.SubmissionRequestDTO;
import com.project.handongjudge.submission.dto.SubmissionResponseDTO;
import org.springframework.stereotype.Service; 
import lombok.RequiredArgsConstructor;

import com.project.handongjudge.submission.repository.SubmissionRepository;
import com.project.handongjudge.submission.util.SubmissionMapper;
import com.project.handongjudge.submission.entity.Submission;
import com.project.handongjudge.user.repository.UserRepository;
import com.project.handongjudge.problem.repository.ProblemRepository;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.user.repository.EnrollmentRepository;
import com.project.handongjudge.problem.util.CodeExtenstion;
import java.io.File;
import com.project.handongjudge.domjudge.service.DomjudgeService;


@RequiredArgsConstructor
@Service
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final SectionRepository sectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DomjudgeService domjudgeService;
    public SubmissionResponseDTO submitCode(SubmissionRequestDTO submissionRequestDTO) {
        // 1. 유저, 문제, 섹션 조회
        // 2. 팀 아이디, 문제 아이디, 컨테스트 아이디 조회
        User user = userRepository.findById(submissionRequestDTO.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));
        Problem problem = problemRepository.findById(submissionRequestDTO.getProblemId()).orElseThrow(() -> new RuntimeException("Problem not found"));
        Section section = sectionRepository.findById(submissionRequestDTO.getSectionId()).orElseThrow(() -> new RuntimeException("Section not found"));
        String teamId = enrollmentRepository.findTeamIdByUserIdAndSectionId(user.getId(), section.getId());
        String domjudgeProblemId = problem.getDomjudgeProblemId();

        String contestId = section.getContestId().toString();  // TODO: 추후 수정 ( sectionId -> contestId)
        
        File codeFile = CodeExtenstion.StringToFile(submissionRequestDTO.getLanguage(), submissionRequestDTO.getCode());

        // 3. 제출 코드 저장
        Submission submission = submissionMapper.toSubmission(submissionRequestDTO);

        // 4. Domjudge API call , submissionId 반환
        domjudgeService.submitCode(contestId, teamId  , domjudgeProblemId, submissionRequestDTO.getLanguage(), codeFile);



        submissionRepository.save(submission);  
        // 5. 제출 코드 반환
        SubmissionResponseDTO submissionResponseDTO = submissionMapper.toSubmissionResponseDTO(submission);

        // 6. 제출 코드 반환
        return submissionResponseDTO;
    }
    
}
