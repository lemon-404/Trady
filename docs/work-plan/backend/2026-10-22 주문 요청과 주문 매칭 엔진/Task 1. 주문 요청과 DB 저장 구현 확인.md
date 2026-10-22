# 주문 요청과 DB 저장 구현 확인

- 작성 날짜: 2026-10-22
- 관련 Task: Task 1. 주문 요청을 받아 DB에 저장
- 검증 대상: 현재 회원 ID 주입, 매수·매도 요청, 계좌·종목 조회, 주문 생성과 저장 경로

## 1. 확인 목적

주문 요청이 현재 회원의 계좌 및 종목과 연결되어 초기 상태의 주문으로 저장되는 구현 구조를 확인한다. 예외 처리 구현과 API 문서 작성은 이 기록에서 제외한다. Task 2의 매칭 엔진, 체결 처리와 결제도 포함하지 않는다.

## 2. 참고 자료

| 자료 | 경로 | 참고 내용 |
|---|---|---|
| 관련 작업 문서 | [주문 요청과 주문 매칭 엔진](../2026-10-22%20주문%20요청과%20주문%20매칭%20엔진.md) | Task 1의 주문 요청·회원 식별·계좌 및 종목 조회·DB 저장 범위 |

외부 공식 자료를 추가로 확인하지 않고 현재 코드와 설정을 기준으로 분석했다.

## 3. 확인 방법

코드를 정적으로 확인했다. 이번 기록 작성 과정에서 빌드, HTTP 요청, 테스트, 실제 DB 조회·저장은 실행하지 않았다. 사용자로부터 실제 저장 성공 결과를 전달받은 근거도 없어 실행 성공으로 판정하지 않는다.

| 코드·설정 경로 | 확인 내용 |
|---|---|
| [backend/src/main/java/trady/com/backend/global/auth/CurrentMemberId.java](../../../../backend/src/main/java/trady/com/backend/global/auth/CurrentMemberId.java) | 파라미터 애노테이션과 향후 인증 정보 전환 지점 |
| [backend/src/main/java/trady/com/backend/global/auth/HeaderMemberIdArgumentResolver.java](../../../../backend/src/main/java/trady/com/backend/global/auth/HeaderMemberIdArgumentResolver.java) | 헤더 읽기, `Long` 인자 지원과 변환 |
| [backend/src/main/java/trady/com/backend/global/auth/LocalAuthWebMvcConfig.java](../../../../backend/src/main/java/trady/com/backend/global/auth/LocalAuthWebMvcConfig.java) | `local` 프로필의 resolver 등록 |
| [backend/src/main/java/trady/com/backend/order/controller/OrderController.java](../../../../backend/src/main/java/trady/com/backend/order/controller/OrderController.java) | 매수·매도 URL, 검증 진입점과 서비스 호출 |
| [backend/src/main/java/trady/com/backend/order/dto/OrderRequest.java](../../../../backend/src/main/java/trady/com/backend/order/dto/OrderRequest.java) | 주문 입력 필드와 검증 선언 |
| [backend/src/main/java/trady/com/backend/order/service/OrderService.java](../../../../backend/src/main/java/trady/com/backend/order/service/OrderService.java) | 계좌·종목 조회, 방향 결정, 주문 생성·저장과 트랜잭션 |
| [backend/src/main/java/trady/com/backend/order/entity/Order.java](../../../../backend/src/main/java/trady/com/backend/order/entity/Order.java) | DB 매핑, 초기 상태와 시간 생성 |
| [backend/src/main/java/trady/com/backend/order/Enum/Side.java](../../../../backend/src/main/java/trady/com/backend/order/Enum/Side.java) | BUY·SELL 정의 |
| [backend/src/main/java/trady/com/backend/order/Enum/OrderType.java](../../../../backend/src/main/java/trady/com/backend/order/Enum/OrderType.java) | LIMIT·MARKET 선언 순서 |
| [backend/src/main/java/trady/com/backend/order/Enum/OrderStatus.java](../../../../backend/src/main/java/trady/com/backend/order/Enum/OrderStatus.java) | ACCEPTED 상태 정의 |
| [backend/src/main/java/trady/com/backend/order/repository/OrderRepository.java](../../../../backend/src/main/java/trady/com/backend/order/repository/OrderRepository.java) | JPA 저장 인터페이스 |
| [backend/src/main/java/trady/com/backend/account/service/AccountQueryService.java](../../../../backend/src/main/java/trady/com/backend/account/service/AccountQueryService.java) | `Long memberId`로 계좌 조회하는 경로 |
| [backend/src/main/java/trady/com/backend/account/repository/AccountQueryRepository.java](../../../../backend/src/main/java/trady/com/backend/account/repository/AccountQueryRepository.java) | 계좌 조회 인자 타입 |
| [backend/src/main/java/trady/com/backend/stock/service/StockQueryService.java](../../../../backend/src/main/java/trady/com/backend/stock/service/StockQueryService.java) | 종목 코드 조회 경로 |
| [backend/src/main/java/trady/com/backend/stock/repository/StockQueryRepository.java](../../../../backend/src/main/java/trady/com/backend/stock/repository/StockQueryRepository.java) | 코드 기준 종목 조회 |
| [backend/src/main/resources/application.properties](../../../../backend/src/main/resources/application.properties) | 활성 프로필과 JPA 스키마 검증 설정 |
| [backend/build.gradle](../../../../backend/build.gradle) | Validation 의존성 |
| [docs/04_database/Trady.sql](../../../04_database/Trady.sql) | 주문 테이블과 엔티티 매핑 비교 |

실행 검증 시에는 `local` 프로필, 테스트 회원에 연결된 계좌, 요청 코드에 해당하는 종목 및 PostgreSQL 테스트 DB가 필요하다. 개발용 헤더는 인증된 회원임을 증명하지 않으며 로컬 개발에 한정된다. 실제 저장·롤백 검증은 기존 데이터에 영향을 주지 않는 테스트 DB에서 수행해야 한다.

## 4. 검증 결과

### 1. 현재 회원 ID 주입과 타입 통일

| 구분 | 내용 |
|---|---|
| 검증 방법 | `CurrentMemberId`, resolver의 `supportsParameter()`·`resolveArgument()`, 컨트롤러·서비스·계좌 repository의 인자 타입을 대조했다. |
| 예상 결과 | 헤더에서 얻은 회원 ID가 `Long`으로 변환되어 계좌 조회까지 전달된다. |
| 실제 결과 | 코드에서 resolver가 `@CurrentMemberId Long`을 지원하고 헤더 문자열을 `Long.valueOf()`로 변환한다. 컨트롤러, 주문 서비스, 계좌 조회 서비스와 repository 모두 `Long memberId`를 받는다. |


### 2. 로컬 환경 등록과 요청 경로

| 구분 | 내용 |
|---|---|
| 검증 방법 | `LocalAuthWebMvcConfig`, `application.properties`, `OrderController`의 매핑 선언을 확인했다. |
| 예상 결과 | 로컬 환경에서 resolver가 등록되고 `/v1/order/buy`, `/v1/order/sell` 요청을 처리한다. |
| 실제 결과 | 설정에 `spring.profiles.active=local`이 있고 MVC 설정은 `@Profile("local")`에서 resolver를 등록한다. 컨트롤러는 `@RequestMapping("/v1/order")`와 `/buy`·`/sell`의 `@PostMapping`을 사용한다. |

기존 `@RestController("/v1/order")`의 경로 지정 오류는 `@RequestMapping` 분리로 해소되었다. 작업 계획도 현재 매핑 구조로 정리했다. 실제 MVC 라우팅과 인자 주입은 요청 테스트로 확인하지 않았다.

### 3. 주문 입력과 검증 선언

| 구분 | 내용 |
|---|---|
| 검증 방법 | `OrderRequest`, 컨트롤러 인자의 `@Validated`, `backend/build.gradle`의 의존성을 확인했다. |
| 예상 결과 | 종목 코드, 주문 유형, 가격과 수량에 선언한 제약을 요청 진입점에서 검증한다. |
| 실제 결과 | 종목 코드에 `@NotBlank`, `Integer type`에 `@NotNull`·`@Min(0)`·`@Max(1)`, 가격과 수량에 `@NotNull`·`@Positive`가 선언되어 있다. 매수·매도 요청 본문에 `@Validated`가 있고 Validation starter 의존성도 추가되어 있다. |

기존 필수값·범위 검증 누락은 DTO 제약과 컨트롤러 검증 선언으로 보완되었다. 현재 가격 검증은 주문 유형과 관계없이 필수 양수를 요구하며 작업 계획에도 이 입력 조건을 반영했다. 시장가 가격 생략 여부는 별도 정책 확인 대상이다. 여기서는 검증 선언만 기록하며 실패 응답과 예외 처리 구현은 다루지 않는다.

### 4. 회원 계좌와 종목을 이용한 주문 생성

| 구분 | 내용 |
|---|---|
| 검증 방법 | `OrderService.buy()`·`sell()`과 계좌·종목 조회 메서드의 호출 관계를 확인했다. |
| 예상 결과 | 회원 ID로 계좌를 찾고 종목 코드로 종목을 찾은 뒤, 요청 방향에 맞는 주문을 생성한다. |
| 실제 결과 | 두 메서드 모두 `accountQueryService.findByMemberId(memberId)`와 `stockQueryService.findByStockCode(request.stockCode())`를 호출하고 조회한 객체를 주문에 연결한다. 매수는 `Side.BUY`, 매도는 `Side.SELL`을 지정한다. 선행 회원 조회나 회원·계좌 생성은 없다. |

요청 본문에 회원·계좌 ID를 추가하지 않고 회원 식별과 주문 입력을 분리한 구현이다. 주문 유형 변환은 `OrderType.values()[request.type()]`이며 현재 선언 순서에 따라 0은 LIMIT, 1은 MARKET이다. 이 변환은 HTTP 입력 검증에 의존하므로 서비스를 직접 호출하는 경우 같은 범위 검증이 자동 수행된다고 볼 수 없다.

### 5. 초기 상태와 저장 트랜잭션

| 구분 | 내용 |
|---|---|
| 검증 방법 | `Order`의 빌더 생성자, `OrderRepository`, `OrderService`의 트랜잭션과 저장 호출을 확인했다. |
| 예상 결과 | 신규 주문은 `ACCEPTED`로 생성되고 쓰기 트랜잭션에서 저장된다. |
| 실제 결과 | 생성자는 `status`를 `OrderStatus.ACCEPTED`로 지정하고 `createdAt`과 `updatedAt`을 같은 시각으로 초기화한다. 매수·매도 메서드는 각각 `@Transactional`을 선언하고 `orderRepository.save()`를 호출한다. |

서비스 클래스의 읽기 전용 기본 설정과 별도로 저장 메서드에 쓰기 트랜잭션이 선언되어 있다. 현재 저장 경로에는 호가장 등록, 매칭 호출, 체결·결제 처리가 없다. 계좌 잔고·보유 수량 검증이나 예약도 구현되어 있지 않으므로 주문 가능 여부 보장과 단순 주문 저장을 구분해야 한다.

### 6. 주문 엔티티와 SQL 정의 대조

| 구분 | 내용 |
|---|---|
| 검증 방법 | `Order.java`의 테이블·컬럼 매핑과 `Trady.sql`의 주문 테이블 정의를 비교했다. |
| 예상 결과 | 테이블명, 주문 ID, 방향·유형 문자열 형식이 일치한다. |
| 실제 결과 | 엔티티와 SQL 모두 테이블명이 `orders`이고 ID 컬럼은 `order_id`다. SQL의 `side`는 `VARCHAR(4)`, `type`은 `VARCHAR(6)`이며 엔티티도 문자열 enum과 같은 길이를 지정한다. |

테이블명과 enum 저장 방식의 불일치는 현재 파일 대조에서 해소되었다. 작업 계획도 `orders`·`order_id`·문자열 enum 컬럼을 기준으로 정리했다. 다만 SQL 파일 수정이 실제 DB 변경을 의미하지는 않는다. `spring.jpa.hibernate.ddl-auto=validate` 설정은 있으나 애플리케이션을 실행해 실제 스키마 검증을 확인하지 않았다.

### 7. 실제 요청·DB 저장과 롤백

| 구분 | 내용 |
|---|---|
| 검증 방법 | `backend/src/test`의 테스트 파일 목록에서 주문·로컬 회원 ID 주입 전용 테스트 유무를 확인하고, 이번 기록 작성에서 수행한 실행 범위를 확인했다. |
| 예상 결과 | 정상 매수·매도 요청으로 계좌·종목·초기 상태가 맞는 주문이 저장되고 실패 시 불완전한 주문이 남지 않는다. |
| 실제 결과 | 실험 미수행. 해당 기능의 전용 테스트 파일은 발견하지 못했으며 이번 작업에서는 빌드, HTTP 요청, 실제 DB 저장·롤백을 실행하지 않았다. |

사용자는 Task 1의 문제사항 수정을 반영했다고 알렸으며, 이번 재확인에서도 회원 ID 타입, URL 매핑, 입력 검증 선언, 주문 SQL 매핑의 수정을 코드로 확인했다. 실행 결과에 대한 별도 근거는 전달받지 않았으므로 구현 반영과 실행 검증을 구분한다. 테스트 DB를 사용한 정상 매수·매도 저장, 회원 계좌·종목 연결, 초기 상태 및 실패 시 저장 여부는 실행 검증 대상으로 유지한다.
