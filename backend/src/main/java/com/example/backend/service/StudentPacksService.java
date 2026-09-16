package com.example.backend.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.example.backend.dao.StudentPackRepository;
import com.example.backend.dao.StudentRepository;
import com.example.backend.dao.TeacherRepository;
import com.example.backend.dao.entity.StudentPacks;
import com.example.backend.utils.ErrorResult;
import com.example.backend.utils.JwtUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StudentPacksService {

    @Autowired
    private StudentPackRepository studentPackRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private OssService ossService;

    @Value("${dashscope.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "你是一名教学内容整理助手。请阅读上传资料，输出 JSON 对象，"
                    + "必须包含 outline、flashcards、quiz 三个字段，且仅输出 JSON。";

    private static final Set<String> ALLOWED_EXTS = Set.of(".pdf", ".pptx", ".docx", ".txt");

    public ResponseEntity<?> uploadAndGenerate(MultipartFile file, String authToken) {
        Integer ownerId = resolveStudentId(authToken);
        return uploadAndGenerateInternal(file, ownerId);
    }

    public ResponseEntity<?> uploadAndGenerateForTeacher(MultipartFile file, String authToken) {
        Integer teacherId = resolveTeacherId(authToken);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }
        return uploadAndGenerateInternal(file, toTeacherOwnerId(teacherId));
    }

    public ResponseEntity<Map<String, Object>> listStudentHistory(String authToken) {
        Integer ownerId = resolveStudentId(authToken);
        if (ownerId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("学生权限不足"));
        }
        return listHistoryInternal(ownerId);
    }

    public ResponseEntity<Map<String, Object>> listTeacherHistory(String authToken) {
        Integer teacherId = resolveTeacherId(authToken);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }
        return listHistoryInternal(toTeacherOwnerId(teacherId));
    }

    public ResponseEntity<Map<String, Object>> getHistoryDetail(String authToken, int packId) throws JsonProcessingException {
        Integer ownerId = resolveStudentId(authToken);
        if (ownerId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("学生权限不足"));
        }
        return getHistoryDetailInternal(ownerId, packId);
    }

    public ResponseEntity<Map<String, Object>> getTeacherHistoryDetail(String authToken, int packId) throws JsonProcessingException {
        Integer teacherId = resolveTeacherId(authToken);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }
        return getHistoryDetailInternal(toTeacherOwnerId(teacherId), packId);
    }

    public String parseFileToText(MultipartFile file, String extension) throws Exception {
        try (InputStream is = file.getInputStream()) {
            switch (extension.toLowerCase(Locale.ROOT)) {
                case ".pdf":
                    try (PDDocument document = PDDocument.load(is)) {
                        return new PDFTextStripper().getText(document);
                    }
                case ".docx":
                    try (XWPFDocument docx = new XWPFDocument(is)) {
                        StringBuilder sb = new StringBuilder();
                        for (XWPFParagraph p : docx.getParagraphs()) {
                            sb.append(p.getText()).append('\n');
                        }
                        return sb.toString();
                    }
                case ".pptx":
                    try (XMLSlideShow ppt = new XMLSlideShow(is)) {
                        StringBuilder sb = new StringBuilder();
                        ppt.getSlides().forEach(slide -> slide.getShapes().forEach(shape -> {
                            if (shape instanceof XSLFTextShape textShape) {
                                sb.append(textShape.getText()).append('\n');
                            }
                        }));
                        return sb.toString();
                    }
                case ".txt":
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                default:
                    throw new IllegalArgumentException("仅支持 PDF/PPTX/Word/TXT 文件");
            }
        }
    }

    public String generateStudentPack(String textContent) throws Exception {
        String limitedText = textContent.length() > 10000 ? textContent.substring(0, 10000) : textContent;
        String prompt =
                "请按如下 JSON 输出，不能输出 markdown 代码块：\n"
                        + "{\n"
                        + "  \"outline\": \"...\",\n"
                        + "  \"flashcards\": [{\"front\":\"...\",\"back\":\"...\"}],\n"
                        + "  \"quiz\": [{\"question\":\"...\",\"options\":[\"A...\",\"B...\",\"C...\",\"D...\"],\"answer\":\"A\",\"analysis\":\"...\"}]\n"
                        + "}\n"
                        + "资料如下：\n"
                        + limitedText;

        Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(SYSTEM_PROMPT).build();
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(prompt).build();
        GenerationParam param = GenerationParam.builder()
                .model("qwen-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.TEXT)
                .apiKey(apiKey)
                .build();
        GenerationResult result = new Generation().call(param);
        return cleanJsonText(result.getOutput().getText());
    }

    private ResponseEntity<?> uploadAndGenerateInternal(MultipartFile file, Integer ownerId) {
        String filename = file == null ? null : file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "未提供文件"));
        }

        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "仅支持 PDF/PPTX/Word/TXT 文件"));
        }

        try {
            String uploadedFilename = ossService.uploadFile(file, ext);
            String content = parseFileToText(file, ext);
            String rawJsonString = generateStudentPack(content);
            JsonNode rootNode = objectMapper.readTree(rawJsonString);

            if (ownerId != null) {
                StudentPacks pack = new StudentPacks();
                pack.setStudentId(ownerId);
                pack.setFilename(uploadedFilename);
                pack.setOutline(rootNode.path("outline").asText(""));
                pack.setFlashcardsJson(rootNode.path("flashcards").toString());
                pack.setQuizJson(rootNode.path("quiz").toString());
                studentPackRepository.save(pack);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .body(rawJsonString);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("detail", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "服务处理失败: " + ex.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> listHistoryInternal(Integer ownerId) {
        List<StudentPacks> list = studentPackRepository.findStudentPacksByStudentIdOrderByCreatedAtDesc(ownerId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (StudentPacks pack : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", pack.getId());
            item.put("filename", pack.getFilename());
            item.put("created_at", pack.getCreatedAt());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("items", items);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> getHistoryDetailInternal(Integer ownerId, int packId) throws JsonProcessingException {
        StudentPacks pack = studentPackRepository.findStudentPacksByStudentIdAndId(ownerId, packId);
        if (pack == null) {
            return ResponseEntity.status(404).body(ErrorResult.error("记录不存在"));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("outline", pack.getOutline());
        data.put("flashcards", objectMapper.readValue(
                safeJsonArray(pack.getFlashcardsJson()),
                new TypeReference<List<Map<String, Object>>>() {
                }
        ));
        data.put("quiz", objectMapper.readValue(
                safeJsonArray(pack.getQuizJson()),
                new TypeReference<List<Map<String, Object>>>() {
                }
        ));
        data.put("filename", pack.getFilename());
        data.put("created_at", pack.getCreatedAt());

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    private String cleanJsonText(String content) {
        String cleaned = String.valueOf(content)
                .replace("```json", "")
                .replace("```", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private String safeJsonArray(String jsonText) {
        String text = jsonText == null ? "" : jsonText.trim();
        return text.isEmpty() ? "[]" : text;
    }

    private Integer resolveStudentId(String authToken) {
        String token = extractBearerToken(authToken);
        if (token == null) {
            return null;
        }
        try {
            if (!JwtUtils.checkToken(token)) {
                return null;
            }
            int id = JwtUtils.getIdFromJwt(token);
            return studentRepository.existsStudentById(id) ? id : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer resolveTeacherId(String authToken) {
        String token = extractBearerToken(authToken);
        if (token == null) {
            return null;
        }
        try {
            if (!JwtUtils.checkToken(token)) {
                return null;
            }
            int id = JwtUtils.getIdFromJwt(token);
            return teacherRepository.existsTeacherById(id) ? id : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer toTeacherOwnerId(Integer teacherId) {
        return -Math.abs(teacherId);
    }

    private String extractBearerToken(String authToken) {
        if (authToken == null) {
            return null;
        }
        String text = authToken.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            text = text.substring(7).trim();
        }
        return text.isEmpty() ? null : text;
    }
}
