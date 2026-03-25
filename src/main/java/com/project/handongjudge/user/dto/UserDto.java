package com.project.handongjudge.user.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long id;
    private String email;
    private String name;
    private String studentId;
    private LocalDateTime createdAt;
    private String profileImageUrl;
    private String role;

    public static UserDto from(com.project.handongjudge.user.entity.User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .studentId(user.getStudentId())
                .createdAt(user.getCreatedAt())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .build();
    }
}