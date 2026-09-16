package com.example.backend.service;

import com.example.backend.dao.TeacherRepository;
import com.example.backend.utils.ErrorResult;
import com.example.backend.utils.JwtUtils;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StudyPackExportService {

    @Autowired
    private TeacherRepository teacherRepository;

    public ResponseEntity<?> exportDocx(Map<String, Object> payload, String authToken) {
        Integer teacherId = resolveTeacherId(authToken);
        if (teacherId == null) {
            return ResponseEntity.status(403).body(ErrorResult.error("教师权限不足"));
        }

        String exportType = asText(payload.get("export_type")).toLowerCase(Locale.ROOT);
        String title = asText(payload.get("title"));
        if (title.isEmpty()) {
            title = "学习资料导出";
        }

        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTitle(document, title);
            if ("quiz".equals(exportType)) {
                writeQuizSection(document, payload.get("quiz"));
            } else {
                writeOutlineSection(document, asText(payload.get("outline")));
            }
            writeFooter(document, teacherId);
            document.write(out);

            byte[] bytes = out.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ));
            String filename = buildFilename(exportType);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("detail", "导出失败: " + ex.getMessage()));
        }
    }

    private void writeTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(title);
    }

    private void writeOutlineSection(XWPFDocument document, String outline) {
        String text = outline == null ? "" : outline.trim();
        if (text.isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("未提供提纲内容。");
            return;
        }

        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int headingLevel = markdownHeadingLevel(line);
            String content = stripMarkdownHeading(line);
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            if (headingLevel > 0) {
                run.setBold(true);
                run.setFontSize(Math.max(12, 18 - headingLevel * 2));
            } else {
                run.setFontSize(11);
            }
            run.setText(content);
        }
    }

    private void writeQuizSection(XWPFDocument document, Object quizRaw) {
        List<Map<String, Object>> quizList = normalizeQuizList(quizRaw);
        if (quizList.isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("未提供练习题内容。");
            return;
        }

        int index = 1;
        for (Map<String, Object> item : quizList) {
            String question = asText(item.get("question"));
            String answer = asText(item.get("answer"));
            String analysis = asText(item.get("analysis"));
            List<String> options = normalizeOptions(item.get("options"));

            XWPFParagraph qParagraph = document.createParagraph();
            XWPFRun qRun = qParagraph.createRun();
            qRun.setBold(true);
            qRun.setFontSize(12);
            qRun.setText("第 " + index + " 题： " + (question.isEmpty() ? "（未提供题干）" : question));

            for (String option : options) {
                XWPFParagraph optionParagraph = document.createParagraph();
                XWPFRun optionRun = optionParagraph.createRun();
                optionRun.setText(option);
            }

            XWPFParagraph answerParagraph = document.createParagraph();
            XWPFRun answerRun = answerParagraph.createRun();
            answerRun.setBold(true);
            answerRun.setText("答案： " + (answer.isEmpty() ? "未给出" : answer));

            XWPFParagraph analysisParagraph = document.createParagraph();
            XWPFRun analysisRun = analysisParagraph.createRun();
            analysisRun.setText("解析： " + (analysis.isEmpty() ? "无" : analysis));

            document.createParagraph();
            index += 1;
        }
    }

    private void writeFooter(XWPFDocument document, Integer teacherId) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun run = paragraph.createRun();
        run.setFontSize(9);
        run.setItalic(true);
        run.setText("生成教师ID: " + teacherId + "  生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private Integer markdownHeadingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level += 1;
        }
        if (level > 0 && level < line.length() && Character.isWhitespace(line.charAt(level))) {
            return Math.min(level, 6);
        }
        return 0;
    }

    private String stripMarkdownHeading(String line) {
        int level = markdownHeadingLevel(line);
        if (level == 0) {
            return line;
        }
        return line.substring(level).trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeQuizList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> normalizeOptions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (Object option : list) {
            if (option instanceof Map<?, ?> map) {
                String key = asText(map.get("label"));
                String text = asText(map.get("text"));
                String merged = (key + " " + text).trim();
                if (!merged.isEmpty()) {
                    options.add(merged);
                }
                continue;
            }
            String text = asText(option);
            if (!text.isEmpty()) {
                options.add(text);
            }
        }
        return options;
    }

    private String buildFilename(String exportType) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String prefix = "quiz".equals(exportType) ? "study_quiz" : "study_outline";
        return prefix + "_" + stamp + ".docx";
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

    private String asText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
