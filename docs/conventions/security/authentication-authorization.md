# 인증과 인가

상태: Draft

## 목적

Account 인증과 Organization 권한을 분리하고, 여러 애플리케이션 인스턴스에서 동일한 인증·인가 결과를 제공한다.

## 주체 구분

- `Account`는 로그인한 사람의 인증 주체다.
- `Organization`은 공고 작성, 지원, 제안, 합의 같은 업무 행위 주체다.
- 현재 v2 범위는 Account 하나당 Organization 하나다.
- Organization 유형은 `CLUB` 또는 `COMPANY`다.
- 인증은 공통이고 Organization 유형에 따라 온보딩과 허용 행위가 달라진다.

## Google 로그인

- 클라이언트가 받은 Google ID Token을 백엔드로 전달한다.
- 백엔드는 서명, issuer, audience, 만료 시각을 검증한다.
- Google이 검증한 이메일을 Account 식별에 사용한다.
- 클라이언트가 전달한 이메일 문자열만 믿고 Account를 생성하지 않는다.
- Google ID Token 원문을 로그 또는 DB에 저장하지 않는다.

## Access Token

- SponUs가 발급한 짧은 수명의 JWT를 사용한다.
- 로그인 성공 응답 본문으로 반환한다.
- 클라이언트는 브라우저 영구 저장소가 아니라 메모리에 보관한다.
- API 요청에는 `Authorization: Bearer {token}`을 사용한다.
- JWT에는 인증에 필요한 최소 Claim만 포함한다.
- Organization 권한 판단의 최종 기준은 서버의 현재 영구 상태다.

## Refresh Token

- 추측 불가능한 Opaque Token을 사용한다.
- `HttpOnly`, `Secure`, `SameSite` 쿠키로 전달한다.
- 서버에는 토큰 원문 대신 해시와 세션 상태를 저장한다.
- 갱신할 때마다 Refresh Token을 회전한다.
- 회전과 이전 토큰 폐기는 하나의 트랜잭션에서 수행한다.
- 모든 인스턴스가 동일한 세션 저장소를 사용한다.
- 로그아웃은 서버 세션을 폐기하고 쿠키를 만료시킨다.

Refresh Token 재사용 탐지와 세션 전체 폐기 정책은 상세 인증 설계에서 확정한다.

## Organization 권한

- Organization ID를 권한 선택용 요청 헤더로 받지 않는다.
- 인증된 Account에서 서버가 Organization을 조회한다.
- Request Body의 작성자 ID나 Organization ID를 신뢰하지 않는다.
- `CLUB`만 지원서를 제출할 수 있다.
- `COMPANY`만 공고를 작성하고 지원서를 미선정할 수 있다.
- Application Service와 Domain에서 대상 리소스에 대한 소유권도 검증한다.

## 상태 코드

- 토큰이 없거나 유효하지 않음: `401 Unauthorized`
- 인증됐지만 Organization 유형이 기능에 맞지 않음: `403 Forbidden`
- 다른 Organization 소유의 구체적인 리소스: `404 Not Found`
- 정지된 Organization의 행위 정책은 Platform Operations 설계에서 확정한다.

다른 조직 리소스를 `404`로 처리하면 ID를 바꿔가며 데이터 존재 여부를 확인하는 공격을 줄일 수 있다.

## 파일 접근

- 파일 다운로드에는 인증이 필요하다.
- 백엔드는 `AssetId`와 연결된 업무 리소스를 기준으로 객체 단위 권한을 확인한다.
- 권한을 확인한 후에만 짧은 만료의 Presigned GET URL을 발급한다.
- 클라이언트가 전달한 S3 Key를 권한 근거로 사용하지 않는다.

## 쿠키와 브라우저 보안

- Refresh Cookie에는 `HttpOnly`, `Secure`, 적절한 `SameSite`, 제한된 Path를 설정한다.
- CORS는 허용된 프런트엔드 Origin만 등록한다.
- Credential Cookie를 사용하는 endpoint의 CSRF 위험을 별도로 검토한다.
- Access Token, Refresh Token, Google ID Token을 URL Query Parameter로 전달하지 않는다.

## 금지 사항

- Access Token을 Local Storage에 영구 보관하는 것을 서버 표준으로 가정
- Refresh Token 원문 DB 저장
- Organization ID 요청 헤더만으로 권한 결정
- 토큰과 쿠키를 로그에 기록
- JWT Claim만 보고 정지된 Organization의 현재 권한을 확정
- 인증 필터와 Controller가 서로 다른 오류 응답 형식 사용

## 테스트 기준

- Google Token의 issuer, audience, expiry 검증을 테스트한다.
- Refresh Token 동시 갱신 시 한 요청만 성공하는지 검증한다.
- 폐기된 Refresh Token 재사용을 검증한다.
- CLUB과 COMPANY의 교차 권한을 테스트한다.
- 다른 조직 리소스가 `404`인지 검증한다.
- 만료·변조 JWT가 `401`인지 검증한다.

## 미결정 사항

- Access Token과 Refresh Token의 정확한 만료 시간
- Refresh Token 재사용 탐지 시 세션 패밀리 전체 폐기 여부
- 로그인·갱신 응답의 온보딩 상태 포함 여부
- Cookie의 구체적인 SameSite 값과 Path
- CORS Origin 목록과 CSRF 방어 방식
