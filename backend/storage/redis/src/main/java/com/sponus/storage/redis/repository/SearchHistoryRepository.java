package com.sponus.storage.redis.repository;

import org.springframework.data.repository.CrudRepository;

import com.sponus.storage.redis.entity.SearchHistory;

public interface SearchHistoryRepository extends CrudRepository<SearchHistory, Long> {
}
