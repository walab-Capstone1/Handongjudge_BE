package com.project.handongjudge.community.dto;

import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionCreateDto {

    @NotNull(message = "섹션 ID는 필수입니다")
    private Long sectionId;

    private Long assignmentId;  // 선택적

    private Long problemId;  // 선택적

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;

    @NotNull(message = "익명 여부는 필수입니다")
    private Boolean isAnonymous;

    @NotNull(message = "공개 여부는 필수입니다")
    private Boolean isPublic;
}


