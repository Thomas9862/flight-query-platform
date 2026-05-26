package com.flight.query.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.deepseek.api-key}")
    private String apiKey;

    @Value("${langchain4j.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.deepseek.model-name:deepseek-chat}")
    private String modelName;

    @Value("${elasticsearch.server-url:http://localhost:9200}")
    private String esServerUrl;

    @Value("${elasticsearch.index-name:flight_knowledge}")
    private String esIndexName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("[LangChain4jConfig] 初始化DeepSeek ChatModel, model={}", modelName);

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(2000)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("[LangChain4jConfig] 初始化DeepSeek StreamingChatModel, model={}", modelName);

        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(2000)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("[LangChain4jConfig] 初始化Elasticsearch向量存储, url={}, index={}",
                esServerUrl, esIndexName);

        return ElasticsearchEmbeddingStore.builder()
                .serverUrl(esServerUrl)
                .indexName(esIndexName)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("[LangChain4jConfig] 初始化bge-small-zh本地Embedding模型");
        long start = System.currentTimeMillis();

        EmbeddingModel model = new BgeSmallZhEmbeddingModel();

        long cost = System.currentTimeMillis() - start;
        log.info("[LangChain4jConfig] bge-small-zh模型加载完成, 耗时{}ms", cost);

        return model;
    }
}
