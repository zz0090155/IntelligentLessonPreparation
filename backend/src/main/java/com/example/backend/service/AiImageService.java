package com.example.backend.service;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AiImageService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    public String generateImage(String prompt) throws Exception {
        ImageSynthesisParam param = ImageSynthesisParam.builder()
                .apiKey(apiKey)
                .model("wanx-v1")
                .prompt(prompt)
                .size("1024*1024")
                .n(1)
                .build();

        ImageSynthesis imageSynthesis = new ImageSynthesis();

        ImageSynthesisResult result = imageSynthesis.call(param);

        if (result != null && result.getOutput() != null &&
                result.getOutput().getResults() != null &&
                !result.getOutput().getResults().isEmpty()) {

            Map<String, String> firstResult = result.getOutput().getResults().get(0);

            return firstResult.get("url");
        } else {
            throw new RuntimeException("未能获取到图片链接");
        }
    }
}