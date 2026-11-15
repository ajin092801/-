# 함께타요

중간에 내린 친구 몫까지 차가 알아서 계산해 주는 기름값 N빵 앱 (Pleos Connect 차량용 앱)

함께 탄 사람과 탄 구간을 차량 데이터(기어, 주행거리, 연료·배터리, 문 열림)로 기록하고, 구간마다 쓴 에너지 비용을 그 구간에 탄 인원으로 나눕니다. 전기차에서는 V2L 캠핑 플래너가 함께 열립니다.

## 폴더 구성

| 폴더 | 내용 |
| --- | --- |
| `server/` | 동작하는 프로토타입: 정산 서버(Python), 차량 화면, 동승자 폰 화면 |
| `android/` | Pleos Connect(Android Automotive)용 Kotlin 앱 소스 |
| `demo/` | 시연 영상과 녹화 스크립트 |

## 프로토타입 실행 방법

Python 3.10 이상만 있으면 되고, 추가 설치는 필요 없습니다.

```bash
cd server
python3 server.py
```

- 차량 화면: http://localhost:8000/car
  - 오른쪽 '가상 차량 콘솔'이 Pleos Virtual Console 역할을 합니다. 기어, 주행, 문 열림을 바꿔 볼 수 있어요.
- 동승자 폰 화면: http://localhost:8000/p/여정코드 (같은 와이파이의 폰에서는 PC의 IP 주소로 접속)
- 시연 화면(차 + 폰 2대): http://localhost:8000/demo

### 시연 순서
1. 차량 화면에 여정 코드가 뜹니다. 폰에서 `/p/여정코드`로 접속해 이름과 좌석을 고릅니다.
2. `여정 시작` → 콘솔에서 기어 `D` → `+16km 주행`
3. 기어 `P` → 콘솔에서 `뒷좌석 왼쪽` 문 열고 닫기 → 그 자리 동승자 폰에 하차 확인이 뜹니다.
4. `네, 내렸어요` → 다시 `D`로 주행 → `P` → `도착 · 정산하기`
5. 각자 폰에 금액과 계산 근거가 표시됩니다. 전기차 여정에서는 `캠핑 모드 (V2L)`가 열립니다.

### 정산 로직 테스트

```bash
cd server
python3 -m unittest test_settlement
```

기획서의 예시(휘발유 1,700원/L, 4명 출발 후 1명 중간 하차)를 그대로 검증합니다. 결과는 중간 하차자 2,600원, 나머지 3,700원입니다.

## 서버 API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/trips` | 여정 생성 (`driver`, `powertrain`: ice/ev) |
| GET | `/api/trips/{code}` | 여정 상태, 사람별 예상·확정 금액 |
| POST | `/api/trips/{code}/join` | 동승자 탑승 (`name`, `seat`: FR/RL/RR) |
| POST | `/api/trips/{code}/start` | 여정 시작 |
| POST | `/api/trips/{code}/vehicle` | 차량 상태 전송 (`gear`, `odometer_km`, `fuel_l`/`battery_kwh`, `doors`) |
| POST | `/api/trips/{code}/alight` | 하차 확인 응답 (`passenger_id`, `yes`) |
| POST | `/api/trips/{code}/extra` | 톨비·주차비 추가 (`amount`) |
| POST | `/api/trips/{code}/finish` | 도착, 정산 확정 |

정차(P) 중 문이 '열림 → 닫힘'이 되면 그 좌석 동승자에게 하차 확인을 보냅니다. 확인되면 구간을 마감하고 새 구간을 엽니다.

## Android 앱 (Pleos Connect)

`android/`는 Android Studio에서 여는 Kotlin + Jetpack Compose 프로젝트입니다.

- `vehicle/CarPropertyVehicleDataSource.kt`: AAOS 표준 `CarPropertyManager`로 기어, 주행거리, 연료/배터리, 도어를 구독합니다. 지원하는 속성만 구독합니다.
- `vehicle/PleosVehicleDataSource.kt`: Pleos Vehicle State SDK를 연결할 자리입니다. SDK 문서 확인 후 구현합니다.
- `data/TripApi.kt`: 위 서버 API 클라이언트입니다.
- `MainActivity.kt`: 탑승 준비, 주행 중(한 줄 표시, 터치 없음), 정차·하차 감지, 정산 화면이 있습니다. `CarUxRestrictionsManager`로 주행 중 UI 제한을 적용합니다.

> 현재 상태: 이 소스는 아직 빌드하거나 에뮬레이터에서 실행해 보지 않았습니다. Pleos Connect Emulator에서 빌드하고, 권한과 SDK 연동을 확인해야 합니다. 주행거리·도어 같은 일부 AAOS 속성은 시스템 권한이 필요할 수 있어서, 실제 탑재는 Pleos Vehicle SDK 경로를 우선 사용합니다.

에뮬레이터는 PC의 서버에 `http://10.0.2.2:8000`으로 접속합니다(`app/build.gradle.kts`의 `SERVER_URL`).

## 제출용 APK

공모전 규칙에 따라 release APK는 `apk/팀명_앱이름_release.apk` 경로에 둡니다. 빌드 후 추가할 예정입니다.

## 다음 단계

- [ ] Pleos Connect Emulator에서 Android 앱 빌드·실행, release APK 생성
- [ ] Pleos Vehicle State SDK, NaviHelper SDK 연동
- [ ] 서버를 FastAPI + WebSocket으로 전환하고 클라우드에 배포
- [ ] 차량 화면에 실제 여정 URL QR 생성
- [ ] V2L 사용 전력 정산 합산, 가족·부부 모드
