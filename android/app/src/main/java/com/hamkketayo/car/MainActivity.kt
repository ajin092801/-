package com.hamkketayo.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.hamkketayo.car.data.TripApi
import com.hamkketayo.car.vehicle.CarPropertyVehicleDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Bg = Color(0xFF111418); private val CardBg = Color(0xFF1D2229); private val Blue = Color(0xFF3D7BFF)

class MainActivity : ComponentActivity() {
    private val api = TripApi(BuildConfig.SERVER_URL)
    private lateinit var vehicle: CarPropertyVehicleDataSource
    private var uxManager: CarUxRestrictionsManager? = null
    private var trip by mutableStateOf<JSONObject?>(null)
    private var touchBlocked by mutableStateOf(false)   // 주행 중 UI 제한

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vehicle = CarPropertyVehicleDataSource(this).also { it.start() }
        // 주행 중 UI 제한 상태 구독
        val car = Car.createCar(this)
        uxManager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        uxManager?.registerListener { r: CarUxRestrictions -> touchBlocked = r.isRequiresDistractionOptimization }

        lifecycleScope.launch {
            trip = api.createTrip(driver = "철수", ev = vehicle.isEv)
            launch { vehicle.state.collect { v -> trip?.let { api.pushVehicle(it.getString("code"), v) } } }
            while (true) { trip?.let { trip = api.get(it.getString("code")) }; delay(700) }
        }
        setContent { MaterialTheme { Screen() } }
    }

    @Composable private fun Screen() {
        val t = trip ?: return Box(Modifier.fillMaxSize().background(Bg))
        val code = t.getString("code")
        Column(Modifier.fillMaxSize().background(Bg).padding(48.dp)) {
            when (t.getString("status")) {
                "lobby" -> {
                    Text("함께 타고, 공정하게 나눠요", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("폰으로 QR을 찍고 탑승하세요 · 여정 코드 $code", color = Color.Gray, fontSize = 22.sp)
                    // TODO: 여정 URL QR 이미지 (서버 /p/<code>)
                    Spacer(Modifier.weight(1f))
                    Button({ lifecycleScope.launch { trip = api.start(code) } }, Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(Blue)) { Text("여정 시작", fontSize = 26.sp) }
                }
                "driving" -> Box(Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp)).padding(20.dp),
                    contentAlignment = Alignment.Center) {
                    // 주행 중에는 한 줄만, 터치 요소 없음
                    Text("탑승 ${t.getInt("active_count")}명 · 1인당 예상 ${"%,d".format(t.getInt("per_head_now"))}원",
                        color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                "stopped" -> {
                    val people = t.getJSONArray("people")
                    val pending = (0 until people.length()).map { people.getJSONObject(it) }.firstOrNull { it.getBoolean("pending_alight") }
                    Text(if (pending != null) "하차를 감지했어요" else "잠시 멈췄어요", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    pending?.let { Text("${it.getString("seat_name")} 문 열림 · ${it.getString("name")} 님 하차 확인 중", color = Color(0xFFFF8A3D), fontSize = 24.sp) }
                    Spacer(Modifier.weight(1f))
                    if (!touchBlocked) Button({ lifecycleScope.launch { trip = api.finish(code) } }, Modifier.fillMaxWidth().height(72.dp),
                        colors = ButtonDefaults.buttonColors(Blue)) { Text("도착 · 정산하기", fontSize = 26.sp) }
                }
                "finished" -> {
                    Text("탄 만큼 나누면, 정산 끝", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    val people = t.getJSONArray("people")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in 0 until people.length()) {
                            val p = people.getJSONObject(i)
                            Column(Modifier.weight(1f).background(CardBg, RoundedCornerShape(16.dp)).padding(20.dp)) {
                                Text(p.getString("name"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text("탄 거리 ${p.getDouble("distance_km")}km", color = Color.Gray, fontSize = 18.sp)
                                Text("${"%,d".format(p.getInt("amount"))}원", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() { vehicle.stop(); super.onDestroy() }
}
