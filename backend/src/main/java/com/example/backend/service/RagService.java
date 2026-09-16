package com.example.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RagService {

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${rag.kb.storage-path:data/rag_kb_chunks.json}")
    private String kbStoragePath;

    private InMemoryEmbeddingStore<TextSegment> vectorStore;
    private QwenEmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> chunkArchive = new ArrayList<>();

    @PostConstruct
    public void init() {
        embeddingModel = QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-v2")
                .build();
        vectorStore = new InMemoryEmbeddingStore<>();
        loadArchiveAndRebuild();
    }

    public synchronized String buildKnowledgeBase(MultipartFile file, String ext) throws Exception {
        String normalizedExt = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        String textContent = extractText(file, normalizedExt);
        return buildKnowledgeBaseFromText(textContent);
    }

    public synchronized String buildKnowledgeBaseFromText(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("知识库文本为空");
        }

        Document document = Document.from(text);
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.split(document);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("未提取到可索引文本");
        }

        vectorStore.addAll(embeddingModel.embedAll(segments).content(), segments);
        for (TextSegment segment : segments) {
            String value = segment.text() == null ? "" : segment.text().trim();
            if (!value.isEmpty()) {
                chunkArchive.add(value);
            }
        }
        persistArchive();
        return "构建成功";
    }

    public synchronized void clearKnowledgeBase() {
        vectorStore = new InMemoryEmbeddingStore<>();
        chunkArchive.clear();
        deleteArchiveFile();
    }

    public synchronized String searchRelevantContext(String queryText) {
        if (vectorStore == null || queryText == null || queryText.trim().isEmpty()) {
            return "";
        }
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        List<EmbeddingMatch<TextSegment>> matches = vectorStore.findRelevant(queryEmbedding, 3, 0.6);
        StringBuilder contextBuilder = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : matches) {
            contextBuilder.append(match.embedded().text()).append('\n');
        }
        return contextBuilder.toString().trim();
    }

    private String extractText(MultipartFile file, String ext) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未提供文件");
        }
        try (InputStream is = file.getInputStream()) {
            switch (ext) {
                case ".pdf":
                    Document pdfDoc = new ApachePdfBoxDocumentParser().parse(is);
                    return pdfDoc.text();
                case ".txt":
                    Document txtDoc = new TextDocumentParser().parse(is);
                    return txtDoc.text();
                case ".docx":
                    return parseDocx(is);
                case ".pptx":
                    return parsePptx(is);
                default:
                    throw new IllegalArgumentException("仅支持 PDF/TXT/DOCX/PPTX 文件");
            }
        }
    }

    private String parseDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument docx = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : docx.getParagraphs()) {
                String line = paragraph.getText();
                if (line != null && !line.trim().isEmpty()) {
                    sb.append(line.trim()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private String parsePptx(InputStream inputStream) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            StringBuilder sb = new StringBuilder();
            ppt.getSlides().forEach(slide -> slide.getShapes().forEach(shape -> {
                if (shape instanceof XSLFTextShape textShape) {
                    String line = textShape.getText();
                    if (line != null && !line.trim().isEmpty()) {
                        sb.append(line.trim()).append('\n');
                    }
                }
            }));
            return sb.toString();
        }
    }

    private void loadArchiveAndRebuild() {
        Path path = Path.of(kbStoragePath);
        if (!Files.exists(path)) {
            return;
        }
        try {
            List<String> stored = objectMapper.readValue(
                    Files.readString(path, StandardCharsets.UTF_8),
                    new TypeReference<List<String>>() {
                    }
            );
            if (stored == null || stored.isEmpty()) {
                return;
            }
            chunkArchive.clear();
            chunkArchive.addAll(stored);
            List<TextSegment> segments = stored.stream().map(TextSegment::from).toList();
            vectorStore.addAll(embeddingModel.embedAll(segments).content(), segments);
        } catch (Exception ignore) {
            // ignore broken archive and continue with empty store
        }
    }

    private void persistArchive() {
        try {
            Path path = Path.of(kbStoragePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, objectMapper.writeValueAsString(chunkArchive), StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            // persist failure should not break request
        }
    }

    private void deleteArchiveFile() {
        try {
            Files.deleteIfExists(Path.of(kbStoragePath));
        } catch (Exception ignore) {
            // ignore
        }
    }
}
