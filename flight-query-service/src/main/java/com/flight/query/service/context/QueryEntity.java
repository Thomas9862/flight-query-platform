package com.flight.query.service.context;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话业务实体
 * <p>
 * 每轮对话结束后，从用户问题和查询结果中显式提取的业务实体。
 * 用于下一轮对话生成SQL时注入Prompt，解决"那这个航司呢"等指代问题。
 * <p>
 * 合并规则：新值非null时覆盖旧值，null不覆盖（保留上下文）。
 */
@Data
public class QueryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前讨论的航司名称 */
    private String airline;

    /** 当前时间范围描述（如"上周"、"2025年1月"） */
    private String dateRange;

    /** 当前地区/大洲（如"欧洲"、"亚太"） */
    private String region;

    /** 当前分析的航线（如"LHR-PVG"） */
    private String route;

    /** 当前分析指标（如"利润"、"订单量"、"收入"） */
    private String metric;

    /** 当前品牌（如"skytours"） */
    private String brand;

    /** 当前市场（如"UK"、"DE"） */
    private String market;

    /**
     * 判断是否有任何非空字段
     */
    public boolean hasAnyField() {
        return airline != null || dateRange != null || region != null
                || route != null || metric != null || brand != null || market != null;
    }

    /**
     * 合并实体：新值非null时覆盖旧值
     *
     * @param newEntity 新提取的实体
     * @return 合并后的实体（修改并返回自身）
     */
    public QueryEntity merge(QueryEntity newEntity) {
        if (newEntity == null) {
            return this;
        }
        if (newEntity.getAirline() != null) {
            this.airline = newEntity.getAirline();
        }
        if (newEntity.getDateRange() != null) {
            this.dateRange = newEntity.getDateRange();
        }
        if (newEntity.getRegion() != null) {
            this.region = newEntity.getRegion();
        }
        if (newEntity.getRoute() != null) {
            this.route = newEntity.getRoute();
        }
        if (newEntity.getMetric() != null) {
            this.metric = newEntity.getMetric();
        }
        if (newEntity.getBrand() != null) {
            this.brand = newEntity.getBrand();
        }
        if (newEntity.getMarket() != null) {
            this.market = newEntity.getMarket();
        }
        return this;
    }

    /**
     * 将实体转为Prompt注入文本
     */
    public String toPromptText() {
        if (!hasAnyField()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n【对话上下文，当前问题中未提及的信息从此处补充】\n");

        if (airline != null) {
            sb.append("- 当前讨论的航司：").append(airline).append("\n");
        }
        if (dateRange != null) {
            sb.append("- 当前时间范围：").append(dateRange).append("\n");
        }
        if (region != null) {
            sb.append("- 当前地区：").append(region).append("\n");
        }
        if (route != null) {
            sb.append("- 当前航线：").append(route).append("\n");
        }
        if (metric != null) {
            sb.append("- 分析指标：").append(metric).append("\n");
        }
        if (brand != null) {
            sb.append("- 当前品牌：").append(brand).append("\n");
        }
        if (market != null) {
            sb.append("- 当前市场：").append(market).append("\n");
        }

        return sb.toString();
    }
}
