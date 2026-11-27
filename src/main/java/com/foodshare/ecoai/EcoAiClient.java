package com.foodshare.ecoai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodshare.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calls the Python forecasting service (ecoai/service.py). URL and optional key come from application.properties. */
@Component
public class EcoAiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String key;

    public EcoAiClient(ObjectMapper mapper,
                       @Value("${ecoai.url:http://127.0.0.1:5001}") String baseUrl,
                       @Value("${ecoai.key:}") String key) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.key = key;
    }

    public Map<String, Object> forecast(List<Map<String, Object>> history, int horizon, Double pickupCapacityKg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("history", history);
        body.put("horizon", horizon);
        if (pickupCapacityKg != null) body.put("pickup_capacity_kg", pickupCapacityKg);
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/forecast"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!key.isBlank()) req.header("X-EcoAI-Key", key);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = mapper.readValue(res.body(), new TypeReference<Map<String, Object>>() { });
            if (res.statusCode() == 200) return json;
            String msg = String.valueOf(json.getOrDefault("message", "The forecast service rejected the request."));
            if (res.statusCode() == 400) throw ApiException.badRequest(msg);
            throw new ApiException(HttpStatus.BAD_GATEWAY, msg);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The EcoAI forecast service is not reachable. Start it with: cd ecoai && python3 service.py");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The forecast request was interrupted.");
        }
    }
}
