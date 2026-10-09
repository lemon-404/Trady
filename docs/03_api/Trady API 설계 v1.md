# Trady API 설계 v1

## 1. API URI 구조

Trady API의 URI는 다음 구조를 기본으로 한다.

```text
http://localhost:8080/{version}/{resource}/{sub-resource}
```

| 구성 요소 | 설명 |
|---|---|
| `http://localhost:8080` | 운영 도메인 |
| `{version}` | API 버전 |
| `{resource}` | API의 최상위 리소스 |
| `{sub-resource}` | 각 리소스에 포함되는 세부 리소스 |

Resource와 Sub-resource의 명칭은 **단수 명사**를 사용한다.

---

## 2. 운영 도메인

현재 개발 환경의 운영 도메인은 다음과 같다.

```text
http://localhost:8080
```

---

## 3. Resource

Trady API의 최상위 Resource는 다음과 같이 구성한다.

| Resource | 담당 영역 |
|---|---|
| `oauth2` | 회원가입, 로그인, 로그아웃 등 사용자 인증 및 인증 정보 관리 |
| `user` | 사용자 정보 및 사용자 계좌 관리 |
| `stock` | 주식 종목 정보 및 OHLCV 차트 데이터 관리 |
| `order` | 주식 매수·매도 주문 및 주문 상태 관리 |
| `execution` | 주문 체결 결과 및 체결 내역 관리 |
| `settlement` | 체결 이후 발생하는 결제 예정 정보 및 D+2 결제 관리 |

---

## 4. Sub-resource

### 4.1 OAuth2

회원가입과 로그인 등 사용자의 인증과 인증 정보 변경에 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `signup` | 회원가입 |
| `login` | 로그인 및 인증 정보 발급 |
| `logout` | 로그아웃 및 인증 정보 만료 |
| `reissue` | 인증 정보 재발급 |
| `profile` | 인증 사용자 개인정보 변경 |

### 4.2 User

사용자 정보와 사용자에게 소속된 계좌 정보를 조회하고 관리하는 데 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `profile` | 사용자 기본 정보 조회 및 관리 |
| `account` | 사용자의 모의 투자 계좌 및 계좌 현황 관리 |

### 4.3 Stock

주식 종목의 기본 정보와 OHLCV 차트 데이터를 조회하는 데 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `info` | 종목 코드, 종목명 등 주식 기본 정보 조회 |
| `candle` | 종목별 OHLCV 차트 데이터 조회 |

### 4.4 Order

주식의 매수·매도 주문을 요청하고 주문 상태와 주문 내역을 관리하는 데 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `buy` | 매수 주문 요청 |
| `sell` | 매도 주문 요청 |
| `detail` | 개별 주문 정보 및 주문 상태 조회 |
| `history` | 주문 내역 조회 |
| `cancel` | 미체결 주문 취소 |

### 4.5 Execution

주문 매칭으로 발생한 체결 결과와 체결 내역을 조회하는 데 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `detail` | 개별 체결 정보 조회 |
| `history` | 체결 내역 조회 |

### 4.6 Settlement

주문 체결 이후 발생하는 결제 예정 정보와 D+2 결제 처리 결과를 관리하는 데 사용한다.

| Sub-resource | 담당 영역 |
|---|---|
| `schedule` | 체결에 따라 발생한 결제 예정 정보 관리 |
| `history` | 완료된 결제 내역 조회 |
| `process` | 결제 예정일에 도달한 D+2 결제 처리 |
| `retry` | 실패한 결제 데이터 재처리 |

---

## 5. 공통 Request / Response 객체

API에서 공통으로 사용하는 Request 및 Response 객체를 정의한다.

---

## 6. API 목록

| 운영 도메인 | Version | Resource | Sub-resource | HTTP Method | Request | Response | URL |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |