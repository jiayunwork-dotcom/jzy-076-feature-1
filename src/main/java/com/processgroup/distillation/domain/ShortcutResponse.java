package com.processgroup.distillation.domain;

/**
 * 简捷法计算响应：对外只暴露最小回流比、最少理论板数、逐板阶梯结果三类内容。
 *
 * @param minimumRefluxRatio Underwood 最小回流比
 * @param minimumStages      Fenske 全回流最少理论板数
 * @param stepping           给定回流比下的逐板阶梯结果
 */
public record ShortcutResponse(
        double minimumRefluxRatio,
        double minimumStages,
        SteppingResponse stepping
) {
}
