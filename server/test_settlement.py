"""정산 로직 테스트: python3 -m unittest test_settlement"""
import unittest
from settlement import Trip

class T(unittest.TestCase):
    def test_mid_alight(self):
        t = Trip(code="1", fuel_price=1700)
        t.on_vehicle({"gear": "P", "odometer_km": 0, "fuel_l": 40})
        a = t.join("영희", "RL"); t.join("민수", "FR"); t.join("서준", "RR")
        t.start(); t.on_vehicle({"gear": "D"}); t.on_vehicle({"odometer_km": 90, "fuel_l": 34})   # 6L, 4명
        t.on_vehicle({"gear": "P"}); t.on_vehicle({"doors": {"RL": True}}); t.on_vehicle({"doors": {"RL": False}})
        self.assertIn(a, t.pending_alight)
        t.confirm_alight(a, True)
        t.on_vehicle({"gear": "D"}); t.on_vehicle({"odometer_km": 120, "fuel_l": 32}); t.on_vehicle({"gear": "P"})  # 2L, 3명
        t.finish()
        amt = {p["name"]: p["amount"] for p in t.snapshot()["people"]}
        self.assertEqual(amt, {"철수": 3700, "영희": 2600, "민수": 3700, "서준": 3700})

if __name__ == "__main__":
    unittest.main()
