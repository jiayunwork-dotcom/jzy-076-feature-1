package com.processgroup.distillation.domain;

import java.util.List;

/**
 * 逐板阶梯结果。
 *
 * @param requestedRefluxRatio 实际使用的回流比
 * @param totalStages          理论板总数（阶梯数）
 * @param feedStageIndex       进料板位置（1 起算，即第一块使用提馏段操作线的阶梯）
 * @param balance              物料衡算反推出的各流股流量
 * @param stages               每一块理论板的组成记录
 */
public record SteppingResponse(
        double requestedRefluxRatio,
        int totalStages,
        int feedStageIndex,
        StreamFlows balance,
        List<StageResult> stages
) {
}
