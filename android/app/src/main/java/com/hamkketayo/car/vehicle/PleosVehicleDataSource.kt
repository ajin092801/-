package com.hamkketayo.car.vehicle

/*
 * Pleos Connect Vehicle State SDK 연결 자리.
 *
 * Pleos Playground 문서(Vehicle SDK)를 확인한 뒤 아래를 구현한다.
 *  1) SDK 초기화 및 capability 확인 (checkXXXCapabilities)
 *  2) 기어, 주행거리, 연료 잔량 / 배터리 잔량, 문별 개폐 상태 구독
 *  3) 받은 값을 VehicleSnapshot으로 바꿔 state에 내보내기
 *
 * 문별 개폐를 받을 수 없는 차량은 '정차 + 문 열림' 이벤트만으로 하차 후보를 띄우고,
 * 동승자 폰에서 확인받는다 (서버 로직은 동일).
 *
 * SDK 클래스 이름은 공개 문서 기준으로 채운다. 확인 전까지는
 * CarPropertyVehicleDataSource로 에뮬레이터에서 동작을 검증한다.
 */
