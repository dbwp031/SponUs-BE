package com.sponus.core.domain.propose.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.propose.Propose;

public interface ProposeRepository extends JpaRepository<Propose, Long> {

	Boolean existsByOrganization(Organization organization);

	// 동일 공고에 이미 지원했는지 확인 (중복 지원 방지)
	Boolean existsByOrganizationAndAnnouncement(Organization organization, Announcement announcement);

	// Optional<Propose> findByOrganization(Organization organization);

	@Query("SELECT COUNT(p) FROM Propose p WHERE p.organization = :organization AND p.createdAt >= :startOfDay")
	Long countProposesByOrganizationToday(@Param("organization") Organization organization,
		@Param("startOfDay") LocalDateTime startOfDay);

	// N+1 방지를 위해 단일 @ManyToOne 연관은 Fetch Join 으로 함께 로딩한다.
	@Query("""
		SELECT p FROM Propose p
		LEFT JOIN FETCH p.organization
		LEFT JOIN FETCH p.target
		LEFT JOIN FETCH p.announcement
		WHERE p.organization = :organization
		ORDER BY p.createdAt DESC
		""")
	List<Propose> findByOrganizationOrderByCreatedAtDesc(Organization organization, Pageable pageable);

	Long countByOrganization(Organization organization);

	@Query("""
		SELECT p FROM Propose p
		LEFT JOIN FETCH p.organization
		LEFT JOIN FETCH p.target
		LEFT JOIN FETCH p.announcement
		WHERE p.target = :target
		ORDER BY p.createdAt DESC
		""")
	List<Propose> findByTargetOrderByCreatedAtDesc(Organization target, Pageable pageable);

	Long countByTarget(Organization target);
}
