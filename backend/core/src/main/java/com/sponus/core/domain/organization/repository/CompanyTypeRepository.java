package com.sponus.core.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.organization.company.CompanyType;

public interface CompanyTypeRepository extends JpaRepository<CompanyType, Long> {
}
