package com.project.handongjudge.section.service;

import com.project.handongjudge.section.entity.Section;
import com.project.handongjudge.section.entity.SectionUserRole;
import com.project.handongjudge.section.repository.SectionRepository;
import com.project.handongjudge.section.repository.SectionUserRoleRepository;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 수업별 사용자 역할 관리 서비스
 * 전역 권한(SUPER_ADMIN)과 수업별 권한을 통합하여 체크
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectionRoleService {

    private final SectionUserRoleRepository sectionUserRoleRepository;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;

    /**
     * 특정 수업에서 사용자의 역할 조회
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @return 역할 (ADMIN, TUTOR, STUDENT) 또는 null (역할 없음)
     */
    public Optional<SectionUserRole.SectionRole> getUserRoleInSection(Long userId, Long sectionId) {
        // SUPER_ADMIN은 모든 수업에서 ADMIN 권한을 가진 것으로 간주
        User user = userRepository.findById(userId)
                .orElse(null);
        
        if (user != null && user.getRole() == User.Role.SUPER_ADMIN) {
            return Optional.of(SectionUserRole.SectionRole.ADMIN);
        }

        // SectionUserRole에서 조회
        Optional<SectionUserRole> sectionUserRole = sectionUserRoleRepository
                .findBySectionIdAndUserId(sectionId, userId);
        
        return sectionUserRole.map(SectionUserRole::getRole);
    }

    /**
     * 특정 수업에서 사용자가 특정 역할을 가지고 있는지 확인
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @param requiredRoles 필요한 역할들
     * @return 권한이 있으면 true
     */
    public boolean hasRole(Long userId, Long sectionId, SectionUserRole.SectionRole... requiredRoles) {
        Optional<SectionUserRole.SectionRole> userRole = getUserRoleInSection(userId, sectionId);
        
        if (userRole.isEmpty()) {
            return false;
        }

        SectionUserRole.SectionRole role = userRole.get();
        for (SectionUserRole.SectionRole requiredRole : requiredRoles) {
            if (role == requiredRole) {
                return true;
            }
        }
        return false;
    }

    /**
     * 특정 수업에서 관리자 권한(ADMIN 또는 TUTOR)이 있는지 확인
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @return 관리자 권한이 있으면 true
     */
    public boolean isManager(Long userId, Long sectionId) {
        return hasRole(userId, sectionId, 
                SectionUserRole.SectionRole.ADMIN, 
                SectionUserRole.SectionRole.TUTOR);
    }

    /**
     * 특정 수업에서 ADMIN 권한이 있는지 확인
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @return ADMIN 권한이 있으면 true
     */
    public boolean isAdmin(Long userId, Long sectionId) {
        return hasRole(userId, sectionId, SectionUserRole.SectionRole.ADMIN);
    }

    /**
     * 특정 수업에서 수강생인지 확인
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @return 수강생이면 true
     */
    public boolean isStudent(Long userId, Long sectionId) {
        return hasRole(userId, sectionId, SectionUserRole.SectionRole.STUDENT);
    }

    /**
     * 특정 수업에서 역할이 있는지 확인 (어떤 역할이든)
     * @param userId 사용자 ID
     * @param sectionId 수업 ID
     * @return 역할이 있으면 true
     */
    public boolean hasAnyRole(Long userId, Long sectionId) {
        return getUserRoleInSection(userId, sectionId).isPresent();
    }

    /**
     * 사용자가 관리 중인 수업 목록 조회 (ADMIN 또는 TUTOR 역할)
     * @param userId 사용자 ID
     * @return 관리 중인 수업의 SectionUserRole 목록
     */
    public List<SectionUserRole> getManagingSections(Long userId) {
        return sectionUserRoleRepository.findManagingSectionsByUserId(userId);
    }

    /**
     * 사용자가 수강 중인 수업 목록 조회 (STUDENT 역할)
     * @param userId 사용자 ID
     * @return 수강 중인 수업의 SectionUserRole 목록
     */
    public List<SectionUserRole> getEnrolledSections(Long userId) {
        return sectionUserRoleRepository.findEnrolledSectionsByUserId(userId);
    }

    /**
     * 사용자의 모든 수업별 역할 조회
     * @param userId 사용자 ID
     * @return 모든 수업별 역할 목록
     */
    public List<SectionUserRole> getAllSectionRoles(Long userId) {
        return sectionUserRoleRepository.findByUserId(userId);
    }

    /**
     * 수업 생성 시 ADMIN 역할 자동 부여
     * @param sectionId 수업 ID
     * @param userId 사용자 ID (생성자)
     */
    @Transactional
    public void assignAdminRole(Long sectionId, Long userId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 이미 역할이 있는지 확인
        Optional<SectionUserRole> existing = sectionUserRoleRepository
                .findBySectionIdAndUserId(sectionId, userId);
        
        if (existing.isPresent()) {
            // 이미 있으면 ADMIN으로 업데이트
            SectionUserRole role = existing.get();
            role.setRole(SectionUserRole.SectionRole.ADMIN);
            sectionUserRoleRepository.save(role);
        } else {
            // 없으면 새로 생성
            SectionUserRole sectionUserRole = SectionUserRole.builder()
                    .section(section)
                    .user(user)
                    .role(SectionUserRole.SectionRole.ADMIN)
                    .build();
            sectionUserRoleRepository.save(sectionUserRole);
        }

        // creator_id 설정
        if (section.getCreator() == null) {
            section.setCreator(user);
            sectionRepository.save(section);
        }

        log.info("ADMIN 역할 부여 완료 - sectionId: {}, userId: {}", sectionId, userId);
    }

    /**
     * 튜터 역할 부여
     * @param sectionId 수업 ID
     * @param userId 사용자 ID
     * @param adminUserId ADMIN 권한을 가진 사용자 ID (권한 체크용)
     */
    @Transactional
    public void assignTutorRole(Long sectionId, Long userId, Long adminUserId) {
        // 권한 체크: ADMIN만 튜터를 추가할 수 있음
        if (!isAdmin(adminUserId, sectionId)) {
            throw new IllegalArgumentException("튜터를 추가할 권한이 없습니다");
        }

        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 이미 역할이 있는지 확인
        Optional<SectionUserRole> existing = sectionUserRoleRepository
                .findBySectionIdAndUserId(sectionId, userId);
        
        if (existing.isPresent()) {
            SectionUserRole role = existing.get();
            // ADMIN이면 TUTOR로 변경 불가
            if (role.getRole() == SectionUserRole.SectionRole.ADMIN) {
                throw new IllegalArgumentException("ADMIN 역할은 변경할 수 없습니다");
            }
            role.setRole(SectionUserRole.SectionRole.TUTOR);
            sectionUserRoleRepository.save(role);
        } else {
            SectionUserRole sectionUserRole = SectionUserRole.builder()
                    .section(section)
                    .user(user)
                    .role(SectionUserRole.SectionRole.TUTOR)
                    .build();
            sectionUserRoleRepository.save(sectionUserRole);
        }

        log.info("TUTOR 역할 부여 완료 - sectionId: {}, userId: {}", sectionId, userId);
    }

    /**
     * 수강생 역할 부여 (Enrollment와 연동)
     * @param sectionId 수업 ID
     * @param userId 사용자 ID
     */
    @Transactional
    public void assignStudentRole(Long sectionId, Long userId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 이미 역할이 있는지 확인
        Optional<SectionUserRole> existing = sectionUserRoleRepository
                .findBySectionIdAndUserId(sectionId, userId);
        
        if (existing.isPresent()) {
            SectionUserRole role = existing.get();
            // ADMIN이나 TUTOR면 STUDENT로 변경 불가
            if (role.getRole() == SectionUserRole.SectionRole.ADMIN || 
                role.getRole() == SectionUserRole.SectionRole.TUTOR) {
                log.warn("이미 관리자 역할이 있어 STUDENT 역할을 부여하지 않습니다 - sectionId: {}, userId: {}", 
                        sectionId, userId);
                return;
            }
            // 이미 STUDENT면 그대로 유지
            if (role.getRole() == SectionUserRole.SectionRole.STUDENT) {
                return;
            }
        } else {
            // 없으면 새로 생성
            SectionUserRole sectionUserRole = SectionUserRole.builder()
                    .section(section)
                    .user(user)
                    .role(SectionUserRole.SectionRole.STUDENT)
                    .build();
            sectionUserRoleRepository.save(sectionUserRole);
        }

        log.info("STUDENT 역할 부여 완료 - sectionId: {}, userId: {}", sectionId, userId);
    }

    /**
     * 관리자/튜터 역할 제거 → 해당 수업에서 역할을 STUDENT로 변경 (행 삭제 X)
     * @param sectionId 수업 ID
     * @param userId 사용자 ID
     * @param adminUserId ADMIN 권한을 가진 사용자 ID (권한 체크용)
     */
    @Transactional
    public void removeRole(Long sectionId, Long userId, Long adminUserId) {
        // 권한 체크: ADMIN만 역할을 제거할 수 있음
        if (!isAdmin(adminUserId, sectionId)) {
            throw new IllegalArgumentException("역할을 제거할 권한이 없습니다");
        }

        Optional<SectionUserRole> roleOpt = sectionUserRoleRepository
                .findBySectionIdAndUserId(sectionId, userId);

        if (roleOpt.isEmpty()) {
            log.warn("역할 없음 - sectionId: {}, userId: {}", sectionId, userId);
            return;
        }

        SectionUserRole sur = roleOpt.get();

        // ADMIN 역할: 최소 1명의 ADMIN이 필요하므로 마지막 ADMIN은 STUDENT로 변경 불가
        if (sur.getRole() == SectionUserRole.SectionRole.ADMIN) {
            List<SectionUserRole> admins = sectionUserRoleRepository
                    .findAdminsBySectionId(sectionId);
            if (admins.size() <= 1) {
                throw new IllegalArgumentException("최소 1명의 ADMIN이 필요합니다");
            }
        }

        // 삭제하지 않고 STUDENT로 변경
        sur.setRole(SectionUserRole.SectionRole.STUDENT);
        sectionUserRoleRepository.save(sur);
        log.info("역할을 STUDENT로 변경 완료 - sectionId: {}, userId: {}", sectionId, userId);
    }

    /**
     * 특정 수업의 ADMIN 목록 조회
     * @param sectionId 수업 ID
     * @return ADMIN 역할을 가진 사용자 목록
     */
    public List<SectionUserRole> getAdmins(Long sectionId) {
        return sectionUserRoleRepository.findAdminsBySectionId(sectionId);
    }
}

