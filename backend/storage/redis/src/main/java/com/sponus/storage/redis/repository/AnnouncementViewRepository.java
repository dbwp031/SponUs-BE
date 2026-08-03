package com.sponus.storage.redis.repository;

import org.springframework.data.repository.CrudRepository;

import com.sponus.storage.redis.entity.AnnouncementView;

public interface AnnouncementViewRepository extends CrudRepository<AnnouncementView, String> {

}
