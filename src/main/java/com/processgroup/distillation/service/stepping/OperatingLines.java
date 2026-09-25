package com.processgroup.distillation.service.stepping;

/**
 * 一对操作线及其几何/流量信息。
 *
 * @param rectifying    精馏段操作线
 * @param stripping     提馏段操作线
 * @param intersectionX 两线交点横坐标 xq（在 q 线上）
 * @param intersectionY 两线交点纵坐标 yq
 * @param rectLiquidFlow    精馏段液相流量 L
 * @param rectVaporFlow     精馏段气相流量 V
 * @param stripLiquidFlow   提馏段液相流量 L'
 * @param stripVaporFlow    提馏段气相流量 V'
 */
public record OperatingLines(
        OperatingLine rectifying,
        OperatingLine stripping,
        double intersectionX,
        double intersectionY,
        double rectLiquidFlow,
        double rectVaporFlow,
        double stripLiquidFlow,
        double stripVaporFlow
) {
}
