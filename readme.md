# Your Unique Memory Expedition(YUME) [Back]

## 로컬 실행 설정

프로젝트 루트에 Git으로 추적되지 않는 `.env` 파일을 만들고 다음 값을 설정합니다.  
`application.yml`에는 실제 비밀값 대신 환경변수 참조만 포함되어 있습니다.

일본 열차 경로는 RapidAPI의 `NAVITIME Route(totalnavi)`를 사용합니다.  
무료 Basic 플랜은 월 500회까지 사용할 수 있습니다. 

- 도보, 택시, 자전거, 오토바이: Google Routes API
- 열차: NAVITIME Route(totalnavi)
- 버스: Google 지도에서 확인 후 수동 입력

```text
DB_HOST_USERNAME=your_database_username
DB_HOST_PASSWORD=your_database_password
JWT_SECRET_KEY=at_least_32_bytes_of_random_secret
AUTH_SIGNUP_ENABLED=true
SERVER_FORWARD_HEADERS_STRATEGY=native
GOOGLE_MAPS_ROUTES_API_KEY=your_server_routes_api_key
NAVITIME_RAPID_API_KEY=your_rapidapi_key
```

데이터베이스 주소가 기본값인 `jdbc:mysql://localhost:3306/yume`과 다르면
`DB_HOST_URL`도 설정합니다.  
정적 지도 이미지에 별도 키를 사용할 경우 `GOOGLE_MAPS_API_KEY`를 추가할 수 있습니다.
신규 가입을 막으려면 `AUTH_SIGNUP_ENABLED=false`로 설정합니다.
리버스 프록시 환경에서는 기본값인 `native`가 컨테이너의 신뢰 가능한 프록시 규칙으로
전달 헤더를 처리한 뒤 로그인·회원가입 제한에 실제 클라이언트 주소를 사용합니다.

## 운영 확인

- 상태 확인: `GET /actuator/health`
- 모든 API 응답에는 `X-Request-ID` 헤더가 포함됩니다.
- 오류 화면에 표시되는 요청 ID로 백엔드 로그를 검색할 수 있습니다.
- 로그에는 API 키와 JWT를 직접 기록하지 않습니다.
- GitHub Actions에서는 Flyway migration 적용·검증 후 테스트를 실행합니다.


## 프로젝트 개요
일본 여행 일정을 구글 스프레드시트로 관리하며 느꼈던 아카이빙의 한계와 불편함을 해소하기 위해 시작한 프로젝트입니다.  
단순한 일정 기록을 넘어 방문 장소의 히스토리, 기념품 구매 현황, 고슈인 수집 상태 등을 체계적으로 관리하고,  
Spring Boot의 기술적 역량을 깊이 있게 다지는 것을 목표로 했습니다.

## 개발 진행 방식
~~- 자가 구현 후 AI 검수  
  Java 기반 Spring Boot와 MySQL로 비즈니스 로직을 직접 설계 및 구현한 후,  
  Gemini AI와의 코드 리뷰를 통해 구조적 결함을 보완했습니다.~~

 - AI 구현 후 검수
  Codex를 활용해 불편했거나 추가하고싶은 부분을 개선한 후  
  결과물을 검수하고 코드를 확인하며 검토함

## 주요 중점
- 여행-계획-일정-장소의 유연한 도메인 설계  
  여행(Plan)-계획(PlanDay)-일정(DaySchedule)-장소(Spot)로 이어지는 구조로 여행정보를 체계화하고  
  구글 place_id로 장소의 중복을 방지하고 웹사이트, 운영시간, 개인 메모 등의 상세 데이터를 축적할 수 있도록 함

  
- 수집품(고슈인, 기념품) 중심의 관리  
  기념품 종류(kind)와 수집 상태(status), 수량 및 가격 등을 기념품 각각에 대해 기록할 수 있도록 하여  
  그동안 수집한 기념품들을 체계적으로 관리하고자 하였음

## 향후 계획

1. 비회원 일정 공유 및 실시간 협업  
  UUID 기반의 공유 토큰을 발급하여 가입 없이도 지인과 일정을 공유하고,  
  수정사항의 실시간 적용 및 접속자 확인 기능을 추가할 계획


2. 일정 불러오기 개선
  현재는 서비스 자체의 일정을 export한 json과 개인적으로 사용하던 양식의 엑셀파일만 import 되는데  
  다양한 양식도 '계획'이기만 하다면 가져올 수 있도록 대응범위를 넓힐 생각임
