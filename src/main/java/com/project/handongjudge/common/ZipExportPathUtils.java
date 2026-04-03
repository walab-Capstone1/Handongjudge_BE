package com.project.handongjudge.common;

import com.project.handongjudge.grade.dto.StudentGradeSummaryDTO;

/**
 * ZIP보내기 등에서 사용하는 경로 세그먼트 정규화.
 */
public final class ZipExportPathUtils {

    private ZipExportPathUtils() {
    }

    /**
     * 문제 폴더 아래 학생 디렉터리명: 학번_이름 (이름 없으면 학번만).
     */
    public static String studentFolderSegment(StudentGradeSummaryDTO student) {
        if (student == null) {
            return "unknown";
        }
        return studentFolderSegment(student.getStudentId(), student.getStudentName());
    }

    public static String studentFolderSegment(String studentId, String studentName) {
        String sid = studentId == null ? "" : studentId.trim();
        String nm = studentName == null ? "" : studentName.trim();
        if (sid.isEmpty() && nm.isEmpty()) {
            return "unknown";
        }
        String combined = nm.isEmpty() ? sid : (sid.isEmpty() ? nm : sid + "_" + nm);
        return sanitizePathSegment(combined);
    }

    public static String sanitizePathSegment(String s) {
        String value = s == null ? "unknown" : s.trim();
        if (value.isEmpty()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9가-힣._-]", "_");
    }
}
