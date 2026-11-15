"""함께타요 정산 로직.

여정을 탑승·하차 시점으로 구간을 나누고, 구간마다 쓴 에너지 비용을
그 구간에 탄 인원(운전자 포함)으로 나눈다. 한 사람의 금액은 그가 탄
구간들의 몫을 더한 값이며, 최종 금액은 100원 단위로 반올림한다.
"""
from __future__ import annotations

from dataclasses import dataclass, field

SEAT_NAMES = {"FL": "운전석", "FR": "조수석", "RL": "뒷좌석 왼쪽", "RR": "뒷좌석 오른쪽"}
ELEC_PRICE = 350  # 원/kWh (충전 단가 가정)


def round100(x: float) -> int:
    return int((x + 50) // 100 * 100)


@dataclass
class Passenger:
    pid: str
    name: str
    seat: str
    active: bool = True
    is_driver: bool = False
    distance: float = 0.0
    energy: float = 0.0     # 이 사람에게 배분된 에너지 (L 또는 kWh)
    cost: float = 0.0       # 배분된 에너지 비용 (원)
    segments: list = field(default_factory=list)


@dataclass
class Trip:
    code: str
    driver: str = "철수"
    fuel_price: int = 1700
    powertrain: str = "ice"   # ice | ev
    status: str = "lobby"     # lobby | driving | stopped | finished
    passengers: dict = field(default_factory=dict)
    vehicle: dict = field(default_factory=lambda: {
        "gear": "P", "odometer_km": 0.0, "fuel_l": 40.0, "battery_kwh": 48.0,
        "battery_capacity_kwh": 77.4, "doors": {}})
    segments: list = field(default_factory=list)
    seg_start: dict | None = None
    pending_alight: dict = field(default_factory=dict)   # pid -> seat
    extras: list = field(default_factory=list)
    events: list = field(default_factory=list)
    _seq: int = 0

    def __post_init__(self):
        self.passengers["driver"] = Passenger("driver", self.driver, "FL", is_driver=True)

    # ---------- 에너지 ----------
    def _energy(self) -> float:
        v = self.vehicle
        return v["fuel_l"] if self.powertrain == "ice" else v["battery_kwh"]

    def _unit_price(self) -> int:
        return self.fuel_price if self.powertrain == "ice" else ELEC_PRICE

    def _active(self):
        return [p for p in self.passengers.values() if p.active]

    def _log(self, text):
        self.events.append(text)
        self.events = self.events[-8:]

    # ---------- 구간 ----------
    def _open_segment(self):
        self.seg_start = {"odo": self.vehicle["odometer_km"], "energy": self._energy(),
                          "occupants": [p.pid for p in self._active()]}

    def _close_segment(self):
        if not self.seg_start:
            return
        s = self.seg_start
        dist = max(0.0, self.vehicle["odometer_km"] - s["odo"])
        used = max(0.0, s["energy"] - self._energy())
        cost = used * self._unit_price()
        n = len(s["occupants"]) or 1
        seg = {"no": len(self.segments) + 1, "distance": round(dist, 1), "energy": round(used, 2),
               "cost": round(cost), "occupants": s["occupants"]}
        self.segments.append(seg)
        for pid in s["occupants"]:
            p = self.passengers[pid]
            p.distance += dist
            p.energy += used / n
            p.cost += cost / n
            p.segments.append(seg["no"])
        self.seg_start = None

    # ---------- 동작 ----------
    def join(self, name: str, seat: str) -> str:
        if seat not in SEAT_NAMES or seat == "FL":
            raise ValueError("선택할 수 없는 좌석이에요")
        if any(p.active and p.seat == seat for p in self.passengers.values()):
            raise ValueError("이미 누가 앉은 좌석이에요")
        self._seq += 1
        pid = f"p{self._seq}"
        if self.status in ("driving", "stopped"):
            self._close_segment()           # 중간 합류: 구간을 나눈다
        self.passengers[pid] = Passenger(pid, name, seat)
        if self.status in ("driving", "stopped"):
            self._open_segment()
        self._log(f"{name} 님 탑승 ({SEAT_NAMES[seat]})")
        return pid

    def start(self):
        if self.status == "lobby":
            self.status = "stopped" if self.vehicle["gear"] == "P" else "driving"
            self._open_segment()
            self._log("여정 시작")

    def on_vehicle(self, data: dict):
        """차량 앱이 Vehicle State(기어·주행거리·연료/배터리·도어)를 보낸다."""
        v = self.vehicle
        prev_doors = dict(v["doors"])
        for k in ("gear", "odometer_km", "fuel_l", "battery_kwh"):
            if k in data:
                v[k] = data[k]
        if "doors" in data:
            v["doors"] = dict(data["doors"])
        if self.status in ("driving", "stopped"):
            self.status = "stopped" if v["gear"] == "P" else "driving"
        # 정차 중 문이 '열림 → 닫힘'이 되면 그 좌석의 동승자에게 하차 확인
        if v["gear"] == "P" and self.status == "stopped":
            for seat, was_open in prev_doors.items():
                if was_open and not v["doors"].get(seat):
                    for p in self._active():
                        if p.seat == seat and not p.is_driver:
                            self.pending_alight[p.pid] = seat
                            self._log(f"{SEAT_NAMES[seat]} 문 열림 → {p.name} 님 하차 확인 중")

    def confirm_alight(self, pid: str, yes: bool):
        if pid not in self.pending_alight:
            return
        self.pending_alight.pop(pid)
        p = self.passengers[pid]
        if yes:
            self._close_segment()
            p.active = False
            self._open_segment()
            self._log(f"{p.name} 님 하차 확인 · {p.distance:.0f}km")
        else:
            self._log(f"{p.name} 님 계속 탑승")

    def add_extra(self, amount: int, label: str):
        if amount > 0:
            self.extras.append({"label": label, "amount": amount})
            self._log(f"{label} {amount:,}원 추가")

    def finish(self):
        if self.status != "finished":
            self._close_segment()
            self.status = "finished"
            self.pending_alight.clear()
            self._log("도착 · 정산 완료")

    # ---------- 화면용 데이터 ----------
    def _live_shares(self):
        """진행 중 구간까지 포함한 사람별 예상 금액."""
        shares = {pid: p.cost for pid, p in self.passengers.items()}
        dists = {pid: p.distance for pid, p in self.passengers.items()}
        energies = {pid: p.energy for pid, p in self.passengers.items()}
        if self.seg_start:
            s = self.seg_start
            used = max(0.0, s["energy"] - self._energy())
            dist = max(0.0, self.vehicle["odometer_km"] - s["odo"])
            n = len(s["occupants"]) or 1
            for pid in s["occupants"]:
                shares[pid] += used * self._unit_price() / n
                dists[pid] += dist
                energies[pid] += used / n
        extra_total = sum(e["amount"] for e in self.extras)
        if extra_total:
            n_all = len(self.passengers)
            for pid in shares:
                shares[pid] += extra_total / n_all
        return shares, dists, energies

    def snapshot(self) -> dict:
        shares, dists, energies = self._live_shares()
        people = []
        for pid, p in self.passengers.items():
            people.append({
                "pid": pid, "name": p.name, "seat": p.seat, "seat_name": SEAT_NAMES[p.seat],
                "active": p.active, "is_driver": p.is_driver,
                "distance_km": round(dists[pid], 1), "energy": round(energies[pid], 2),
                "raw": round(shares[pid]), "amount": round100(shares[pid]),
                "pending_alight": pid in self.pending_alight, "segments": p.segments,
            })
        total = sum(s["cost"] for s in self.segments) + sum(e["amount"] for e in self.extras)
        receive = sum(x["amount"] for x in people if not x["is_driver"])
        active = [x for x in people if x["active"]]
        per_head_now = round100(sum(x["raw"] for x in active) / len(active)) if active else 0
        return {
            "code": self.code, "status": self.status, "powertrain": self.powertrain,
            "fuel_price": self.fuel_price, "elec_price": ELEC_PRICE,
            "unit": "L" if self.powertrain == "ice" else "kWh",
            "vehicle": self.vehicle, "people": people, "segments": self.segments,
            "extras": self.extras, "total": round(total), "driver_receives": receive,
            "active_count": len(active), "per_head_now": per_head_now,
            "events": self.events,
        }
