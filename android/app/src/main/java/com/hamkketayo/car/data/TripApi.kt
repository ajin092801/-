package com.hamkketayo.car.data

import com.hamkketayo.car.vehicle.VehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 함께타요 서버 API (server/server.py 와 짝) */
class TripApi(private val base: String) {
    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            JSONObject(c.inputStream.bufferedReader().readText())
        }

    suspend fun createTrip(driver: String, ev: Boolean) =
        call("POST", "/api/trips", JSONObject().put("driver", driver).put("powertrain", if (ev) "ev" else "ice"))
    suspend fun get(code: String) = call("GET", "/api/trips/$code")
    suspend fun start(code: String) = call("POST", "/api/trips/$code/start", JSONObject())
    suspend fun finish(code: String) = call("POST", "/api/trips/$code/finish", JSONObject())
    suspend fun addExtra(code: String, amount: Int) =
        call("POST", "/api/trips/$code/extra", JSONObject().put("amount", amount).put("label", "톨비"))

    /** 차량 상태가 바뀔 때마다 서버로 보낸다. 서버가 구간·하차 감지를 처리한다. */
    suspend fun pushVehicle(code: String, v: VehicleSnapshot) = call("POST", "/api/trips/$code/vehicle",
        JSONObject().put("gear", v.gear).put("odometer_km", v.odometerKm)
            .apply { v.fuelL?.let { put("fuel_l", it) }; v.batteryKwh?.let { put("battery_kwh", it) } }
            .put("doors", JSONObject(v.doors as Map<*, *>)))
}
