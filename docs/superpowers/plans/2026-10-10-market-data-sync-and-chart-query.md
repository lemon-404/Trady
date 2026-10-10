# 종목 동기화·과거 캔들 적재·차트 조회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 키움 API를 실측해 제공 가능한 종목·봉 주기·과거 기간을 결정하고, 즉시/주기적 적재 실행과 DB 기반 차트 조회 API 한 개를 구현한다.

**Architecture:** 종목 기본 정보 동기화와 과거 캔들 수집을 별도 서비스로 구성하고 하나의 작업에서 순서대로 실행한다. 조회는 DB에만 접근한다. 실행 모드는 api, sync, scheduler로 나누고 실제 제공 범위는 측정 결과를 확정한 뒤 설정으로 주입한다.

**Tech Stack:** Java 17, Spring Boot 4.1.1, Spring MVC, WebClient, Spring Data JPA, PostgreSQL, Gradle Wrapper, JUnit 5, Mockito.

## Global Constraints

- 사용자 요구: “먼저 요청을 해서 키움 api의 요청 한계치에 따라 n개월치 틱범위 캔들, n개의 종목 을 정할 수 있게 하고 그다음 코드 구현”.
- 추가 요구: “적재 실행 모드는 주기적으로 실행될 수 있게 스케쥴링도 되어 있으며 따로 무슨 모드로 실행하면 그 즉시 적재 동기화 모드로 실행”.
- 이번 작업은 계획 작성이다. 실제 API 요청·DB 변경·애플리케이션 구현·Git 커밋은 하지 않는다.
- 측정 → 범위 확정 → 구현 순서를 지킨다. 종목 수·개월 수·지원 주기를 임의로 서비스 확정값으로 정하지 않는다.
- 기존 기획은 15분봉이다. 다른 주기나 복수 주기 지원은 실측 결과와 변경 이유를 제시해 확정한다.
- WebSocket·실시간 봉 집계·프론트엔드·주문/결제·파티셔닝·캐시는 이번 범위에서 제외한다.
- 기존 문서를 임의로 변경하지 않는다. 필요한 새 측정 보고서와 기능 코드만 추가한다.
- API 문서의 단수 리소스 규칙을 따른다. 상세 조회 계약은 기존 문서에 없어 본 계획에서 제안한다.
- 실제 패키지는 trady.com.backend.global.dataloader.kiwoom이다. 이전 대화의 kiwoon 경로를 재생성하지 않는다.
- 키·토큰·Authorization 헤더는 로그, 보고서, 테스트 결과, 커밋에 기록하지 않는다.
- 자동 단위 테스트는 외부 API를 호출하지 않는다. DB 테스트는 별도 PostgreSQL DB에서 실행한다.
- 현재 세션에 실행 스킬 두 개가 등록되어 있지 않다. 계획 실행 시 설치 여부를 확인하고, 없으면 해당 스킬을 사용했다고 주장하지 않고 프로젝트의 TDD·리뷰 절차로 진행하는 대안을 안내한다.

---

## 1. 확인한 현재 상태

- Stock: code 유일성, name, market, createdAt 보유.
- Candle: Stock 참조, interval, time, OHLCV 보유. 유일 키는 (stock_id, interval, candle_time).
- KiwoomStockClient, KiwoonCandleClient 및 응답 DTO는 구현 전.
- TokenProvider는 토큰을 폐기하면 종료 상태로 전환한다. 스케줄 실행마다 폐기하면 다음 회차가 동작하지 않는다.
- TokenLifecycle은 현재 모든 실행에서 토큰을 발급한다. 조회 모드에서는 외부 API를 호출하지 않도록 조건이 필요하다.
- 현재 Lifecycle은 폐기 실패 뒤에도 “폐기 완료”를 기록한다. 모드 연결 작업에서 성공/실패 로그를 바로잡는다.
- JPA 엔티티와 docs/04_database/Trady.sql에는 종목·봉 유일 제약이 있다. 실제 연결 DB의 제약도 실행 전에 검증한다.
- 기존 API 문서는 /{version}/{resource}/{sub-resource}, 단수 리소스 stock/candle을 지정한다. 상세 목록은 비어 있다.

참고 문서:
- docs/02_architecture/시스템 구조도/차트 데이터 수집·조회 구조도 V1.md
- docs/03_api/Trady API 설계 v1.md
- docs/04_database/Trady 데이터베이스 테이블 역할 및 책임.md
- docs/04_database/Trady.sql
- docs/09_schedule/Trady Prototype v0 구현 범위.md

공식 자료:
- [키움 가이드](https://openapi.kiwoom.com/guide/apiguide?dummyVal=0)
- [종목 목록 예제](https://github.com/Kiwoom-Securities/Kiwoom-REST-API/blob/main/examples/국내주식/종목정보/list_domestic_stocks.py)
- [분봉 예제](https://github.com/Kiwoom-Securities/Kiwoom-REST-API/blob/main/examples/국내주식/차트/get_domestic_stock_minute_chart.py)

공식 예제에서 종목 목록은 ka10099, 분봉은 ka10080이다. 분봉 요청은 한 stk_cd와 tic_scope를 사용한다. 여러 종목은 순차 조회하는 방향으로 실험한다. 예제 코드의 페이지 수·sleep 간격은 서버 최대 보관량이나 공식 호출 제한이 아니다.

## 2. 실행 모드

| 모드 | 실행 | 종료 |
|---|---|---|
| api (기본) | DB 기반 차트 HTTP API. 외부 수집·토큰 발급·스케줄 등록 없음 | 서버로 유지 |
| sync | 잠금 → 토큰 준비 → 종목 정보 → 과거 캔들을 즉시 한 번 동기화 | 토큰 폐기 시도 후 종료. 실패 시 실패 종료 |
| scheduler | 시작 시 즉시 한 번 동기화하고, 완료 후 설정 간격마다 같은 작업 반복 | 정상 종료 때 작업 정리 후 토큰 폐기 |

scheduler는 fixed-delay로 한다. 간격은 측정한 전체 작업 시간과 요청 예산에 맞춰 설정한다. cron·휴장일 달력은 도입하지 않는다. 사용자 조회가 수집을 자동 실행하지 않는다.

동일 환경·앱키의 수집 프로세스는 하나만 허용한다. PostgreSQL session advisory lock을 **토큰 발급 전에** 획득하고 프로세스 종료까지 유지한다. 실패하면 토큰 발급·폐기 없이 종료한다. scheduler가 켜져 있을 때 별도 sync는 거절하며 수동 재실행은 scheduler를 정상 종료한 뒤 한다. api 프로세스는 함께 실행 가능하다.

## 3. 파일 지도

계획 실행 시 생성/변경할 경로이며 이번 턴에는 코드 파일을 만들지 않는다.

| 경로 | 책임 |
|---|---|
| backend/tools/Measure-KiwoomMarketData.ps1 | 실제 API 측정 도구 |
| docs/09_schedule/2026-10-10-kiwoom-market-data-measurement.md | 실측 결과·제공 범위 결정 |
| backend/src/main/resources/application-marketdata.properties | 확정된 비밀 아닌 제공 범위·실행 정책 |
| backend/src/main/resources/application.properties | mode 기본값·추가 설정 import |
| backend/src/main/java/trady/com/backend/marketdata/config/MarketDataProperties.java | mode·종목·주기·예산 검증 |
| backend/src/main/java/trady/com/backend/marketdata/config/MarketDataConfig.java | 설정과 스케줄러 등록 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/KiwoomPage.java | 본문+연속조회 메타데이터 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/KiwoomRequestGate.java | 수집 요청 간격 조율 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/stock/client/KiwoomStockClient.java | 종목 목록 페이지 호출 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/stock/dto/KiwoomStockResponse.java | 종목 외부 DTO |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/candle/KiwoomCandleClient.java | 기존 KiwoonCandleClient 이름 수정 후 분봉 호출 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/candle/dto/KiwoomCandleResponse.java | 차트 외부 DTO |
| backend/src/main/java/trady/com/backend/marketdata/stock/StockSyncService.java | 종목 변환·검증·갱신 |
| backend/src/main/java/trady/com/backend/marketdata/stock/StockRepository.java | 종목 조회·upsert |
| backend/src/main/java/trady/com/backend/marketdata/candle/CandleMapper.java | 문자열 가격·시간을 내부 형식으로 변환 |
| backend/src/main/java/trady/com/backend/marketdata/candle/CandleValidator.java | 봉 데이터 검증 |
| backend/src/main/java/trady/com/backend/marketdata/candle/CandleRepository.java | 멱등 적재·기간 조회 |
| backend/src/main/java/trady/com/backend/marketdata/candle/CandleCollectionService.java | bounded 페이지 수집·기간 필터·페이지 저장 |
| backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataSyncService.java | 종목 → 캔들 순서 조율 |
| backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataProcessLock.java | 수집 프로세스 배타 잠금 |
| backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataSyncRunner.java | mode별 즉시 실행 |
| backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataScheduler.java | 주기 등록·취소·실패 회차 기록 |
| backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/auth/KiwoomTokenLifecycle.java | 수집 전용 초기화·종료 순서 |
| backend/src/main/java/trady/com/backend/BackendApplication.java | sync 완료 뒤 컨텍스트 종료 |
| backend/src/main/java/trady/com/backend/marketdata/query/ChartQueryService.java | 조회 조건·DB 읽기 |
| backend/src/main/java/trady/com/backend/marketdata/query/ChartController.java | GET API 한 개 |
| backend/src/main/java/trady/com/backend/marketdata/query/ChartResponse.java | 종목·봉·페이지 응답 |

테스트는 동일 상대 패키지의 backend/src/test/java 아래 둔다. 초기 구현은 기존 엔티티를 그대로 유지하며 upsert는 PostgreSQL ON CONFLICT를 활용한다. 삭제 후 재생성하지 않아 Stock ID와 기존 참조가 유지되어야 한다.

---

## Task 1: API 사전 측정과 범위 확정

**Files:**
- Create: backend/tools/Measure-KiwoomMarketData.ps1
- Create: docs/09_schedule/2026-10-10-kiwoom-market-data-measurement.md

**Interfaces:**
- Consumes: 프로세스 환경변수 KIWOOM_APP_KEY, KIWOOM_SECRET_KEY. 실제 값은 보고서에 기록하지 않는다.
- Produces: 요청 환경·시간 범위·호출 수·종료 이유·제공 범위 결정.

- [ ] **Step 1: 환경과 공식 제한 확인**

다른 수집 프로세스를 종료한 상태에서 한 종목·15분봉부터 검증한다. 최초 자체 예산은 10페이지, 요청 간 최소 2초다. 공식 제한이 더 엄격하면 늘린다. 이 숫자는 API 한계 또는 최종 운영값이 아니다. 제한을 넘길 때까지 요청량을 높이는 실험은 하지 않는다.

- [ ] **Step 2: 독립 측정 도구 작성**

측정 도구는 종목·봉 주기·목표 기간·페이지 예산·요청 간격을 입력받는다. 토큰 발급 후 차트 페이지를 순차 조회하고, 페이지별 건수·시간 범위·중복 수·소요 시간을 기록한다. 목표 도달, 서버의 데이터 종료, 자체 예산 소진, 연속 조회 키 이상을 구분해 종료한다. DB는 변경하지 않으며 종료 시 측정용 토큰 폐기를 시도한다.

- [ ] **Step 3: 명시적으로 실행하고 결과 기록**

한 종목의 15분봉·최근 1개월을 초기 실험 조건으로 요청한다. 페이지별 건수·시간 범위·중복 수·소요 시간 및 종료 이유가 기록되는지 확인한다. 이는 실험 조건이며 서비스 제공 범위를 확정한 것이 아니다.

- [ ] **Step 4: 범위·데이터 의미·종목 정보를 검증**

local-page-budget 종료를 서버 최대 보관량으로 해석하지 않는다. 목표에 도달하지 못하면 공인 예산 안에서 추가 페이지 또는 공식적으로 지원되는 기준일 요청을 검증한다. 한 종목 결과를 전체 종목에 일반화하지 않는다.

공식 가이드의 API 실행 기능 또는 동일한 인증 세션에서 ka10099 종목정보 리스트를 시장별로 조회한다.
Authorization은 현재 세션에서 메모리로 전달하며 문서·콘솔에 붙여넣지 않는다. 다음 페이지가 있으면 응답 cont-yn/next-key를 전달해 후보 종목을 찾는다. 첫 페이지에 없다는 이유로 미지원으로 판정하지 않는다.

보고서 필수 항목:
- 환경·측정 날짜·TR·시장/거래소·종목·봉 주기·수정주가 여부
- 첫 페이지/누적 고유 봉 수·최초/최신 시각·페이지 중복·정렬
- 목표 도달 / 서버 종료 / 자체 예산 종료 / 오류 구분
- 요청 횟수·전체 소요 시간·지연·정제된 오류 코드
- 가격 부호 의미·시간대·봉 시간이 시작/끝 중 무엇인지·진행 중 봉 여부
- trde_qty와 누적 거래량의 차이
- 종목 목록의 code/name/marketCode/marketName 실제 매핑
- API 공식 제한과 이번 측정의 관찰 결과를 별도 표로 기록

- [ ] **Step 5: 제공 범위 확정 게이트**

실측된 후보 조합을 사용자에게 제시해 종목 목록, 봉 주기, 과거 기간, 주기 실행 간격을 확정한다. 15분 이외 지원은 기존 설계와 차이를 명시한다. 기간은 봉 수를 단순 달력 개월로 환산하지 않고 실제 가장 오래된 시각으로 판단한다.

결정 결과로 아래 값이 모두 실제 값으로 채워진 설정 파일을 만든다:
`symbols`, `markets`, `interval-minutes`, `lookback-months`, `adjusted`,
`request-gap`, `max-pages-per-series`, `max-requests-per-run`,
`fixed-delay`, `shutdown-await`, `exchange`, `zone`.

호출 비용은 종목×주기별 실제 페이지 수의 합계에 종목 목록과 인증 여유를 더한다. 자체 예산을 초과한 실험 결과로 “N개월 전부 수집 가능”이라고 발표하지 않는다.

**게이트 전에는 Task 2 이후 제품 코드를 구현하지 않는다.** 응답 의미가 예상과 다르면 해당 계약을 먼저 수정한다. 측정 결과가 필요한 숫자를 지금 가짜 값으로 채우지 않는다.

- [ ] **Step 6: 결과만 지정 커밋**

측정 도구와 결과 보고서만 지정해 커밋한다. 비밀 설정 파일이 포함되지 않았는지 확인한다.

---

## Task 2: 확정 설정·외부 API 계약·페이지 호출

**Files:**
- Create: backend/src/main/resources/application-marketdata.properties
- Modify: backend/src/main/resources/application.properties
- Create: backend/src/main/java/trady/com/backend/marketdata/config/MarketDataProperties.java
- Create: backend/src/main/java/trady/com/backend/marketdata/config/MarketDataConfig.java
- Create: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/KiwoomPage.java
- Create: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/KiwoomRequestGate.java
- Modify: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/stock/client/KiwoomStockClient.java
- Modify: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/stock/dto/KiwoomStockResponse.java
- Rename/implement: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/candle/KiwoonCandleClient.java → KiwoomCandleClient.java
- Modify: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/candle/dto/KiwoomCandleResponse.java
- Test: backend/src/test/java/trady/com/backend/marketdata/config/MarketDataPropertiesTest.java
- Test: backend/src/test/java/trady/com/backend/global/dataloader/kiwoom/KiwoomMarketClientTest.java

**Interfaces:**

- 종목 클라이언트는 시장과 연속 조회 키를 받아 종목 정보 한 페이지를 반환한다.
- 차트 클라이언트는 종목 코드·봉 주기·수정주가 정책·연속 조회 키를 받아 캔들 한 페이지를 반환한다.
- 페이지 결과에는 본문·다음 페이지 존재 여부·연속 조회 키를 함께 보관한다.
- 첫 요청에는 연속 조회 키가 없다. 다음 페이지가 있다고 응답했는데 키가 없으면 실패로 처리한다.
- 종목·차트 요청은 공통 요청 간격 관리 기능을 사용한다.

- [ ] **Step 1: 설정 실패·연속 조회 헤더·업무 오류 테스트 작성**

HTTP 계층은 테스트용 응답으로 대체하고 인증 값은 가짜 문자열을 사용한다. 요청의 API 구분·인증·연속 조회 헤더와 응답 본문·다음 페이지 정보가 정확히 전달되는지 검증한다.
독립 테스트로 HTTP 오류, 실패 응답 코드, 빈 본문, 다음 페이지가 있는데 연속 조회 키가 없는 응답을 검증한다. 요청 JSON 직렬화는 BodyInserter를 테스트용 HTTP sink에 기록하거나 로컬 가짜 서버에서 확인한다. 단순히 DTO 접근자만 검사하지 않는다.

- [ ] **Step 2: 실패 확인**

해당 작업의 테스트를 먼저 실행하고, 아직 구현되지 않은 동작 때문에 실패하는지 확인한다.

기대 결과: 아직 구현되지 않은 기능 때문에 테스트가 실패한다. 컴파일 가능한 최소 골격을 준비한 뒤 동작 검증 자체가 실패하는지도 확인한다.

- [ ] **Step 3: 설정과 호출 구현**

공통 설정의 기본 실행 모드는 api로 지정하고, 확정된 수집 범위 설정 파일을 추가로 불러온다. 기존 비밀 설정 파일 연결은 유지한다.
application-marketdata.properties에는 Task 1에서 확정한 실제 값만 작성한다. 빈 종목 목록, 중복 목록, 허용되지 않은 mode, 0 이하 예산/기간/주기, 문서상 미지원 분 간격은 시작 시 거절한다. 조회 mode에서도 whitelist·주기 정책은 동일하다. mode는 api/sync/scheduler만 허용한다.

외부 응답 DTO는 다음 정보를 내부 필드와 정확히 연결한다:
- stock 응답: return_code, return_msg, list; 각 행 code/name/marketCode/marketName
- candle 응답: return_code, return_msg, stk_cd, stk_min_pole_chart_qry
- candle 행: cntr_tm, open_pric, high_pric, low_pric, cur_prc, trde_qty

요청마다 Provider에서 토큰을 받아 Bearer 헤더를 설정한다. 종목·차트 클라이언트는 같은 RequestGate를 공유한다. RequestGate는 단일 수집 프로세스에서 이전 호출 시작과의 간격을 보장하고, interrupt를 받으면 중단한다. 예외 로그에 HTTP 요청/응답 원문을 넣지 않는다.

응답 본문과 헤더를 함께 읽어 연속 조회 정보를 보존한다. 빈 응답·실패 코드·요청과 다른 종목은 거절하고, 정상 응답만 페이지 결과로 반환한다.
요청을 보내기 전에 공통 요청 간격 관리 기능을 거친다.

오늘은 자동 HTTP 재시도를 붙이지 않는다. timeout·429·5xx·업무 오류는 회차 실패로 기록하고 scheduler 다음 회차 또는 수동 sync로 재시도한다. 이 정책으로 요청 폭증과 이중 적재를 피하고, 실패 응답과 제한 준수를 분명히 한다.

- [ ] **Step 4: 테스트 통과 및 코드 리뷰**
위 테스트를 재실행한다. 인증 비밀 없이 통과해야 한다. KiwoomRequestGate는 실제 긴 sleep 대신 제어 가능한 시각/대기 함수를 사용해 간격과 interrupt를 검증한다.

- [ ] **Step 5: 이 작업 파일만 지정해 커밋**
커밋 메시지: `feat: add bounded Kiwoom market data clients`.
새 DTO·Client·설정·테스트 경로를 명시하여 git add하고 secret 파일이 포함되지 않았는지 확인한다.

---

## Task 3: 종목 정보 저장과 OHLCV 정규화·검증

**Files:**
- Create: backend/src/main/java/trady/com/backend/marketdata/stock/StockSyncService.java
- Create: backend/src/main/java/trady/com/backend/marketdata/stock/StockRepository.java
- Create: backend/src/main/java/trady/com/backend/marketdata/candle/CandleMapper.java
- Create: backend/src/main/java/trady/com/backend/marketdata/candle/CandleValidator.java
- Test: backend/src/test/java/trady/com/backend/marketdata/stock/StockSyncServiceTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/candle/CandleMapperTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/candle/CandleValidatorTest.java

**Interfaces:**
- Consumes: StockClient.fetchPage, 확정된 markets/symbols, 기존 stock 테이블.
- 종목 동기화 서비스는 선정 종목 코드와 저장된 DB ID의 연결 정보를 반환한다.
- 캔들 변환기는 외부 응답의 문자열을 내부 가격·시각·거래량으로 변환한다.
- 캔들 검증기는 잘못된 값을 거절한다.
- 종목 저장소는 코드·이름·시장을 저장하거나 갱신하고 기존 종목 ID를 유지한다.
- 종목 코드로 조회한 결과가 없는 경우도 명시적으로 처리한다.

내부 봉 데이터는 기준 시각, 시가·고가·저가·종가, 거래량을 가진다. API 응답 DTO와 DB 엔티티를 직접 결합하지 않는다.

- [ ] **Step 1: 실패 테스트 작성**
부호를 가격 방향 표기로 정규화하는 테스트는 Task 1에서 그 의미를 확인한 경우에만 승인한다. 실제 음수 값으로 취급해야 하는 데이터라면 검증/변환 계약을 먼저 수정한다. 날짜·가격·거래량의 누락/형식 오류, int 범위 초과, 음수 거래량, low>high도 각각 테스트한다.

종목 동기화는 2페이지에 선정 종목이 나오는 fixture, 비선정 종목 제외, 기존 종목명 변경, 끝까지 없는 선정 종목을 테스트한다. 일부만 발견한 상태를 전체 성공으로 표시하지 않는다.

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 최소 변환·검증 구현**

가격은 측정으로 확인한 부호 규칙에 따라 정규화하고 정수 범위 초과를 거절한다. 시각은 정해진 형식으로 엄격하게 해석하고 거래량은 봉 단위 값으로 변환한다. 가격은 양수, 거래량은 음수 아님을 확인하며 저가가 고가보다 높거나 시가·종가가 저가와 고가 사이에 있지 않으면 거절한다.
변환기와 검증기를 독립 파일로 두고 Spring 빈으로 각각 한 번만 등록한다. 시간대는 Task 1에서 확인한 값으로 통일하고 DB에는 기존 LocalDateTime 규약을 유지한다. interval 경계는 장 시작/봉 표기 규약이 확인되기 전 임의로 분 % interval 계산으로 변경하지 않는다.

종목이 없으면 추가하고, 이미 있으면 이름·시장만 갱신한다. 기존 ID와 생성 시각은 보존한다.
코드·이름·시장 누락, 기존 컬럼 길이 초과는 저장 전에 거절한다. 숫자 종목 코드를 정수로 변환해 앞의 0을 잃지 않는다. KRX/NXT/SOR 코드의 의미를 섞지 않으며 오늘은 확정된 한 거래소·한 수정주가 정책만 저장한다. 현재 유일 키에 이 두 차원이 없으므로 정책을 바꾸면 기존 데이터와 섞이지 않도록 재설계를 먼저 한다.

- [ ] **Step 4: 검증**
종목 갱신 전후 stock_id와 created_at이 같은지 확인한다. 외부 요청 중에는 DB 트랜잭션을 열어 두지 않는다. 다중 페이지를 모두 확인하지 못했으면 “종목 미지원” 대신 “수집 미완료”로 분류한다.

- [ ] **Step 5: 작업 파일과 테스트만 커밋**
커밋 메시지: `feat: sync selected stocks and normalize candles`.

---

## Task 4: 과거 캔들 적재와 반복 실행 정합성

**Files:**
- Create: backend/src/main/java/trady/com/backend/marketdata/candle/CandleRepository.java
- Create: backend/src/main/java/trady/com/backend/marketdata/candle/CandleCollectionService.java
- Create: backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataSyncService.java
- Test: backend/src/test/java/trady/com/backend/marketdata/candle/CandleCollectionServiceTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/candle/CandleRepositoryTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/sync/MarketDataSyncServiceTest.java

**Interfaces:**
- 캔들 저장소는 종목 ID·봉 주기·검증된 봉 목록을 페이지 단위로 저장하고 처리 건수를 반환한다.
- 캔들 수집 서비스는 종목과 주기·수집 구간을 받아 요청 수·저장 건수·최초/최신 시각·목표 도달 여부를 반환한다.
- 전체 동기화 서비스는 한 회차를 실행하고, 부분 실패를 전체 성공으로 처리하지 않는다.
- Consumes: StockSyncService.sync(), CandleClient.fetchPage(), Mapper/Validator, 확정된 scope 설정.

- [ ] **Step 1: 실패 테스트 작성**

실제 PostgreSQL의 유일 제약과 갱신 동작을 검증한다. 테스트 DB는 별도 스키마/트랜잭션 rollback을 사용한다.
fixture로 2페이지 중복, 중간 페이지 실패 뒤 재실행, 페이지 예산 초과, 연속 키 반복, 응답 종목 불일치, 같은 페이지 내 중복, 최신 진행 중 봉 제외를 검증한다. 첫 페이지 저장 후 두 번째 페이지 실패 시 전체를 성공으로 표시하지 않는다.

- [ ] **Step 2: 실패 확인**
DB 테스트에 PostgreSQL이 준비되지 않았으면 통과로 보고하지 않는다. 외부 API는 사용하지 않는다.

- [ ] **Step 3: 페이지별 짧은 트랜잭션 저장**

페이지 전체를 검증한 뒤 짧은 트랜잭션에서 일괄 저장한다. 종목·봉 주기·기준 시각이 같은 행이 있으면 OHLCV를 갱신하고, 없으면 추가한다. 기존 JPA 엔티티는 모델의 기준으로 유지하며 JDBC는 PostgreSQL의 중복 키 갱신 저장이 필요한 부분에 한정한다.
한 페이지 내부 중복 시각은 동일 데이터면 하나로 합치고 서로 값이 다르면 불명확한 덮어쓰기 대신 그 페이지를 실패 처리한다. 호출 재시도/별도 실행의 수정된 봉은 DB upsert로 갱신한다.

수집은 아래 순서를 고정한다:
1. 회차 시작 시 Clock으로 기준 시각을 한 번 잡는다.
2. from = 기준 시각에서 확정 개월 수를 뺀 시각.
3. Task 1의 봉 시간 의미를 기준으로 확정 봉만 포함할 until을 계산한다. 오늘의 장중 진행 봉을 과거 확정 봉으로 저장하지 않는다.
4. 종목×지원 주기를 순차 처리한다. HTTP 호출·JSON 해석·검증은 DB 트랜잭션 밖에서 실행한다.
5. 응답 범위를 확인하고 [from, until)의 봉만 정렬·검증한 뒤 페이지 단위로 저장한다.
6. 가장 오래된 봉이 from 이전에 도달하면 해당 series 조회를 종료한다. 정렬을 API가 보장하지 않는 경우 min timestamp로 판단하며 페이지 순서의 과거 이동도 검증한다.
7. continuation이 끝나면 실제 가용 시작 시각을 기록한다. 목표 범위를 못 채웠는데 성공으로 숨기지 않는다.
8. 반복 키·빈 페이지+continuation·페이지/회차 요청 예산 초과는 회차 실패로 처리한다.
9. 종목 정보 동기화가 완료되기 전에는 캔들 수집을 시작하지 않는다.

초기 구현의 스케줄 회차는 확정된 작은 lookback 범위를 다시 읽어 upsert한다. 최신 시각만 watermark로 삼으면 과거 실패 구간을 놓칠 수 있으므로, 오늘은 영속 checkpoint 없이 정확성을 우선한다. **따라서 매회 전체 비용도 Task 1에서 측정하고 fixed-delay를 결정해야 한다.** 비용이 허용되지 않으면 종목/기간을 줄이거나 별도 checkpoint 설계를 사용자에게 제시한 후 계획을 수정한다. 임의로 일부만 갱신해 동일 보장을 주장하지 않는다.

- [ ] **Step 4: 테스트 통과·실제 DB 제약 확인**

동일 수집을 두 번 실행해 stock/candle 개수가 불필요하게 늘지 않는지, 수정된 봉은 갱신되는지 확인한다. 데이터 없는 휴장일을 수집 실패로 단정하지 않는다. oldest/newest는 데이터 경계이지 구간 내 모든 봉의 완전성을 보증하는 값이 아니다.

- [ ] **Step 5: 지정 파일 커밋**
커밋 메시지: `feat: add idempotent historical candle collection`.

---

## Task 5: 즉시 실행·주기 실행·수집 종료 연결

**Files:**
- Create: backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataProcessLock.java
- Create: backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataSyncRunner.java
- Create: backend/src/main/java/trady/com/backend/marketdata/sync/MarketDataScheduler.java
- Modify: backend/src/main/java/trady/com/backend/marketdata/config/MarketDataConfig.java
- Modify: backend/src/main/java/trady/com/backend/global/dataloader/kiwoom/auth/KiwoomTokenLifecycle.java
- Modify: backend/src/main/java/trady/com/backend/BackendApplication.java
- Test: backend/src/test/java/trady/com/backend/marketdata/sync/MarketDataModeTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/sync/MarketDataProcessLockTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/sync/MarketDataSchedulerTest.java
- Modify/Test: backend/src/test/java/trady/com/backend/global/dataloader/kiwoom/auth/KiwoomTokenLifecycleTest.java

**Interfaces:**
- 프로세스 잠금은 수집 시작 전에 획득하고 종료 시 해제한다.
- 스케줄러는 주기 작업을 등록하고, 종료 시 새 회차를 막은 뒤 실행 중 작업을 기다린다.
- 실행 모드 담당자는 즉시 동기화와 주기 실행을 연결한다.
- 각 실행은 동일한 한 회차 동기화 서비스를 사용한다.
- 기존 Provider의 토큰 초기화·폐기를 재사용하고 매 회차마다 Provider를 재생성하지 않는다.

- [ ] **Step 1: 부팅·모드·종료 테스트를 먼저 작성**

기존 SpringApplication 기반 테스트 방법을 유지한다. DB/API는 대체하되 실제 컨텍스트의 mode 조건과 종료 순서를 검증한다.

필수 시나리오:
- 기본 api: initialize/수집/스케줄 등록 호출 0회.
- sync: 잠금→토큰 초기화→수집→폐기→잠금 해제 순서, 완료 후 종료.
- sync 실패: 실패 종료, 이미 얻은 토큰은 폐기 시도, 실패를 성공 exit로 감추지 않음.
- scheduler: 즉시 1회, 그 후 fake scheduler의 fixed-delay 콜백으로 같은 서비스 호출.
- scheduler 작업 실패 후 다음 회차 동작, 두 회차 동시 실행 없음.
- shutdown: 새 회차 취소→실행 작업 종료 대기→토큰 폐기→잠금 해제.
- 잠금 실패: 토큰 초기화·폐기 둘 다 0회.
- 같은 PostgreSQL의 두 독립 connection 중 하나만 잠금 성공.

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: 모드 및 수명 주기 구현**

수집 전용 구성은 sync 또는 scheduler 모드에서만 활성화한다.
수집 전용 runner와 lifecycle에 적용한다. 설정 검증 빈은 api에서도 등록해 오타 mode를 거절한다.

애플리케이션 진입점에서는 실제 실행 모드가 sync인 경우 한 회차 완료 후 Spring 컨텍스트를 종료한다.
수집 실패는 ApplicationRunner에서 예외를 전파한다. 정상 run 반환 전에 수집이 끝나야 하며 비동기로 던져 놓고 종료하지 않는다.

sync와 scheduler는 웹 서버 없이 실행하도록 설정한다. api만 웹 서버를 연다. TaskScheduler는 scheduler mode에만 한 스레드로 등록한다. 초기 수집이 끝난 뒤 fixed-delay를 등록하여 처음 두 회차가 겹치지 않게 한다.

MarketDataProcessLock은 DataSource에서 가져온 **전용 connection을 보유**한다. JdbcTemplate의 서로 다른 pool connection에서 lock/unlock하지 않는다.
고정 키는 단일 DB의 Trady market-data 수집 작업을 의미한다. connection은 획득 실패/종료 때 반드시 반환한다. 잠금 상태·반환값을 검사한다. 서로 다른 DB에서 같은 키움 앱키를 사용하는 병렬 실행까지 보호하는 장치는 아니므로 오늘은 금지한다.

Lifecycle과 Runner의 시작 순서를 명시한다. 권장 순서:
1. 수집 프로세스 잠금을 획득한다.
2. 수집 모드의 토큰을 초기화한다.
3. 종목·캔들 동기화를 실행한다.

종료는 애너테이션 처리 순서에 대한 추측에 의존하지 않는다. 하나의 종료 조율 경로에서 스케줄 중단 및 실행 작업 대기 → 토큰 폐기 → 잠금 해제를 수행한다. 기존 Lifecycle 종료 처리를 이 경로로 연결하고 토큰 폐기 담당은 한 곳으로 제한한다. 완료 로그는 성공한 경우에만 기록한다.

스케줄러가 없어도 동일 종료 조율이 동작하도록 scheduler 참조는 ObjectProvider로 선택적으로 받는다. 종료 대기 시간은 확정된 shutdown-await를 사용하고, 작업은 페이지 사이에 중단 상태를 확인한다. 대기 한도를 초과하면 실패를 기록하고 취소를 시도하며 원격 폐기를 보장했다고 로그하지 않는다.

- [ ] **Step 4: 관련 테스트 통과·외부 호출 없는 api 부팅 검증**

기존 테스트가 직접 Lifecycle을 import하는 경우에도 mode=sync를 명시하고 새 의존성은 mock으로 제공한다. 빈 등록 조건 때문에 테스트가 아무 동작 없이 통과하지 않도록 initialize 호출 검증을 유지한다.

- [ ] **Step 5: 지정 파일 커밋**
커밋 메시지: `feat: add immediate and scheduled market data modes`.

---

## Task 6: 클라이언트용 차트 조회 API 한 개

**Files:**
- Create: backend/src/main/java/trady/com/backend/marketdata/query/ChartController.java
- Create: backend/src/main/java/trady/com/backend/marketdata/query/ChartQueryService.java
- Create: backend/src/main/java/trady/com/backend/marketdata/query/ChartResponse.java
- Modify: backend/src/main/java/trady/com/backend/marketdata/candle/CandleRepository.java
- Test: backend/src/test/java/trady/com/backend/marketdata/query/ChartControllerTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/query/ChartQueryServiceTest.java
- Test: backend/src/test/java/trady/com/backend/marketdata/query/ChartReadOnlyIntegrationTest.java

**Interfaces:**
- GET /v1/stock/candle?code=005930&interval=15&from=2026-10-01T00:00:00&to=2026-10-03T00:00:00&limit=1000
- 선택 파라미터 after: 직전 응답의 nextAfter를 그대로 전달.
- 조회 서비스는 종목 코드·봉 주기·시작/종료 시각·페이지 기준 시각·조회 개수를 받아 차트 응답을 반환한다.
- 캔들 저장소는 해당 종목과 주기의 지정 구간을 시간순으로 조회한다.
- 조회 구간은 시작 시각을 포함하고 종료 시각을 제외한다. 시간순으로 정렬하며 이어 읽을 기준 시각보다 이후의 봉만 반환한다.
- limit 기본 1000, 허용 1~5000은 API 응답 보호 정책 제안이다. 키움 API 제한 수치가 아니다.
- 정책상 미제공 종목·미등록 종목 404, 미지원 주기/역전 구간/잘못된 날짜/범위 밖 after/잘못된 limit 400.
- 등록 종목이지만 저장 데이터가 없는 구간은 HTTP 200과 빈 봉 목록을 반환한다.
- 조회에서 종목/차트 외부 API를 호출하거나 누락분을 자동 수집하지 않는다.

**응답 정보:** 종목 코드·이름·시장, 봉 주기, 시간대, 요청한 시작/종료 시각, OHLCV 목록, 다음 페이지 존재 여부와 이어 읽을 기준 시각을 포함한다.

- [ ] **Step 1: HTTP 및 조회 실패 테스트 작성**

MockMvc standalone으로 Controller를 검증한다. 실제 DB 검증은 별도 integration test에서 한다.
서비스 테스트에서는 실제 query()에 역전 구간을 넣어 Repository 호출이 없음을 검증한다. 그 외 종목 분리·주기 분리·경계 시각 제외·ASC 순서·limit+1 기반 hasMore·페이지 간 중복 없음·미등록/빈 구간을 각각 검증한다. 첫 1000개만 반환하고 나머지를 조용히 버리지 않는다.

- [ ] **Step 2: 실패 확인**

- [ ] **Step 3: Controller와 DB 조회 구현**

서비스는 읽기 전용 트랜잭션에서 제공 대상과 저장된 종목 정보를 확인한다. 봉 주기는 DB의 분봉 표기와 일치시킨다. 이어 읽을 시각이 있으면 요청 구간 안에 있어야 하며, 시작 시각이 종료 시각보다 이른지와 조회 개수 제한을 항상 검증한다.

조회는 종목 ID·봉 주기로 제한하고 시작 시각은 포함, 종료 시각은 제외하여 시간순으로 정렬한다.
이어 읽을 기준 시각이 있으면 그 시각보다 이후인 봉만 조회한다. 사용자 입력은 SQL 문자열에 이어 붙이지 않고 매개변수로 전달한다. 다음 페이지 존재 여부를 확인하기 위해 요청 개수보다 한 건 더 읽는다.

요청 개수보다 많은 결과가 있으면 다음 페이지가 있다고 표시한다. 요청한 개수만 반환하고 마지막 봉의 시각을 다음 조회 기준으로 제공한다. 다음 페이지가 없으면 이어 읽을 기준값을 비워 둔다. 종목 메타데이터는 응답에 한 번만 담고 각 봉마다 반복하지 않는다.

zone은 확정된 시간대 이름을 반환한다. 저장 데이터의 시간대 해석과 일치시킨다. API는 빈 구간이 휴장인지 미수집인지 단정하지 않는다. 현재 범위에 완전성/상태 API는 없으므로 README/측정 보고서에서 조회 데이터가 저장된 과거 봉임을 설명한다.

- [ ] **Step 4: 조회의 외부 의존성 차단 확인**

api 모드에서 잘못된 키움 주소를 주어도 DB 조회가 성공하고 AuthClient/StockClient/CandleClient가 호출되지 않아야 한다. 이것은 빈이 존재하지 않아야 한다는 뜻이 아니라 외부 호출이 없어야 한다는 뜻이다. 일반 api 기동에 필요한 DB 설정은 정상 값이어야 한다.

- [ ] **Step 5: 지정 파일 커밋**
커밋 메시지: `feat: serve stored stock candles through query API`.

---

## Task 7: 전체 실행 검증·운영 절차·최종 리뷰

**Files:**
- Test: backend/src/test/java/trady/com/backend/marketdata/MarketDataFlowTest.java
- Update: docs/09_schedule/2026-10-10-kiwoom-market-data-measurement.md
- Review: Task 2~6의 변경 파일만

**Interfaces:**
- Consumes: 확정된 설정 파일, PostgreSQL 테스트 DB, 가짜 외부 응답.
- Produces: 즉시 실행/재실행/스케줄/조회 검증 결과와 확인한 제한 목록.

- [ ] **Step 1: 전체 흐름을 가짜 외부 응답과 실제 테스트 DB로 검증**

필수 assertions:
1. 종목 정보 이름/시장이 저장되고 해당 ID의 봉이 조회된다.
2. 동일 응답 재적재 시 유일 키별 행 수가 증가하지 않는다.
3. 두 번째 페이지 실패 후 재실행으로 누락이 채워진다.
4. 수집 과정 없이 api만 재시작해도 저장된 결과를 조회한다.
5. scheduler 실패 회차 후 다음 회차가 실행되고 로그에 성공으로 표시되지 않는다.
6. shutdown 시 진행 중 수집보다 먼저 토큰을 폐기하지 않는다.
7. 다른 수집 프로세스가 lock을 가진 동안 추가 프로세스는 토큰 API를 호출하지 않는다.

- [ ] **Step 2: 자동 검증**
백엔드의 새 단위 테스트와 가짜 서버 테스트를 실행해 통과하는지 확인한다. PostgreSQL 테스트는 준비된 테스트 DB에서 실행한다. 기본 BackendApplicationTests가 라이브 토큰을 발급하지 않는지도 api 모드 변경 후 확인한다. 테스트 환경이 없거나 기존 무관한 실패가 있으면 원인을 구분해 보고하고 성공으로 표시하지 않는다.

- [ ] **Step 3: 실측으로 확정한 실제 소규모 대상 적재**
웹 서버를 열지 않는 sync 모드로 실행한다. 종목 동기화→캔들 적재→성공/실패 요약→토큰 폐기 시도→프로세스 종료 순서를 확인한다. 같은 모드로 다시 실행해 중복이 생기지 않는지 확인한다. 실제 호출은 Task 1과 같이 승인된 환경/예산에서만 수행한다.

- [ ] **Step 4: 주기 모드 실행**
웹 서버를 열지 않는 scheduler 모드로 실행한다. 즉시 1회 동기화한 뒤 설정된 간격으로 반복되는지 확인한다. 종료는 Ctrl+C로 요청한다. 강제 kill·전원 차단 시 원격 토큰 폐기는 보장하지 않는다. 유실된 session lock은 DB 연결 종료로 해제된다.

- [ ] **Step 5: API 모드 실행 및 조회**
별도 터미널에서 api 모드를 실행한다. 조회 API의 종목·주기·기간에는 측정 결과에 따라 실제로 적재한 값을 사용한다. 정상 요청에서 HTTP 200과 종목 정보·저장된 봉 목록이 반환되는지 확인한다. hasMore이면 nextAfter로 이어 읽는다. 없는 데이터가 있다고 가정해 assert하지 않는다.

- [ ] **Step 6: 리뷰**
requesting-code-review 스킬로 요구사항·트랜잭션 범위·HTTP 오류·중복·시간대·종료 순서를 검토한다. 문제를 수정하면 해당 테스트를 다시 실행한다. 이번 계획 문서 자체는 writing-plans의 지시에 따라 자기 검토하며 별도 리뷰 에이전트를 부르지 않는다.

- [ ] **Step 7: 결과 기록·지정 파일 커밋**
실측 결과 보고서에 실제 수행 명령·결과·제공 범위·제약·남은 항목을 기록한다. 계획 실행 중 실제 수치가 바뀌면 근거를 함께 남긴다. 기존 아키텍처/API 설계 문서 변경이 필요하면 차이를 먼저 알리고 별도 요청 없이 덮어쓰지 않는다.
커밋 메시지: `test: verify market data sync and query flow`.

## 4. 구현 전 확정할 계약과 의도적인 범위 제한

1. 종목 수/봉 주기/개월 수/fixed-delay는 Task 1에서 결정한다. 문서 작성 시 실제 요청을 실행한 것으로 간주하지 않는다.
2. 오늘은 한 거래소·한 수정주가 정책이다. 현재 DB 키로 KRX/NXT/SOR나 수정/비수정 봉을 함께 저장하지 않는다.
3. 조회 파라미터·응답 모델·페이지 크기·HTTP 오류 정책은 본 계획의 제안이다. 사용자 검토 후 확정한다.
4. api와 scheduler를 서로 다른 프로세스로 실행할 수 있지만 동일 앱키의 수집 프로세스는 한 개다.
5. 첫 버전은 작은 확정 범위의 재적재 방식이다. 최신 timestamp만으로 누락이 없다고 주장하지 않는다.
6. 스케줄링과 WebSocket은 다른 기능이다. 이번 완료 기준에는 실시간 수신이 없다.

## 5. 자기 검토 결과

| 사용자 요구 | 대응 작업 |
|---|---|
| 먼저 실제 요청으로 API 범위 파악 | Task 1, 로컬 예산과 서버 제한 분리 |
| 종목 수·개월·주기 결정 후 구현 | Task 1의 필수 게이트 |
| 종목 기본 정보 동기화 | Task 2~3 |
| 과거 차트 적재·재실행 | Task 4 |
| 즉시 실행하는 mode | Task 5 sync |
| 주기적 스케줄링 | Task 5 scheduler, 최초 즉시 실행 포함 |
| 클라이언트 조회 API 한 개 | Task 6 |
| 기존 수집/조회 책임 분리 | api는 DB만 조회, 토큰 초기화 조건부 |
| 데이터 정합성 | 종목 ID 보존, 봉 유일 키 upsert, 페이지 실패 복구 |
| 검증과 리뷰 | 각 Task의 RED/GREEN, Task 7 통합 검증 |

실측에 의존하는 값은 지금 단정하지 않았고, 결정 절차와 후속 구현의 입력으로 명시했다. 응답 항목·서비스 책임·설정 검증 기준은 부록과 함께 대조한다. 구현 단계에서 실측이 계약을 바꾸면 게이트에서 해당 Task를 갱신한다.

## 6. 실행 인계

계획 승인 후 Task 1만 먼저 실행하고 측정 결과로 제공 범위를 결정한다. 실제 API 호출 환경/인증 정보가 준비되지 않았으면 모의 측정값으로 다음 게이트를 통과하지 않는다.

실행 방식:
1. 작업별 별도 에이전트 + 단계별 리뷰. 사용자 선택 시에만 위임한다.
2. 현재 세션에서 작업 순서대로 TDD와 리뷰 수행.

이 문서는 오늘의 목표에 대한 작업 순서와 계약이다. 전체 구현이 오늘 반드시 완료된다는 시간 보장은 하지 않는다.

## 부록 A. 응답 데이터와 설정 검증 기준

종목 응답에서는 응답 코드·메시지와 종목 목록을 읽고, 각 종목의 코드·이름·시장 코드·시장명을 내부 데이터에 연결한다.

차트 응답에서는 응답 코드·메시지, 종목 코드, 봉 목록을 읽는다. 각 봉의 기준 시각, 시가·고가·저가·종가, 봉 거래량을 변환하며 누적 거래량과 혼동하지 않는다. 필드의 실제 의미는 Task 1의 측정 결과로 확인한다.

실행 설정에는 모드, 선정 종목과 시장, 지원 봉 주기, 과거 수집 개월 수, 수정주가 정책, 요청 간격, 페이지·회차 요청 예산, 스케줄 간격, 종료 대기 시간, 거래소와 시간대를 포함한다.

설정 검증 기준:

- 실행 모드는 api, sync, scheduler 중 하나여야 한다.
- 종목·시장·봉 주기 목록은 비어 있거나 중복되면 안 된다.
- 종목 코드와 시장 값에는 빈 값이 없어야 한다.
- 봉 주기는 실제 지원 여부를 확인하고 제공 대상으로 확정한 값만 사용한다.
- 수집 기간, 요청·페이지 예산, 요청 간격, 실행 주기와 종료 대기 시간은 양수여야 한다.
- 거래소와 시간대는 수집 데이터 및 DB 식별 정책과 일치해야 한다.

KRX는 현재 단계의 단일 거래소 제안이다. 다른 거래소를 선택하면 실측 결과와 DB 식별 정책을 다시 검토한다. API가 지원하는 모든 봉 주기를 서비스에서 제공할 필요는 없다.

테스트에서는 별도의 소규모 대상과 고정 시각을 사용하여 결과를 재현할 수 있게 한다. 테스트용 종목 수·기간·요청 예산을 운영 정책으로 간주하지 않는다.

봉 시각이 구간 시작인지 끝인지 확인하기 전에는 진행 중 봉을 제외하는 계산을 임의로 확정하지 않는다.
