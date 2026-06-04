package com.sponus.core.domain.announcement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.announcement.Announcement;
import com.sponus.core.domain.organization.company.Company;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	Page<Announcement> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);

	Long countByCompany(Company company);

	Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
