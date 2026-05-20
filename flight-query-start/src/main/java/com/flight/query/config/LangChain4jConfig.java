package com.flight.query.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.dashscope.api-key}")
    private String apiKey;

    @Value("${langchain4j.dashscope.model-name:qwen-plus}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("[LangChain4jConfig] 初始化通义千问ChatModel, model={}", modelName);

        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1f)
                .maxTokens(2000)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        log.info("[LangChain4jConfig] 初始化通义千问StreamingChatModel, model={}", modelName);

        return QwenStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1f)
                .maxTokens(2000)
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
