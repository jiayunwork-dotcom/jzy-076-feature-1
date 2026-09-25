package com.processgroup.distillation.domain;

/**
 * 「给 R 求 N」请求：分离任务底层参数 + 工程上打算采用的实际回流比。
 *
 * @param feedComposition    进料中轻关键组分摩尔分数 zF，0 &lt; xB &lt; zF &lt; xD &lt; 1
 * @param distillateComposition 馏出液目标组成 xD
 * @param bottomsComposition    釜液目标组成 xB
 * @param feedThermalFactor  进料热状态参数 q（泡点 q=1，饱和蒸汽 q=0，过冷 q&gt;1，过热 q&lt;0）
 * @param relativeVolatility 轻/重关键组分相对挥发度 alpha（&gt; 1 才可分离）
 * @param refluxRatio        实际回流比 R（须大于最小回流比，否则曲线上不存在有限板数）
 */
public record RefluxToStagesRequest(
        Double feedComposition,
        Double distillateComposition,
        Double bottomsComposition,
        Double feedThermalFactor,
        Double relativeVolatility,
        Double refluxRatio
) {
}
