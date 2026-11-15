package com.hamkketayo.car.vehicle

import kotlinx.coroutines.flow.StateFlow

/** 앱이 필요로 하는 차량 상태. 서버의 /vehicle API와 같은 형태. */
data class VehicleSnapshot(
    val gear: String = "P",                    // P, R, N, D
    val odometerKm: Double = 0.0,
    val fuelL: Double? = null,                 // 내연기관
    val batteryKwh: Double? = null,            // 전기차
    val doors: Map<String, Boolean> = emptyMap() // FL, FR, RL, RR → 열림 여부
)

/**
 * 차량 데이터 출처를 추상화한다.
 *  - PleosVehicleDataSource : Pleos Connect Vehicle State SDK (탑재 목표)
 *  - CarPropertyVehicleDataSource : AAOS 표준 CarPropertyManager (에뮬레이터 검증용)
 * 차종별 지원 여부는 Pleos 가이드대로 capability 확인 후 고른다.
 */
interface VehicleDataSource {
    val state: StateFlow<VehicleSnapshot>
    val isEv: Boolean
    fun start()
    fun stop()
}
