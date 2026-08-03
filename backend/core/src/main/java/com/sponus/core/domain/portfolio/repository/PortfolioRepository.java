package com.sponus.core.domain.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.portfolio.Portfolio;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long>, PortfolioCustomRepository {
}
