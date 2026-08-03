package com.sponus.core.domain.organization.repository.querydsl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.sponus.core.domain.organization.Organization;
import com.sponus.core.domain.organization.repository.querydsl.conditions.OrganizationSearchCondition;

public interface OrganizationCustomRepository {

	Page<Organization> searchOrganizationV2(OrganizationSearchCondition condition, Pageable pageable);
}
