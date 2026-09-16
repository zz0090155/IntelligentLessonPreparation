package com.example.backend.controller;

import com.example.backend.config.AuthContextInterceptor;
import com.example.backend.service.AiEngineGatewayService;
import com.example.backend.service.OtherService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class OtherController {
    @Autowired
    private OtherService otherService;

    @Autowired
    private AiEngineGatewayService aiEngineGatewayService;

    @GetMapping("/health")
    public ResponseEntity<Map<String,Object>> health(){
        return otherService.health();
    }

    @PostMapping("/upload_doc")
    public ResponseEntity<?> uploadDoc(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "teacherId", required = false) String teacherId,
            @RequestParam(value = "teacher_id", required = false) String teacherIdSnake,
            @RequestParam(value = "kb_scope", required = false) String kbScope,
            @RequestParam(value = "conversation_key", required = false) String conversationKey,
            @RequestParam(value = "subject_tag", required = false) String subjectTag,
            @RequestParam(value = "grade_tag", required = false) String gradeTag,
            HttpServletRequest request
    ) {
        Map<String, String> formFields = new HashMap<>();
        putIfText(formFields, "teacher_id", firstNonBlank(teacherId, teacherIdSnake, teacherIdFromRequest(request)));
        putIfText(formFields, "kb_scope", kbScope);
        putIfText(formFields, "conversation_key", conversationKey);
        putIfText(formFields, "subject_tag", subjectTag);
        putIfText(formFields, "grade_tag", gradeTag);
        return aiEngineGatewayService.postMultipart("/ai/upload_doc", file, formFields, authToken(request));
    }

    @PostMapping("/clear_kb")
    public ResponseEntity<?> clearKb(HttpServletRequest request) {
        return aiEngineGatewayService.post("/ai/clear_kb", authToken(request));
    }

    @PostMapping("/upload_image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return aiEngineGatewayService.postMultipart("/ai/upload_image", file, null, authToken(request));
    }

    @PostMapping("/uoload_image")
    public ResponseEntity<?> uploadImageCompat(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return aiEngineGatewayService.postMultipart("/ai/upload_image", file, null, authToken(request));
    }

    @PostMapping("/generate_image")
    public ResponseEntity<?> generateImage(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        if (payload != null) {
            body.putAll(payload);
        }
        return aiEngineGatewayService.post("/ai/generate_image", body, authToken(request));
    }

    @PostMapping("/generate_ppt")
    public ResponseEntity<?> generatePpt(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        return aiEngineGatewayService.postForBinary(
                "/ai/generate_ppt",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chatJson(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.postForBinary(
                "/ai/chat",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> chatMultipart(
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "conversation_key", required = false) String conversationKey,
            @RequestParam(value = "teacherId", required = false) String teacherId,
            @RequestParam(value = "teacher_id", required = false) String teacherIdSnake,
            @RequestParam(value = "image_filename", required = false) String imageFilename,
            @RequestParam(value = "use_knowledge_base", required = false) String useKnowledgeBase,
            @RequestParam(value = "selected_kb_ids", required = false) List<String> selectedKbIds,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest request
    ) {
        Map<String, Object> payload = new HashMap<>();

        String mergedPrompt = firstNonBlank(prompt, message);
        if (mergedPrompt == null || mergedPrompt.trim().isEmpty()) {
            mergedPrompt = (image != null && !image.isEmpty()) ? "请结合上传图片给出课堂分析。" : null;
        }
        putIfTextObject(payload, "prompt", mergedPrompt);
        putIfTextObject(payload, "conversation_key", conversationKey);

        String explicitTeacherId = firstNonBlank(teacherId, teacherIdSnake);
        putIfTextObject(payload, "teacher_id", explicitTeacherId);

        if (useKnowledgeBase != null && !useKnowledgeBase.trim().isEmpty()) {
            payload.put("use_knowledge_base", toBoolean(useKnowledgeBase));
        }

        if (selectedKbIds != null && !selectedKbIds.isEmpty()) {
            List<String> cleanedKbIds = new ArrayList<>();
            for (String item : selectedKbIds) {
                String text = item == null ? "" : item.trim();
                if (!text.isEmpty()) {
                    cleanedKbIds.add(text);
                }
            }
            if (!cleanedKbIds.isEmpty()) {
                payload.put("selected_kb_ids", cleanedKbIds);
            }
        }

        String resolvedImageFilename = imageFilename == null ? "" : imageFilename.trim();
        if ((resolvedImageFilename == null || resolvedImageFilename.isEmpty()) && image != null && !image.isEmpty()) {
            ResponseEntity<?> uploadResult = aiEngineGatewayService.postMultipart(
                    "/ai/upload_image",
                    image,
                    null,
                    authToken(request)
            );
            if (!uploadResult.getStatusCode().is2xxSuccessful()) {
                return uploadResult;
            }
            Object body = uploadResult.getBody();
            if (body instanceof Map<?, ?> bodyMap) {
                Object filenameValue = bodyMap.get("filename");
                if (filenameValue == null) {
                    filenameValue = bodyMap.get("image_url");
                }
                if (filenameValue != null) {
                    resolvedImageFilename = String.valueOf(filenameValue).trim();
                }
            }
        }
        putIfTextObject(payload, "image_filename", resolvedImageFilename);

        return aiEngineGatewayService.postForBinary(
                "/ai/chat",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping("/generate_video")
    public ResponseEntity<?> generateVideo(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return aiEngineGatewayService.post(
                "/ai/generate_video",
                withTeacherContext(payload, request),
                authToken(request)
        );
    }

    @PostMapping("/extract_video_content")
    public ResponseEntity<?> extractVideoContent(@RequestBody Map<String, Object> payload) {
        return otherService.extractVideoContent(payload);
    }

    private Map<String, Object> withTeacherContext(Map<String, Object> payload, HttpServletRequest request) {
        Map<String, Object> merged = new HashMap<>();
        if (payload != null) {
            merged.putAll(payload);
        }

        if (!merged.containsKey("teacherId") && !merged.containsKey("teacher_id")) {
            Object teacherId = request.getAttribute(AuthContextInterceptor.ATTR_TEACHER_ID);
            if (teacherId != null) {
                merged.put("teacherId", String.valueOf(teacherId));
            }
        }
        return merged;
    }

    private String authToken(HttpServletRequest request) {
        Object authAttr = request.getAttribute(AuthContextInterceptor.ATTR_AUTH_HEADER);
        if (authAttr != null) {
            String text = String.valueOf(authAttr).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.trim().isEmpty()) {
            return null;
        }
        return header.trim();
    }

    private String teacherIdFromRequest(HttpServletRequest request) {
        Object teacherId = request.getAttribute(AuthContextInterceptor.ATTR_TEACHER_ID);
        if (teacherId == null) {
            return null;
        }
        String text = String.valueOf(teacherId).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String text = value.trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private void putIfText(Map<String, String> target, String key, String value) {
        if (target == null || key == null) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return;
        }
        target.put(key, text);
    }

    private void putIfTextObject(Map<String, Object> target, String key, String value) {
        if (target == null || key == null) {
            return;
        }
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return;
        }
        target.put(key, text);
    }

    private boolean toBoolean(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text) || "yes".equals(text) || "on".equals(text);
    }

}
