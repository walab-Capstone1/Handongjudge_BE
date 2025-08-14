package com.project.handongjudge.section.controller;

import com.project.handongjudge.section.dto.SectionInfoDto;
import com.project.handongjudge.section.dto.SectionRequest;
import com.project.handongjudge.section.dto.SectionResponse;
import com.project.handongjudge.section.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sections")
public class SectionController {

    private final SectionService sectionService;

    @PostMapping
    public ResponseEntity<SectionResponse> createSection(@RequestBody SectionRequest request) {
        SectionResponse response = sectionService.createSection(request);
        return ResponseEntity.ok(response);
    }
    // SectionController.java에 추가
    @GetMapping("/{sectionId}")
    public ResponseEntity<SectionInfoDto> getSectionInfo(@PathVariable Long sectionId) {
        try {
            SectionInfoDto sectionInfo = sectionService.getSectionInfo(sectionId);
            return ResponseEntity.ok(sectionInfo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
