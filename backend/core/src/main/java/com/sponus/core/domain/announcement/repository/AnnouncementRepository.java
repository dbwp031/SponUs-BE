package com.sponus.core.domain.announcement.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.organization.company.Company;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	// 상세 캐시 로드용: 트랜잭션(세션) 밖에서 DTO 변환해도 되도록 company 를 fetch join 으로 함께 로드
	@Query("select a from Announcement a join fetch a.company where a.id = :id")
	Optional<Announcement> findWithCompanyById(@Param("id") Long id);

	Page<Announcement> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);

	Long countByCompany(Company company);

	Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
