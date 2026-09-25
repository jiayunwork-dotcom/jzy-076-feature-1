package com.processgroup.distillation.domain;

/**
 * 物料衡算反推的流股流量与衡算残差。
 *
 * @param feedFlow        进料量 F
 * @param distillateFlow  馏出液流量 D
 * @param bottomsFlow     釜液流量 B
 * @param residual        总物料衡算残差 |F - (D + B)|
 * @param componentResidual 轻组分衡算残差 |F*zF - (D*xD + B*xB)|
 */
public record StreamFlows(
        double feedFlow,
        double distillateFlow,
        double bottomsFlow,
        double residual,
        double componentResidual
) {
}
