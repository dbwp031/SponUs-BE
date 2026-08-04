package com.sponus.sponusbe.domain.announcement.event;

/**
 * 공고 변경(수정/마감/삭제) 발생. 트랜잭션 커밋 이후 캐시를 무효화하기 위한 도메인 이벤트.
 */
public record AnnouncementChangedEvent(Long announcementId) {
}
