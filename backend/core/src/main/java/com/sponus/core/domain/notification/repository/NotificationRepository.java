package com.sponus.core.domain.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sponus.core.domain.notification.Notification;
import com.sponus.core.domain.notification.NotificationStatus;
import com.sponus.core.domain.organization.Organization;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
	List<Notification> findByOrganizationAndAndStatusOrderByCreatedAtDesc(Organization organization,
		NotificationStatus status);
}
