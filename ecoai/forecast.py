"""EcoAI - monthly e-waste volume forecasting.

Input : monthly history [{"month": "2025-01", "kg": 420.5}, ...]
Output: forecast for the next N months with a simple prediction range.

The model is chosen from the amount of data available. Every candidate is scored with a rolling-origin
backtest: train on the past, predict the next `months_ahead` months (up to 6, same as the real forecast),
repeat for the last few origins; the lowest MAE wins. A model is only tried when it still has enough
training data at the earliest origin (12 months for the seasonal models, 24 for gradient boosting).

  < 4 months   -> rejected (not enough data to forecast anything honest)
  always       -> naive (last value), 3-month average, linear trend
  ~23+ months  -> + seasonal naive and ridge (trend + month-of-year)    [for a 6-month horizon]
  ~35+ months  -> + gradient boosting on lag features                  [for a 6-month horizon]
  (shorter horizons need a little less history, e.g. 20 / 32 months for a 3-month horizon)

Confidence is a label about the DATA VOLUME only (low < 12 months, medium < 24, high >= 24);
it is not a statistical guarantee.
"""
from __future__ import annotations

import math
import re
from typing import Callable

import numpy as np
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.linear_model import LinearRegression, Ridge

MIN_POINTS = 4
MAX_POINTS = 600
MAX_HORIZON = 24
_MONTH_RE = re.compile(r"^(\d{4})-(0[1-9]|1[0-2])$")


class ForecastError(ValueError):
    """Bad input; the message is safe to show to API clients."""


# ----------------------------------------------------------------------------- input handling
def _month_index(label: str) -> int:
    m = _MONTH_RE.match(label or "")
    if not m:
        raise ForecastError(f"Invalid month '{label}'. Use the format YYYY-MM.")
    return int(m.group(1)) * 12 + int(m.group(2)) - 1


def _month_label(index: int) -> str:
    return f"{index // 12:04d}-{index % 12 + 1:02d}"


def prepare_series(history) -> tuple[int, np.ndarray, int]:
    """Validate, sort and fill gaps. Returns (first_month_index, kg_array, number_of_filled_months)."""
    if not isinstance(history, list) or not history:
        raise ForecastError("history must be a non-empty list of {month, kg}.")
    if len(history) > MAX_POINTS:
        raise ForecastError(f"history can have at most {MAX_POINTS} entries.")
    seen: dict[int, float] = {}
    for row in history:
        if not isinstance(row, dict):
            raise ForecastError("Each history entry must be an object with month and kg.")
        idx = _month_index(row.get("month"))
        kg = row.get("kg")
        if isinstance(kg, bool) or not isinstance(kg, (int, float)) or not math.isfinite(kg):
            raise ForecastError(f"kg for {row.get('month')} must be a number.")
        if kg < 0:
            raise ForecastError(f"kg for {row.get('month')} cannot be negative.")
        if idx in seen:
            raise ForecastError(f"Month {row.get('month')} appears more than once.")
        seen[idx] = float(kg)
    first, last = min(seen), max(seen)
    values, filled = [], 0
    for idx in range(first, last + 1):
        if idx in seen:
            values.append(seen[idx])
        else:  # gap: linear interpolation between the nearest known months
            lo = max(i for i in seen if i < idx)
            hi = min(i for i in seen if i > idx)
            values.append(seen[lo] + (seen[hi] - seen[lo]) * (idx - lo) / (hi - lo))
            filled += 1
    if len(values) < MIN_POINTS:
        raise ForecastError(f"At least {MIN_POINTS} months of history are needed to forecast (got {len(values)}).")
    return first, np.asarray(values, dtype=float), filled


# ----------------------------------------------------------------------------- candidate models
# Each takes (y_train, first_index, horizon) and returns an array of `horizon` predictions.
def _naive(y, first, h):
    return np.repeat(y[-1], h)


def _moving_avg(y, first, h):
    return np.repeat(y[-3:].mean(), h)


def _linear(y, first, h):
    t = np.arange(len(y)).reshape(-1, 1)
    model = LinearRegression().fit(t, y)
    return model.predict(np.arange(len(y), len(y) + h).reshape(-1, 1))


def _seasonal_naive(y, first, h):
    return np.array([y[len(y) - 12 + (k % 12)] for k in range(h)])


def _season_feats(t: np.ndarray, first: int, scale: float) -> np.ndarray:
    moy = (first + t) % 12
    return np.column_stack([t / scale, np.sin(2 * np.pi * moy / 12), np.cos(2 * np.pi * moy / 12)])


def _ridge_seasonal(y, first, h):
    n = len(y)
    scale = max(n, 1)
    model = Ridge(alpha=1.0).fit(_season_feats(np.arange(n), first, scale), y)
    return model.predict(_season_feats(np.arange(n, n + h), first, scale))


def _gbr_lags(y, first, h):
    lags = (1, 2, 3, 12)

    def row(hist: list[float], idx: int) -> list[float]:
        moy = idx % 12
        return [hist[-l] for l in lags] + [math.sin(2 * math.pi * moy / 12), math.cos(2 * math.pi * moy / 12)]

    hist = list(y)
    X = [row(hist[:i], first + i) for i in range(12, len(hist))]
    model = GradientBoostingRegressor(n_estimators=150, max_depth=2, learning_rate=0.05, subsample=0.9, random_state=0)
    model.fit(np.asarray(X), y[12:])
    out = []
    for k in range(h):  # recursive multi-step prediction
        p = float(model.predict(np.asarray([row(hist, first + len(hist))]))[0])
        out.append(p)
        hist.append(max(p, 0.0))
    return np.asarray(out)


# name, function, minimum training points
CANDIDATES: list[tuple[str, Callable, int]] = [
    ("naive", _naive, 1),
    ("moving_average_3", _moving_avg, 3),
    ("linear_trend", _linear, 3),
    ("seasonal_naive", _seasonal_naive, 12),
    ("ridge_trend_seasonal", _ridge_seasonal, 12),
    ("gradient_boosting_lags", _gbr_lags, 24),
]


# ----------------------------------------------------------------------------- selection
BACKTEST_HORIZON = 6      # candidates are scored on up to this many months ahead, like the real forecast
MIN_SPREAD_FRACTION = 0.02  # prediction range is never narrower than 2% of the average level


def _backtest(fn, min_train, y, first, origins, steps):
    """Per-step errors (list of `steps` lists), or None when a model lacks training data at an origin."""
    errs = [[] for _ in range(steps)]
    for o in origins:
        if o < min_train:
            return None
        pred = np.maximum(np.asarray(fn(y[:o], first, steps), dtype=float), 0.0)
        for s in range(steps):
            errs[s].append(float(pred[s] - y[o + s]))
    return errs


def forecast(history, horizon: int = 6, pickup_capacity_kg: float | None = None) -> dict:
    if isinstance(horizon, bool) or not isinstance(horizon, int) or not 1 <= horizon <= MAX_HORIZON:
        raise ForecastError(f"horizon must be a whole number from 1 to {MAX_HORIZON}.")
    if pickup_capacity_kg is not None and (isinstance(pickup_capacity_kg, bool) or not isinstance(pickup_capacity_kg, (int, float)) or pickup_capacity_kg <= 0):
        raise ForecastError("pickup_capacity_kg must be a positive number.")

    first, y, filled = prepare_series(history)
    n = len(y)
    steps = max(1, min(horizon, BACKTEST_HORIZON, n - 3))   # months ahead used for scoring
    k = max(1, min(6, n // 3))                              # number of backtest origins
    origins = list(range(max(3, n - steps - k + 1), n - steps + 1))

    scored = []
    for name, fn, min_train in CANDIDATES:
        errs = _backtest(fn, min_train, y, first, origins, steps)
        if errs is None:
            continue
        flat = np.asarray([e for step in errs for e in step])
        scored.append({"model": name, "mae": float(np.abs(flat).mean()),
                       "rmse_steps": [float(math.sqrt(np.mean(np.square(step)))) for step in errs]})
    best = min(scored, key=lambda s: s["mae"])  # list order breaks ties in favour of simpler models
    fn = next(f for nm, f, _ in CANDIDATES if nm == best["model"])

    floor = MIN_SPREAD_FRACTION * float(y.mean())
    rmse_steps = np.maximum(np.maximum.accumulate(best["rmse_steps"]), floor)  # range never shrinks with the horizon
    pooled_rmse = float(np.sqrt(np.mean(np.square(rmse_steps))))

    raw = fn(y, first, horizon)
    rows = []
    for step, value in enumerate(raw, start=1):
        kg = max(float(value), 0.0)
        r = rmse_steps[step - 1] if step <= steps else rmse_steps[-1] * math.sqrt(step / steps)
        spread = 1.28 * float(r)  # rough 80% range from the backtest errors
        row = {"month": _month_label(first + n - 1 + step), "kg": round(kg, 1),
               "low": round(max(kg - spread, 0.0), 1), "high": round(kg + spread, 1)}
        if pickup_capacity_kg:
            row["pickups_needed"] = math.ceil(kg / pickup_capacity_kg) if kg > 0 else 0
            row["pickups_peak"] = math.ceil(row["high"] / pickup_capacity_kg) if row["high"] > 0 else 0
        rows.append(row)

    warnings = []
    if n < 12:
        warnings.append("Less than 12 months of history: seasonality cannot be learned, treat the forecast as a rough trend.")
    if filled:
        warnings.append(f"{filled} missing month(s) were filled by interpolation.")
    naive_mae = next(s["mae"] for s in scored if s["model"] == "naive")
    return {
        "model": best["model"],
        "data_points": n,
        "confidence": "low" if n < 12 else "medium" if n < 24 else "high",
        "backtest": {"mae": round(best["mae"], 2), "rmse": round(pooled_rmse, 2), "naive_mae": round(naive_mae, 2),
                     "origins": len(origins), "months_ahead": steps},
        "candidates": [{"model": s["model"], "mae": round(s["mae"], 2)} for s in sorted(scored, key=lambda s: s["mae"])],
        "filled_months": filled,
        "warnings": warnings,
        "forecast": rows,
    }


if __name__ == "__main__":  # python forecast.py  -> demo on SYNTHETIC data (not real collection data)
    import json
    rng = np.random.default_rng(1)
    demo = [{"month": _month_label(2023 * 12 + i), "kg": round(500 + 8 * i + 70 * math.sin(2 * math.pi * (i % 12) / 12) + rng.normal(0, 15), 1)} for i in range(30)]
    print(json.dumps(forecast(demo, 6, pickup_capacity_kg=250), indent=2))
