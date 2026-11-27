import math
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import numpy as np

from forecast import ForecastError, forecast, prepare_series, _month_label
from service import app


def series(n, start=2023 * 12, noise=15.0, seed=1, trend=8.0, season=70.0, base=500.0):
    rng = np.random.default_rng(seed)
    return [{"month": _month_label(start + i), "kg": round(max(base + trend * i + season * math.sin(2 * math.pi * (i % 12) / 12) + rng.normal(0, noise), 0), 1)} for i in range(n)]


class ForecastTests(unittest.TestCase):
    def test_long_series_picks_a_seasonal_model_and_beats_naive_on_holdout(self):
        full = series(42)
        train, test = full[:36], full[36:]
        res = forecast(train, 6)
        self.assertIn(res["model"], {"seasonal_naive", "ridge_trend_seasonal", "gradient_boosting_lags"})
        self.assertEqual(res["confidence"], "high")
        mae = np.mean([abs(f["kg"] - t["kg"]) for f, t in zip(res["forecast"], test)])
        naive_mae = np.mean([abs(train[-1]["kg"] - t["kg"]) for t in test])
        self.assertLess(mae, naive_mae)
        self.assertEqual(res["forecast"][0]["month"], "2026-01")
        self.assertEqual(len(res["forecast"]), 6)

    def test_medium_series_does_not_use_gradient_boosting(self):
        names = {c["model"] for c in forecast(series(20), 3)["candidates"]}
        self.assertNotIn("gradient_boosting_lags", names)
        self.assertIn("ridge_trend_seasonal", names)
        self.assertIn("seasonal_naive", names)

    def test_seasonal_models_need_enough_history(self):
        # horizon 3 -> thresholds 20 (seasonal) and 32 (gradient boosting)
        for n, expect in ((18, False), (19, False), (20, True)):
            names = {c["model"] for c in forecast(series(n), 3)["candidates"]}
            self.assertEqual("ridge_trend_seasonal" in names, expect, n)
            self.assertEqual("seasonal_naive" in names, expect, n)
        self.assertIn("gradient_boosting_lags", {c["model"] for c in forecast(series(32), 3)["candidates"]})
        self.assertNotIn("gradient_boosting_lags", {c["model"] for c in forecast(series(31), 3)["candidates"]})

    def test_short_series_works_with_low_confidence_and_simple_models_only(self):
        res = forecast(series(5), 3)
        self.assertEqual(res["confidence"], "low")
        self.assertTrue(res["warnings"])
        self.assertTrue({c["model"] for c in res["candidates"]} <= {"naive", "moving_average_3", "linear_trend"})

    def test_rejects_too_little_data(self):
        with self.assertRaises(ForecastError):
            forecast(series(3), 3)

    def test_gap_is_filled_and_reported(self):
        h = series(10)
        del h[4]
        _, y, filled = prepare_series(h)
        self.assertEqual(len(y), 10)
        self.assertEqual(filled, 1)
        self.assertEqual(forecast(h, 2)["filled_months"], 1)

    def test_input_validation(self):
        good = series(8)
        bad_inputs = [
            ([{"month": "2025-13", "kg": 1}] * 1, 3), ([{"month": "25-01", "kg": 1}], 3),
            (good + [dict(good[0])], 3),  # duplicate month
            (good[:3] + [{"month": "2030-01", "kg": -5}], 3),
            (good[:3] + [{"month": "2030-01", "kg": "x"}], 3),
            (good[:3] + [{"month": "2030-01", "kg": float("nan")}], 3),
            (good[:3] + [{"month": "2030-01", "kg": True}], 3),
            ("nope", 3), ([], 3), (good, 0), (good, 25), (good, "3"),
        ]
        for hist, hor in bad_inputs:
            with self.subTest(hist=str(hist)[:40], hor=hor):
                with self.assertRaises(ForecastError):
                    forecast(hist, hor)

    def test_forecast_is_never_negative_and_range_contains_point(self):
        falling = [{"month": _month_label(2024 * 12 + i), "kg": max(100 - 12 * i, 0)} for i in range(9)]
        for row in forecast(falling, 12)["forecast"]:
            self.assertGreaterEqual(row["kg"], 0)
            self.assertGreaterEqual(row["low"], 0)
            self.assertLessEqual(row["low"], row["kg"])
            self.assertGreaterEqual(row["high"], row["kg"])

    def test_pickup_plan(self):
        res = forecast(series(24), 3, pickup_capacity_kg=250)
        for row in res["forecast"]:
            self.assertEqual(row["pickups_needed"], math.ceil(row["kg"] / 250))
            self.assertGreaterEqual(row["pickups_peak"], row["pickups_needed"])
        with self.assertRaises(ForecastError):
            forecast(series(24), 3, pickup_capacity_kg=0)

    def test_deterministic(self):
        self.assertEqual(forecast(series(36), 6), forecast(series(36), 6))


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.c = app.test_client()
        os.environ.pop("ECOAI_KEY", None)

    def test_health(self):
        self.assertEqual(self.c.get("/health").get_json(), {"status": "ok"})

    def test_forecast_ok(self):
        r = self.c.post("/forecast", json={"history": series(24), "horizon": 4})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(len(r.get_json()["forecast"]), 4)

    def test_bad_requests_return_400_with_message(self):
        for payload in ({"history": series(2)}, {"history": series(10), "horizon": 99}, {}, {"history": "x"}):
            r = self.c.post("/forecast", json=payload)
            self.assertEqual(r.status_code, 400, payload)
            self.assertIn("message", r.get_json())
        self.assertEqual(self.c.post("/forecast", data="not json", content_type="text/plain").status_code, 400)
        self.assertEqual(self.c.post("/forecast", json=[1, 2]).status_code, 400)

    def test_api_key_is_enforced_when_configured(self):
        os.environ["ECOAI_KEY"] = "s3cret"
        try:
            body = {"history": series(12), "horizon": 2}
            self.assertEqual(self.c.post("/forecast", json=body).status_code, 401)
            self.assertEqual(self.c.post("/forecast", json=body, headers={"X-EcoAI-Key": "wrong"}).status_code, 401)
            self.assertEqual(self.c.post("/forecast", json=body, headers={"X-EcoAI-Key": "s3cret"}).status_code, 200)
            self.assertEqual(self.c.get("/health").status_code, 200)
        finally:
            os.environ.pop("ECOAI_KEY", None)

    def test_large_body_rejected(self):
        r = self.c.post("/forecast", data="x" * (300 * 1024), content_type="application/json")
        self.assertEqual(r.status_code, 413)


if __name__ == "__main__":
    unittest.main()
