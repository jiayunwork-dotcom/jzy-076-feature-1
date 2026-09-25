package com.processgroup.distillation.domain;

/**
 * 一块理论板（一级阶梯）的逐板组成记录。
 *
 * @param stageNumber      板序号，从塔顶第一块理论板起 1 起算
 * @param liquidComposition 该级与离开蒸汽平衡的液相组成 x_n
 * @param vaporComposition  该级阶梯终点蒸汽组成 y_{n+1}（下一级操作线值）
 * @param section           该级所属塔段（RECTIFYING 精馏段 / STRIPPING 提馏段）
 * @param feedStage         是否为进料板
 */
public record StageResult(
        int stageNumber,
        double liquidComposition,
        double vaporComposition,
        Section section,
        boolean feedStage
) {
}
