package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AiEngineGatewayService {

    @Value("${ai.engine.base-url:http://127.0.0.1:8100}")
    private String aiEngineBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ResponseEntity<?> get(String path, String authToken, Map<String, Object> queryParams) {
        return exchangeJson(HttpMethod.GET, path, null, authToken, queryParams);
    }

    public ResponseEntity<?> post(String path, Map<String, Object> payload, String authToken) {
        return exchangeJson(HttpMethod.POST, path, payload, authToken, null);
    }

    public ResponseEntity<?> post(String path, String authToken) {
        return exchangeJson(HttpMethod.POST, path, null, authToken, null);
    }

    public ResponseEntity<?> put(String path, Map<String, Object> payload, String authToken) {
        return exchangeJson(HttpMethod.PUT, path, payload, authToken, null);
    }

    public ResponseEntity<?> patch(String path, Map<String, Object> payload, String authToken) {
        return exchangeJson(HttpMethod.PATCH, path, payload, authToken, null);
    }

    public ResponseEntity<?> postForBinary(String path, Map<String, Object> payload, String authToken) {
        return exchangeRaw(HttpMethod.POST, path, payload, authToken, null);
    }

    public ResponseEntity<?> getRaw(String path, String authToken, Map<String, Object> queryParams) {
        return exchangeRaw(HttpMethod.GET, path, null, authToken, queryParams);
    }

    public ResponseEntity<?> postMultipart(
            String path,
            MultipartFile file,
            Map<String, String> formFields,
            String authToken
    ) {
        try {
            URI uri = buildUri(path, null);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            String normalizedAuth = normalizeAuthHeader(authToken);
            if (normalizedAuth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, normalizedAuth);
            }

            MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
            if (file != null && !file.isEmpty()) {
                String filename = file.getOriginalFilename();
                String safeFilename = (filename == null || filename.trim().isEmpty()) ? "upload.bin" : filename.trim();
                ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return safeFilename;
                    }
                };
                multipartBody.add("file", resource);
            }

            if (formFields != null) {
                for (Map.Entry<String, String> entry : formFields.entrySet()) {
                    String key = toSnakeCase(String.valueOf(entry.getKey()));
                    String value = entry.getValue();
                    if (value == null) {
                        continue;
                    }
                    String text = value.trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    multipartBody.add(key, text);
                }
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(multipartBody, headers);
            ResponseEntity<Object> response = restTemplate.exchange(uri, HttpMethod.POST, requestEntity, Object.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(parseBody(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("detail", "ai-engine gateway error: " + ex.getMessage()));
        }
    }

    public ResponseEntity<?> exchangeJson(
            HttpMethod method,
            String path,
            Object payload,
            String authToken,
            Map<String, Object> queryParams
    ) {
        try {
            URI uri = buildUri(path, queryParams);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            String normalizedAuth = normalizeAuthHeader(authToken);
            if (normalizedAuth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, normalizedAuth);
            }

            Object normalizedPayload = payload == null ? null : normalizePayload(payload);
            if (normalizedPayload != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }

            HttpEntity<?> requestEntity = new HttpEntity<>(normalizedPayload, headers);
            ResponseEntity<Object> response = restTemplate.exchange(uri, method, requestEntity, Object.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(parseBody(ex.getResponseBodyAsString()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("detail", "ai-engine gateway error: " + ex.getMessage()));
        }
    }

    public ResponseEntity<?> exchangeRaw(
            HttpMethod method,
            String path,
            Object payload,
            String authToken,
            Map<String, Object> queryParams
    ) {
        try {
            URI uri = buildUri(path, queryParams);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.ALL));
            String normalizedAuth = normalizeAuthHeader(authToken);
            if (normalizedAuth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, normalizedAuth);
            }

            Object normalizedPayload = payload == null ? null : normalizePayload(payload);
            if (normalizedPayload != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }

            HttpEntity<?> requestEntity = new HttpEntity<>(normalizedPayload, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(uri, method, requestEntity, byte[].class);

            HttpHeaders responseHeaders = new HttpHeaders();
            HttpHeaders upstreamHeaders = response.getHeaders();
            for (Map.Entry<String, List<String>> entry : upstreamHeaders.entrySet()) {
                String headerName = entry.getKey();
                if (headerName == null) {
                    continue;
                }
                if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)
                        || HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(headerName)) {
                    continue;
                }
                responseHeaders.put(headerName, new ArrayList<>(entry.getValue()));
            }

            return ResponseEntity.status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(response.getBody() == null ? new byte[0] : response.getBody());
        } catch (HttpStatusCodeException ex) {
            MediaType mediaType = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getContentType();
            String body = ex.getResponseBodyAsString();
            String safeBody = body == null ? "" : body;
            if (mediaType != null && MediaType.APPLICATION_JSON.includes(mediaType)) {
                return ResponseEntity.status(ex.getStatusCode()).body(parseBody(safeBody));
            }
            HttpHeaders headers = new HttpHeaders();
            if (mediaType != null) {
                headers.setContentType(mediaType);
            }
            return ResponseEntity.status(ex.getStatusCode()).headers(headers).body(safeBody.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("detail", "ai-engine gateway error: " + ex.getMessage()));
        }
    }

    public SseEmitter proxyTaskStream(String taskId, String authToken) {
        SseEmitter emitter = new SseEmitter(0L);
        String encodedTaskId = URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        String targetUrl = resolveBaseUrl() + "/ai/tasks/" + encodedTaskId + "/stream";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMinutes(30))
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET();

        String normalizedAuth = normalizeAuthHeader(authToken);
        if (normalizedAuth != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, normalizedAuth);
        }

        HttpRequest request = requestBuilder.build();
        CompletableFuture.runAsync(() -> streamSseFromAiEngine(request, emitter));
        return emitter;
    }

    private void streamSseFromAiEngine(HttpRequest request, SseEmitter emitter) {
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                emitter.send(SseEmitter.event().name("error").data(toJson(Map.of(
                        "detail", "ai-engine stream request failed",
                        "status", response.statusCode()
                ))));
                emitter.complete();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String eventName = "message";
                StringBuilder dataBuffer = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append("\n");
                        }
                        dataBuffer.append(line.substring(5).trim());
                        continue;
                    }
                    if (line.trim().isEmpty()) {
                        flushSseEvent(emitter, eventName, dataBuffer);
                        eventName = "message";
                        dataBuffer.setLength(0);
                    }
                }

                flushSseEvent(emitter, eventName, dataBuffer);
            }

            emitter.complete();
        } catch (Exception ex) {
            try {
                emitter.send(SseEmitter.event().name("error").data(toJson(Map.of("detail", ex.getMessage()))));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(ex);
        }
    }

    private void flushSseEvent(SseEmitter emitter, String eventName, StringBuilder dataBuffer) throws IOException {
        if (dataBuffer.length() == 0) {
            return;
        }
        emitter.send(SseEmitter.event().name(eventName).data(dataBuffer.toString()));
    }

    private URI buildUri(String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(resolveBaseUrl() + normalizePath(path));
        if (queryParams != null) {
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String key = toSnakeCase(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                String text = String.valueOf(value).trim();
                if (text.isEmpty()) {
                    continue;
                }
                builder.queryParam(key, text);
            }
        }
        return builder.build(true).toUri();
    }

    private Object normalizePayload(Object payload) {
        if (payload instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = toSnakeCase(String.valueOf(entry.getKey()));
                normalized.put(key, normalizePayload(entry.getValue()));
            }
            return normalized;
        }

        if (payload instanceof List<?> rawList) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : rawList) {
                normalized.add(normalizePayload(item));
            }
            return normalized;
        }

        return payload;
    }

    private String normalizeAuthHeader(String authToken) {
        if (authToken == null) {
            return null;
        }
        String token = authToken.trim();
        if (token.isEmpty()) {
            return null;
        }
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return "Bearer " + token.substring(7).trim();
        }
        return "Bearer " + token;
    }

    private Object parseBody(String body) {
        String content = body == null ? "" : body.trim();
        if (content.isEmpty()) {
            return Map.of("detail", "ai-engine error");
        }
        try {
            return objectMapper.readValue(content, Object.class);
        } catch (Exception ignore) {
            return Map.of("detail", content);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignore) {
            return String.valueOf(payload);
        }
    }

    private String toSnakeCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String resolveBaseUrl() {
        String base = aiEngineBaseUrl == null ? "" : aiEngineBaseUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
