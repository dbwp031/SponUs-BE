package com.sponus.core.domain.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.organization.club.Club;

public interface ClubRepository extends JpaRepository<Club, Long> {
}
