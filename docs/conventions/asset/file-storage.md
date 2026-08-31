# 파일 저장

상태: Draft

## 목적

지원서 첨부와 결과 증빙 파일을 private S3에 안전하게 저장하고, Domain이 저장소 내부 구조에 의존하지 않도록 한다.

## 업로드 흐름

초기 v2는 백엔드가 multipart 파일을 받아 검증한 후 private S3에 저장한다.

```text
Client
  → Backend multipart upload
  → 형식·크기·권한 검증
  → Private S3 저장
  → Asset 메타데이터 저장
  → AssetId 반환
```

- 클라이언트는 업무 리소스 PUT 요청 전에 파일을 업로드한다.
- 업로드 응답으로 받은 `AssetId`만 공고, 지원서, 결과 요청에 포함한다.
- Domain Context는 S3 Bucket과 Object Key를 직접 저장하지 않는다.
- Presigned POST 직접 업로드는 초기 범위에서 제외한다.

## Asset 책임

Asset Context가 소유할 정보 예시:

- `AssetId`
- 저장소 Provider
- 내부 Object Key
- 원본 파일명
- MIME Type
- 파일 크기
- 업로더 Account 또는 Organization ID
- 생성 시각
- 연결 상태 또는 삭제 상태

공고 첨부, 지원서 추가 자료, 결과 증빙이라는 업무 의미는 해당 Domain Context가 소유한다.

## 다운로드 흐름

```text
Client → Backend: AssetId로 다운로드 요청
Backend: 인증과 객체 단위 권한 확인
Backend → Client: 짧은 Presigned GET URL
Client → S3: 파일 다운로드
```

- 로그인한 사용자만 다운로드할 수 있다.
- Backend가 Asset과 업무 리소스의 관계를 기준으로 권한을 검사한다.
- 권한 확인 후 1~3분 만료의 Presigned GET URL을 반환한다.
- S3 객체는 Public ACL을 사용하지 않는다.
- `Cache-Control: no-store`를 적용한다.
- URL에 포함된 서명과 Object Key를 로그에 남기지 않는다.

## 파일 수량

- 지원서 추가 자료는 최대 1개다.
- 결과 증빙은 PDF와 이미지 등의 복수 파일을 허용한다.
- 결과 증빙의 최대 파일 개수, 파일당 크기, 전체 크기는 미결정이다.

## 검증

- 확장자가 아니라 서버가 확인한 MIME Type과 파일 내용을 기준으로 검증한다.
- 허용 형식 목록을 명시적으로 관리한다.
- 파일명에서 경로 문자를 제거하고 표시용 원본 이름과 저장 Key를 분리한다.
- Object Key는 서버가 생성한다.
- 크기 제한은 요청 수신 단계와 S3 저장 단계에서 모두 적용한다.

## 권한 예시

- 지원서 Draft 첨부: 작성 동아리만 조회·교체 가능
- 제출된 지원서 첨부: 당사자인 동아리와 공고 작성 기업이 조회 가능
- 결과 증빙: 해당 SponsorshipCase 당사자와 권한 있는 운영자만 조회 가능
- 운영자 조회와 다운로드는 감사 기록 대상이다.

## 트랜잭션과 고아 파일

S3 업로드와 DB 트랜잭션은 하나의 원자적 트랜잭션이 아니다.

- 업로드 후 업무 리소스에 연결되지 않은 Asset은 임시 상태로 관리하는 방식을 제안한다.
- 일정 시간 동안 연결되지 않은 Asset은 정리 대상이 된다.
- DB 저장 실패 시 즉시 삭제를 시도하되 실패하더라도 후속 정리 작업이 처리할 수 있어야 한다.
- 이미 제출된 불변 문서에 연결된 Asset은 임의 교체하지 않는다.

## 금지 사항

- 로컬 디스크 영구 저장
- Public S3 Bucket 또는 Public ACL
- 클라이언트가 지정한 Object Key 신뢰
- S3 URL을 Domain 식별자로 사용
- 권한 검사 없는 Presigned URL 발급
- 파일 바이너리를 공고·지원서 JSON PUT 요청에 포함
- 확장자만 검사

## 테스트 기준

- 허용·비허용 MIME Type과 크기를 검증한다.
- 다른 조직의 파일 다운로드가 `404`인지 검증한다.
- 만료 시간이 짧게 설정되는지 Adapter 테스트로 검증한다.
- 업무 리소스 연결 전후의 접근 권한을 검증한다.
- S3 성공 후 DB 실패 시 고아 파일 처리 경로를 검증한다.

## 미결정 사항

- 허용 MIME Type 전체 목록
- 지원서 첨부의 파일당 최대 크기
- 결과 증빙 최대 개수, 파일당 크기, 총크기
- 바이러스 검사 도입 여부
- 임시 Asset 만료 시간과 정리 주기
- 운영자 파일 접근 감사 이벤트 형식
