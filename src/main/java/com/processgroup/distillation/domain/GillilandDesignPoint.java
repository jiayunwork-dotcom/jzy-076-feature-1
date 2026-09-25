package com.processgroup.distillation.domain;

/**
 * Gilliland 权衡曲线上定出的一个设计点：两个渐近锚点 + 一对 (R, N)。
 * 「给 R 求 N」与「给 N 求 R」两条路径返回同一形状，便于正反核对。
 *
 * @param minimumRefluxRatio Underwood 最小回流比 Rmin（X→0 一侧的渐近锚点）
 * @param minimumStages      Fenske 最少理论板数 Nmin（X→1 一侧的渐近锚点）
 * @param refluxRatio        设计点的实际回流比 R
 * @param theoreticalStages  设计点的理论板数 N
 */
public record GillilandDesignPoint(
        double minimumRefluxRatio,
        double minimumStages,
        double refluxRatio,
        double theoreticalStages
) {
}
