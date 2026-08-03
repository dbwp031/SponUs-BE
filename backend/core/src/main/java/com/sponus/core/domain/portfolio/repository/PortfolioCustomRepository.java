package com.sponus.core.domain.portfolio.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.sponus.core.domain.portfolio.Portfolio;
import com.sponus.core.domain.portfolio.repository.conditions.PortfolioSearchParam;

public interface PortfolioCustomRepository {
	Page<Portfolio> findAllByConditions(PortfolioSearchParam portfolioSearchParam, Pageable pageable);
}
