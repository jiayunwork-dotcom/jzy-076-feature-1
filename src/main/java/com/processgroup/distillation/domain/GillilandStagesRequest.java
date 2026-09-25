package com.processgroup.distillation.domain;

/**
 * Gilliland 权衡曲线「给定工程可接受的理论板数 N，反求实际回流比 R」请求。
 *
 * @param feedComposition       进料中轻关键组分摩尔分数 zF
 * @param distillateComposition 馏出液目标组成 xD
 * @param bottomsComposition    釜液目标组成 xB
 * @param feedThermalFactor     进料热状态参数 q
 * @param relativeVolatility    轻/重关键组分相对挥发度 alpha（&gt; 1 才可分离）
 * @param targetStages          工程上愿意接受的理论板数 N（须严格大于最少理论板 Nmin）
 */
public record GillilandStagesRequest(
        Double feedComposition,
        Double distillateComposition,
        Double bottomsComposition,
        Double feedThermalFactor,
        Double relativeVolatility,
        Double targetStages
) {
}
