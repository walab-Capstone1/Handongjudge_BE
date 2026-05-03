package com.project.handongjudge.grade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeRequestDTO {
    @NotNull(message = "과제 ID는 필수입니다")
    private Long assignmentId;
    
    @NotNull(message = "문제 ID는 필수입니다")
    private Long problemId;
    
    @NotNull(message = "학생 ID는 필수입니다")
    private Long userId;
    
    @Min(value = 0, message = "점수는 0 이상이어야 합니다")
    @Max(value = 1000, message = "점수는 1000 이하여야 합니다")
    private Integer score;
    
    private String comment;

    /** true이면 score를 0으로 저장하고 rejected 플래그 설정 */
    private Boolean rejected;
}

