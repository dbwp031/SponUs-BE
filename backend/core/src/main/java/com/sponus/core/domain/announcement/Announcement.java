package com.sponus.core.domain.announcement;

import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.sponus.core.domain.common.BaseEntity;
import com.sponus.core.domain.organization.company.CollaborationTypeEnum;
import com.sponus.core.domain.organization.company.Company;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업(Company)이 소유하는 제휴 공고글. 동아리(Club)가 이 공고에 지원(Propose)한다.
 */
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "announcement")
public class Announcement extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "announcement_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
	private Company company;

	@Column(name = "announcement_title", nullable = false)
	private String title;

	@Column(name = "announcement_content", columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "collaboration_type")
	private CollaborationTypeEnum collaborationType;

	@Column(name = "announcement_start_date")
	private LocalDate startDate;

	@Column(name = "announcement_end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@ColumnDefault("'RECRUITING'")
	@Column(name = "announcement_status", nullable = false)
	private AnnouncementStatus status;

	@Column(name = "view_count")
	@ColumnDefault("0")
	private int viewCount;

	public boolean isRecruiting() {
		return this.status == AnnouncementStatus.RECRUITING;
	}

	public void close() {
		this.status = AnnouncementStatus.CLOSED;
	}

	public void update(String title, String content, CollaborationTypeEnum collaborationType,
		LocalDate startDate, LocalDate endDate) {
		this.title = title == null ? this.title : title;
		this.content = content == null ? this.content : content;
		this.collaborationType = collaborationType == null ? this.collaborationType : collaborationType;
		this.startDate = startDate == null ? this.startDate : startDate;
		this.endDate = endDate == null ? this.endDate : endDate;
	}
}
