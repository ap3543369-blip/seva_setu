"""Tiny HTTP wrapper around forecast.py so the Spring Boot backend can call it.

  python service.py                 # listens on 127.0.0.1:5001
  ECOAI_PORT=5001 ECOAI_KEY=secret python service.py

POST /forecast  {"history":[{"month":"2025-01","kg":420}, ...], "horizon":6, "pickup_capacity_kg":250}
GET  /health
If ECOAI_KEY is set, requests to /forecast must send the same value in the X-EcoAI-Key header.
"""
import hmac
import os

from flask import Flask, jsonify, request

from forecast import ForecastError, forecast

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 256 * 1024  # history is tiny; reject big bodies


@app.get("/health")
def health():
    return jsonify(status="ok")


@app.post("/forecast")
def run_forecast():
    key = os.environ.get("ECOAI_KEY")
    if key and not hmac.compare_digest(request.headers.get("X-EcoAI-Key", ""), key):
        return jsonify(message="Invalid or missing API key."), 401
    body = request.get_json(silent=True)
    if not isinstance(body, dict):
        return jsonify(message="Send a JSON object."), 400
    try:
        return jsonify(forecast(body.get("history"), body.get("horizon", 6), body.get("pickup_capacity_kg")))
    except ForecastError as e:
        return jsonify(message=str(e)), 400


@app.errorhandler(413)
def too_big(_):
    return jsonify(message="Request body too large."), 413


if __name__ == "__main__":
    app.run(host=os.environ.get("ECOAI_HOST", "127.0.0.1"), port=int(os.environ.get("ECOAI_PORT", "5001")))
