package com.processgroup.distillation.domain;

/**
 * Gilliland 权衡曲线上的一个设计点：同一份 Nmin/Rmin 基准、同一组无量纲坐标。
 *
 * @param minimumRefluxRatio  Underwood 最小回流比 Rmin
 * @param minimumStages       Fenske 全回流最少理论板数 Nmin
 * @param refluxRatio         实际回流比 R
 * @param theoreticalStages   该回流比下所需理论板数 N（连续值，含再沸器）
 * @param x                   横坐标 (R-Rmin)/(R+1)
 * @param y                   纵坐标 (N-Nmin)/(N+1)
 */
public record GillilandPointResponse(
        double minimumRefluxRatio,
        double minimumStages,
        double refluxRatio,
        double theoreticalStages,
        double x,
        double y
) {
}
