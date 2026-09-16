package com.example.backend.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VideoAiService {

    @Value("${dashscope.api.key}")
    private String dashscopeApiKey;

    @Value("${did.api.key}")
    private String didApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DID_BASE_URL = "https://api.d-id.com";

    public String uploadImageToTempHost(String localImagePath) {
        String url = "https://tmpfiles.org/api/v1/upload";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(new File(localImagePath)));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, requestEntity, JsonNode.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String originalUrl = response.getBody().path("data").path("url").asText();
                String directUrl = originalUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/");
                return directUrl.startsWith("http://") ? "https://" + directUrl.substring(7) : directUrl;
            }
        } catch (Exception e) {
            System.err.println("upload image to temporary host failed: " + e.getMessage());
        }
        return null;
    }

    public String generateTalkingHead(String text, String sourceUrl, String voiceId) throws Exception {
        String authBase64 = Base64.getEncoder().encodeToString(didApiKey.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + authBase64);

        String resolvedVoiceId = (voiceId != null && !voiceId.trim().isEmpty()) ? voiceId : "zh-CN-XiaoxiaoNeural";

        Map<String, Object> payload = new HashMap<>();
        payload.put("script", Map.of(
                "type", "text",
                "subtitles", false,
                "provider", Map.of("type", "microsoft", "voice_id", resolvedVoiceId),
                "input", text
        ));
        payload.put("config", Map.of("fluent", false, "pad_audio", 0.0));
        payload.put("source_url", sourceUrl);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(DID_BASE_URL + "/talks", requestEntity, Map.class);
        if (createResp.getStatusCode() != HttpStatus.CREATED || createResp.getBody() == null) {
            throw new RuntimeException("D-ID create task failed: " + createResp.getBody());
        }

        String taskId = String.valueOf(createResp.getBody().get("id"));
        for (int i = 0; i < 20; i++) {
            Thread.sleep(2000);
            HttpEntity<Void> getEntity = new HttpEntity<>(headers);
            ResponseEntity<Map> getResp = restTemplate.exchange(
                    DID_BASE_URL + "/talks/" + taskId,
                    HttpMethod.GET,
                    getEntity,
                    Map.class
            );

            if (getResp.getStatusCode() == HttpStatus.OK && getResp.getBody() != null) {
                Map<String, Object> body = getResp.getBody();
                String status = String.valueOf(body.get("status"));
                if ("done".equals(status)) {
                    return String.valueOf(body.get("result_url"));
                }
                if ("error".equals(status)) {
                    throw new RuntimeException("D-ID task failed: " + body);
                }
            } else {
                throw new RuntimeException("D-ID polling failed");
            }
        }
        throw new RuntimeException("video generation timeout");
    }

    public List<String> extractBlackboardContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String systemPrompt = "你是一名教学内容整理助手，擅长提炼课堂黑板关键词。";
        String promptText = text.length() > 2500 ? text.substring(0, 2500) : text;
        String userPrompt = "请从下面教学文本中提取 3-6 个关键词或短语，"
                + "每条不超过 14 字，并只返回 JSON 数组字符串。文本：\n" + promptText;

        try {
            Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build();
            Message userMsg = Message.builder().role(Role.USER.getValue()).content(userPrompt).build();

            GenerationParam param = GenerationParam.builder()
                    .apiKey(dashscopeApiKey)
                    .model("qwen-plus")
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.TEXT)
                    .build();

            GenerationResult result = new Generation().call(param);
            String rawText = result.getOutput().getText();
            String cleaned = rawText.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start != -1 && end != -1 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
                List<String> rawList = objectMapper.readValue(cleaned, new TypeReference<List<String>>() {
                });
                List<String> output = new ArrayList<>();
                for (String item : rawList) {
                    String value = item == null ? "" : item.trim();
                    if (!value.isEmpty()) {
                        output.add(value.length() > 14 ? value.substring(0, 14) : value);
                    }
                }
                return output;
            }
        } catch (Exception e) {
            System.err.println("extract blackboard content failed: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public Map<String, Object> extractVideoContent(String text, String titleHint) throws Exception {
        String clippedText = text.length() > 12000 ? text.substring(0, 12000) : text;
        String title = (titleHint == null || titleHint.trim().isEmpty()) ? "未命名课程视频" : titleHint.trim();

        String systemPrompt = "你是一名教学视频分析助手。请将输入整理为结构化学习材料，并仅输出 JSON。";
        String userPrompt = "请分析下列教学视频文本（字幕或讲稿），输出 JSON，字段包括：\n"
                + "summary（150字以内概述）、key_points（数组，6-10条）、terms（数组，每项含term/definition）、"
                + "review_outline（markdown复习提纲）、quiz（数组，每项含question/options/answer/analysis）。\n"
                + "课程标题：" + title + "\n"
                + "视频文本：\n" + clippedText;

        Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build();
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(userPrompt).build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(dashscopeApiKey)
                .model("qwen-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.TEXT)
                .build();

        GenerationResult result = new Generation().call(param);
        String rawText = result.getOutput().getText();
        String cleaned = rawText.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception parseEx) {
            root = objectMapper.createObjectNode();
        }

        List<String> keyPoints = new ArrayList<>();
        JsonNode keyPointNode = root.path("key_points");
        if (keyPointNode.isArray()) {
            for (JsonNode node : keyPointNode) {
                String value = node.asText("").trim();
                if (!value.isEmpty()) {
                    keyPoints.add(value);
                }
            }
        }
        if (keyPoints.isEmpty()) {
            keyPoints = extractBlackboardContent(clippedText);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("title", title);
        response.put("summary", root.path("summary").asText(""));
        response.put("key_points", keyPoints);
        response.put("terms", root.path("terms").isArray() ? root.path("terms") : objectMapper.createArrayNode());
        response.put("review_outline", root.path("review_outline").asText(""));
        response.put("quiz", root.path("quiz").isArray() ? root.path("quiz") : objectMapper.createArrayNode());
        return response;
    }

    public String buildLessonImagePrompt(String text, List<String> boardItems) {
        String summary = String.join("、", boardItems);
        if (summary.isEmpty()) {
            summary = text == null ? "" : text.replace("\n", " ").trim();
            summary = summary.length() > 60 ? summary.substring(0, 60) : summary;
        }
        return "课堂教学插图，主题：" + summary + "。风格：清新明亮、教学场景、扁平插画、高细节。";
    }

    public String generateLessonImage(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return null;
        }
        try {
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashscopeApiKey)
                    .model("wanx-v1")
                    .prompt(prompt)
                    .size("1024*1024")
                    .n(1)
                    .build();
            ImageSynthesisResult result = new ImageSynthesis().call(param);
            if (result != null
                    && result.getOutput() != null
                    && result.getOutput().getResults() != null
                    && !result.getOutput().getResults().isEmpty()) {
                return result.getOutput().getResults().get(0).get("url");
            }
        } catch (Exception e) {
            System.err.println("generate lesson image failed: " + e.getMessage());
        }
        return null;
    }
}
