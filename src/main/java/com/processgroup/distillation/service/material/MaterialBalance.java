package com.processgroup.distillation.service.material;

/**
 * 总物料衡算与轻组分衡算结果。
 *
 * @param feedFlow        进料量 F
 * @param distillateFlow  馏出液流量 D
 * @param bottomsFlow     釜液流量 B
 * @param feedComposition       进料组成 zF
 * @param distillateComposition 馏出液组成 xD
 * @param bottomsComposition    釜液组成 xB
 * @param totalResidual        总物料衡算残差 |F - D - B|
 * @param componentResidual    轻组分衡算残差 |F*zF - D*xD - B*xB|
 */
public record MaterialBalance(
        double feedFlow,
        double distillateFlow,
        double bottomsFlow,
        double feedComposition,
        double distillateComposition,
        double bottomsComposition,
        double totalResidual,
        double componentResidual
) {
}
