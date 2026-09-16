package com.example.backend.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

@Service
public class PptService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "你是一个专业的教案 PPT 设计师。请根据用户的主题，输出一个标准的 JSON 格式数据。\n" +
                    "不要包含 markdown 代码块（```json），直接返回 JSON 字符串。\n\n" +
                    "JSON 结构要求：\n" +
                    "{\n" +
                    "  \"title\": \"PPT主标题\",\n" +
                    "  \"author\": \"汇报人姓名\",\n" +
                    "  \"slides\": [\n" +
                    "    {\"title\": \"第一页标题\", \"content\": [\"核心要点1\", \"核心要点2\", \"核心要点3\"]},\n" +
                    "    {\"title\": \"第二页标题\", \"content\": [\"内容...\"]}\n" +
                    "  ]\n" +
                    "}";

    public JsonNode generatePptData(String topic) throws Exception {
        Message systemMsg = Message.builder().role(Role.SYSTEM.getValue()).content(SYSTEM_PROMPT).build();
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(topic).build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model("qwen-plus")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.TEXT)
                .build();

        Generation gen = new Generation();
        GenerationResult result = gen.call(param);

        String rawText = result.getOutput().getText();
        String cleanedJson = cleanJsonText(rawText);

        return objectMapper.readTree(cleanedJson);
    }

    public byte[] createPptx(JsonNode pptData) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String titleText = pptData.path("title").asText("课程课件");
            String authorText = pptData.path("author").asText("");

            XSLFSlideMaster defaultMaster = ppt.getSlideMasters().get(0);
            XSLFSlideLayout titleLayout = defaultMaster.getLayout(SlideLayout.TITLE);
            XSLFSlide titleSlide = ppt.createSlide(titleLayout);

            XSLFTextShape titleShape = titleSlide.getPlaceholder(0);
            if (titleShape != null) titleShape.setText(titleText);

            XSLFTextShape subtitleShape = titleSlide.getPlaceholder(1);
            if (subtitleShape != null) subtitleShape.setText(authorText);

            XSLFSlideLayout contentLayout = defaultMaster.getLayout(SlideLayout.TITLE_AND_CONTENT);
            JsonNode slides = pptData.path("slides");

            if (slides.isArray()) {
                for (JsonNode slideData : slides) {
                    XSLFSlide slide = ppt.createSlide(contentLayout);

                    XSLFTextShape slideTitle = slide.getPlaceholder(0);
                    if (slideTitle != null) {
                        slideTitle.setText(slideData.path("title").asText(""));
                    }

                    XSLFTextShape slideContent = slide.getPlaceholder(1);
                    if (slideContent != null) {
                        slideContent.clearText();
                        JsonNode contentArray = slideData.path("content");
                        if (contentArray.isArray()) {
                            for (JsonNode item : contentArray) {
                                XSLFTextParagraph p = slideContent.addNewTextParagraph();
                                XSLFTextRun r = p.addNewTextRun();
                                r.setText(item.asText());
                            }
                        }
                    }
                }
            }

            ppt.write(out);
            return out.toByteArray();
        }
    }

    private String cleanJsonText(String content) {
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }
}