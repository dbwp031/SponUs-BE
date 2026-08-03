package com.sponus.core.domain.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.portfolio.PortfolioImage;

public interface PortfolioImageRepository extends JpaRepository<PortfolioImage, Long> {
}
