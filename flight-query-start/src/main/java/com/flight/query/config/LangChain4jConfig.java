package com.flight.query.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置
 * <p>
 * 注册两个核心Bean：
 * 1. ChatLanguageModel - 通义千问大模型，用于SQL生成和结果解释
 * 2. EmbeddingModel - bge-small-zh ONNX本地模型，用于字段组向量匹配
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.dashscope.api-key}")
    private String apiKey;

    @Value("${langchain4j.dashscope.model-name:qwen-plus}")
    private String modelName;

    /**
     * 通义千问 ChatModel
     * <p>
     * 用于：
     * 1. 根据用户问题生成SQL
     * 2. 对查询结果进行二次解释
     * 3. 从用户问题中提取业务实体
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("[LangChain4jConfig] 初始化通义千问ChatModel, model={}", modelName);

        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1f)    // 低温度，SQL生成需要确定性
                .maxTokens(2000)
                .build();
    }

    /**
     * bge-small-zh ONNX 本地 Embedding 模型
     * <p>
     * 特点：
     * - 专门针对中文优化的轻量级Embedding模型
     * - 模型文件打包在jar中，JVM进程内本地运行
     * - 无需部署外部服务，无API调用费用
     * - 毫秒级响应，适合实时匹配场景
     * <p>
     * 用于：服务启动时向量化字段组描述，查询时向量化用户问题并计算相似度
     */
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
