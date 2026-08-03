package com.sponus.core.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.organization.OrganizationLink;

public interface OrganizationLinkRepository extends JpaRepository<OrganizationLink, Long> {
}
