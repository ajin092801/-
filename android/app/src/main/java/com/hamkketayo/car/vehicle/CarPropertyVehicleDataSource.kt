package com.hamkketayo.car.vehicle

import android.car.Car
import android.car.VehicleAreaDoor
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** AAOS 표준 Car API로 기어·주행거리·연료/배터리·도어를 구독한다. */
class CarPropertyVehicleDataSource(private val context: Context) : VehicleDataSource {
    private var car: Car? = null
    private var cpm: CarPropertyManager? = null
    private val _state = MutableStateFlow(VehicleSnapshot())
    override val state: StateFlow<VehicleSnapshot> = _state
    override var isEv: Boolean = false
        private set

    private val doorAreas = mapOf(
        VehicleAreaDoor.DOOR_ROW_1_LEFT to "FL", VehicleAreaDoor.DOOR_ROW_1_RIGHT to "FR",
        VehicleAreaDoor.DOOR_ROW_2_LEFT to "RL", VehicleAreaDoor.DOOR_ROW_2_RIGHT to "RR")

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            when (v.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> _state.update { it.copy(gear = gearName(v.value as Int)) }
                VehiclePropertyIds.PERF_ODOMETER -> _state.update { it.copy(odometerKm = (v.value as Float).toDouble()) }
                VehiclePropertyIds.FUEL_LEVEL -> _state.update { it.copy(fuelL = (v.value as Float) / 1000.0) }        // mL → L
                VehiclePropertyIds.EV_BATTERY_LEVEL -> _state.update { it.copy(batteryKwh = (v.value as Float) / 1000.0) } // Wh → kWh
                VehiclePropertyIds.DOOR_POS -> {
                    val seat = doorAreas[v.areaId] ?: return
                    val open = (v.value as Int) > 0
                    _state.update { it.copy(doors = it.doors + (seat to open)) }
                }
            }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {}
    }

    override fun start() {
        car = Car.createCar(context)
        cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        val m = cpm ?: return
        isEv = m.getCarPropertyConfig(VehiclePropertyIds.EV_BATTERY_LEVEL) != null
        listOf(VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PERF_ODOMETER,
            if (isEv) VehiclePropertyIds.EV_BATTERY_LEVEL else VehiclePropertyIds.FUEL_LEVEL,
            VehiclePropertyIds.DOOR_POS).forEach { id ->
            if (m.getCarPropertyConfig(id) != null)   // 지원하는 속성만 구독 (capability 확인)
                m.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }
    }

    override fun stop() { cpm?.unregisterCallback(callback); car?.disconnect() }

    private fun gearName(g: Int) = when (g) {
        0x0004 -> "P"; 0x0002 -> "R"; 0x0001 -> "N"; else -> "D"   // VehicleGear 값
    }
}
