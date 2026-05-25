package org.qing.musicagent.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void init() throws Exception {
        System.out.println("正在初始化RAG知识库...");

        // 本地Embedding模型，不需要联网不消耗API
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();

        // 读取txt知识库文件
        ClassPathResource resource = new ClassPathResource("music-knowledge.txt");
        String content = new String(Files.readAllBytes(resource.getFile().toPath()));

        // 把整个txt包装成Document
        Document document = Document.from(content);

        // 切割成小段，每段200字，相邻段落重叠20字
        DocumentSplitter splitter = DocumentSplitters.recursive(200, 20);
        List<TextSegment> segments = splitter.split(document);

        System.out.println("共切割成" + segments.size() + "个片段，开始向量化...");

        // 每段文字转成向量存入知识库
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel.embed(segment.text()).content();
            embeddingStore.add(embedding, segment);
        }

        System.out.println("RAG初始化完成，共加载" + segments.size() + "个知识片段");
    }

    public String retrieve(String query) {
        // 用户问题转成向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 找最相似的3个片段
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(queryEmbedding)
                                .maxResults(3)
                                .build()
                ).matches();

        if (matches.isEmpty()) return "";

        return matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n---\n"));
    }
}