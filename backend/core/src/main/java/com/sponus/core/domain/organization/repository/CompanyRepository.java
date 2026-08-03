package com.sponus.core.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.organization.company.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
