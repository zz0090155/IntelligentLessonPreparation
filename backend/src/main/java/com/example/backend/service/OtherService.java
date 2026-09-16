package com.example.backend.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.example.backend.dao.TeacherConversationsRepository;
import com.example.backend.dao.TeacherMessagesRepository;
import com.example.backend.dao.entity.ChatRequest;
import com.example.backend.dao.entity.TeacherConversations;
import com.example.backend.dao.entity.TeacherMessages;
import com.example.backend.dao.entity.VideoRequest;
import com.example.backend.utils.JwtUtils;
import io.reactivex.Flowable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OtherService {

    @Autowired
    private TeacherConversationsRepository teacherConversationsRepository;

    @Autowired
    private TeacherMessagesRepository teacherMessagesRepository;

    @Autowired
    private OssService ossService;

    @Autowired
    private RagService ragService;

    @Autowired
    private AiImageService aiImageService;

    @Autowired
    private VideoAiService videoAiService;

    @Value("${dashscope.api.key}")
    private String dashscopeApiKey;

    @Value("${aliyun.oss.domain}")
    private String ossDomain;

    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".bmp");
    private static final Set<String> ALLOWED_DOC_EXTS = Set.of(".pdf", ".txt", ".docx", ".pptx");
    private static final String DEFAULT_AVATAR = "https://microlesson-files.oss-cn-wuhan-lr.aliyuncs.com/Gemini_Generated_Image_7bwslp7bwslp7bws.png";

    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "ok");
        map.put("service", "教策云枢AI备课助手");
        return ResponseEntity.ok(map);
    }

    public ResponseEntity<?> uploadDoc(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "未提供文件"));
        }

        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_DOC_EXTS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "仅支持 PDF/TXT/DOCX/PPTX 文件"));
        }

        try {
            String savedFilename = ossService.uploadFile(file, ext);
            String status = ragService.buildKnowledgeBase(file, ext);
            return ResponseEntity.ok(Map.of("status", status, "filename", savedFilename));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("detail", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "文件处理失败: " + ex.getMessage()));
        }
    }

    public ResponseEntity<?> clearKb() {
        ragService.clearKnowledgeBase();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    public ResponseEntity<?> uploadImage(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "未提供图片文件"));
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "仅支持图片格式: png/jpg/jpeg/webp/bmp"));
        }

        try {
            String filename = ossService.uploadFile(file, ext);
            String fullImageUrl = ossDomain + "/" + filename;
            String extractedText = extractImageKnowledgeText(fullImageUrl);
            String kbStatus = "skipped";
            if (extractedText != null && !extractedText.trim().isEmpty()) {
                kbStatus = ragService.buildKnowledgeBaseFromText(extractedText);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("filename", filename);
            result.put("knowledge_base_status", kbStatus);
            result.put("extracted_text_preview", extractedText == null ? "" : extractedText.substring(0, Math.min(180, extractedText.length())));
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "图片上传失败: " + ex.getMessage()));
        }
    }

    public ResponseEntity<?> generateImage(Map<String, String> payload) {
        String prompt = payload == null ? "" : String.valueOf(payload.getOrDefault("prompt", "")).trim();
        if (prompt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "prompt 不能为空"));
        }

        try {
            String imageUrl = aiImageService.generateImage(prompt);
            return ResponseEntity.ok(Map.of("status", "success", "image_url", imageUrl));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "图片生成失败: " + ex.getMessage()));
        }
    }

    public ResponseEntity<?> generateVideo(VideoRequest request) {
        String text = request == null ? "" : String.valueOf(request.getText()).trim();
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "text 不能为空"));
        }

        String sourceUrl = DEFAULT_AVATAR;
        String imageFilename = request.getImageFilename();
        if (imageFilename != null && !imageFilename.trim().isEmpty()) {
            String value = imageFilename.trim();
            sourceUrl = value.startsWith("http://") || value.startsWith("https://") ? value : (ossDomain + "/" + value);
        }

        try {
            String videoUrl = videoAiService.generateTalkingHead(text, sourceUrl, request.getVoiceId());
            List<String> boardContent = videoAiService.extractBlackboardContent(text);
            String imagePrompt = videoAiService.buildLessonImagePrompt(text, boardContent);
            String imageUrl = videoAiService.generateLessonImage(imagePrompt);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("video_url", videoUrl);
            response.put("board_content", boardContent);
            response.put("image_url", imageUrl);
            response.put("image_prompt", imagePrompt);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", ex.getMessage()));
        }
    }

    public ResponseEntity<?> extractVideoContent(Map<String, Object> payload) {
        String text = payload == null ? "" : String.valueOf(payload.getOrDefault("text", "")).trim();
        String title = payload == null ? "" : String.valueOf(payload.getOrDefault("title", "")).trim();
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "text 不能为空"));
        }
        try {
            return ResponseEntity.ok(videoAiService.extractVideoContent(text, title));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "视频内容提取失败: " + ex.getMessage()));
        }
    }

    public SseEmitter chatStream(ChatRequest request, String authToken) {
        SseEmitter emitter = new SseEmitter(0L);
        String conversationKey = request.getConversationKey();
        int convId = 0;

        try {
            List<ChatRequest.ChatMessage> reqMessages = request.getMessages();
            ChatRequest.ChatMessage lastMsg = reqMessages.get(reqMessages.size() - 1);
            String userQuestion = lastMsg.getContent();
            String imageUrl = request.getImageFilename();

            if (conversationKey != null && !conversationKey.trim().isEmpty() && authToken != null) {
                String token = normalizeToken(authToken);
                int teacherId = JwtUtils.getIdFromJwt(token);
                TeacherConversations conversation = teacherConversationsRepository
                        .findTeacherConversationsByTeacherIdAndConversationKey(teacherId, conversationKey);
                if (conversation != null) {
                    TeacherMessages userRecord = new TeacherMessages();
                    userRecord.setConversationId(conversation.getId());
                    userRecord.setRole("user");
                    userRecord.setContent(userQuestion);
                    userRecord.setMediaUrl(imageUrl);
                    teacherMessagesRepository.save(userRecord);
                    convId = conversation.getId();
                }
            }

            if (Boolean.TRUE.equals(request.getUseKnowledgeBase())) {
                String context = ragService.searchRelevantContext(userQuestion);
                if (!context.isEmpty()) {
                    lastMsg.setContent("请严格基于以下知识库内容回答：\n" + context + "\n\n用户问题：" + userQuestion);
                }
            }

            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                String fullImageUrl = imageUrl.startsWith("http") ? imageUrl : (ossDomain + "/" + imageUrl);
                handleVisionChat(reqMessages, fullImageUrl, emitter, conversationKey, convId);
            } else {
                handleTextChat(reqMessages, emitter, conversationKey, convId);
            }
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private void handleTextChat(
            List<ChatRequest.ChatMessage> reqMessages,
            SseEmitter emitter,
            String conversationKey,
            int convId
    ) throws Exception {
        List<Message> messages = new ArrayList<>();
        for (ChatRequest.ChatMessage msg : reqMessages) {
            messages.add(Message.builder().role(msg.getRole()).content(msg.getContent()).build());
        }

        GenerationParam param = GenerationParam.builder()
                .apiKey(dashscopeApiKey)
                .model("qwen-plus")
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .build();

        Flowable<GenerationResult> flowable = new Generation().streamCall(param);
        subscribeAndSend(flowable, emitter, false, conversationKey, convId);
    }

    private void handleVisionChat(
            List<ChatRequest.ChatMessage> reqMessages,
            String imageUrl,
            SseEmitter emitter,
            String conversationKey,
            int convId
    ) throws Exception {
        List<MultiModalMessage> messages = new ArrayList<>();
        for (int i = 0; i < reqMessages.size(); i++) {
            ChatRequest.ChatMessage msg = reqMessages.get(i);
            MultiModalMessage.MultiModalMessageBuilder msgBuilder = MultiModalMessage.builder().role(msg.getRole());
            List<Map<String, Object>> content = new ArrayList<>();
            if (i == reqMessages.size() - 1 && msg.getRole().equals(Role.USER.getValue())) {
                content.add(Map.of("image", imageUrl));
            }
            content.add(Map.of("text", msg.getContent()));
            msgBuilder.content(content);
            messages.add(msgBuilder.build());
        }

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(dashscopeApiKey)
                .model("qwen-vl-plus")
                .messages(messages)
                .incrementalOutput(true)
                .build();

        Flowable<MultiModalConversationResult> flowable = new MultiModalConversation().streamCall(param);
        subscribeAndSend(flowable, emitter, true, conversationKey, convId);
    }

    private void subscribeAndSend(
            Flowable<?> flowable,
            SseEmitter emitter,
            boolean isMultiModal,
            String conversationKey,
            int convId
    ) {
        StringBuilder fullAiResponse = new StringBuilder();
        flowable.subscribe(
                result -> {
                    String textDelta = isMultiModal
                            ? String.valueOf(((MultiModalConversationResult) result).getOutput()
                            .getChoices().get(0).getMessage().getContent().get(0).get("text"))
                            : ((GenerationResult) result).getOutput().getChoices().get(0).getMessage().getContent();
                    if (textDelta != null && !textDelta.isEmpty()) {
                        fullAiResponse.append(textDelta);
                        emitter.send(SseEmitter.event().data(Map.of("text", textDelta)));
                    }
                },
                emitter::completeWithError,
                () -> {
                    try {
                        if (conversationKey != null && !conversationKey.trim().isEmpty() && convId > 0) {
                            TeacherMessages aiRecord = new TeacherMessages();
                            aiRecord.setConversationId(convId);
                            aiRecord.setRole("assistant");
                            aiRecord.setContent(fullAiResponse.toString());
                            teacherMessagesRepository.save(aiRecord);
                        }
                    } finally {
                        emitter.complete();
                    }
                }
        );
    }

    private String extractImageKnowledgeText(String imageUrl) {
        try {
            List<MultiModalMessage> messages = new ArrayList<>();
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("image", imageUrl));
            content.add(Map.of("text", "请识别图片中的教学信息，提取可入知识库的纯文本内容。"));
            messages.add(
                    MultiModalMessage.builder()
                            .role(Role.USER.getValue())
                            .content(content)
                            .build()
            );
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(dashscopeApiKey)
                    .model("qwen-vl-plus")
                    .messages(messages)
                    .build();
            MultiModalConversationResult result = new MultiModalConversation().call(param);
            if (result == null
                    || result.getOutput() == null
                    || result.getOutput().getChoices() == null
                    || result.getOutput().getChoices().isEmpty()) {
                return "";
            }
            Object text = result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text");
            return text == null ? "" : String.valueOf(text).trim();
        } catch (Exception ignore) {
            return "";
        }
    }

    private String normalizeToken(String authToken) {
        String text = authToken == null ? "" : authToken.trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return text.substring(7).trim();
        }
        return text;
    }
}
