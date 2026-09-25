package com.processgroup.distillation.domain;

/**
 * Gilliland 权衡曲线「给定实际回流比 R，求理论板数 N」请求。
 *
 * <p>分离任务的底层参数与 {@link ShortcutRequest} 同义，由现有校验逻辑把关；
 * 物料衡算只取决于组成，与进料量无关，故此处不需要进料量。
 *
 * @param feedComposition       进料中轻关键组分摩尔分数 zF
 * @param distillateComposition 馏出液目标组成 xD
 * @param bottomsComposition    釜液目标组成 xB
 * @param feedThermalFactor     进料热状态参数 q
 * @param relativeVolatility    轻/重关键组分相对挥发度 alpha（&gt; 1 才可分离）
 * @param refluxRatio           实际回流比 R（须严格大于最小回流比）
 */
public record GillilandRefluxRequest(
        Double feedComposition,
        Double distillateComposition,
        Double bottomsComposition,
        Double feedThermalFactor,
        Double relativeVolatility,
        Double refluxRatio
) {
}
