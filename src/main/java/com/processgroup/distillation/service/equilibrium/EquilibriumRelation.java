package com.processgroup.distillation.service.equilibrium;

/**
 * 相平衡关系 —— 全服务唯一定义处。
 *
 * <p>恒相对挥发度二元体系（轻关键组分对重关键组分，{@code alpha_H = 1}）：
 * <ul>
 *   <li>平衡线：{@code y = alpha*x / (1 + (alpha-1)*x)}</li>
 *   <li>逆形式：{@code x = y / (alpha - (alpha-1)*y)}</li>
 * </ul>
 *
 * <p>精馏段操作线、提馏段操作线、逐板阶梯全部必须引用同一个本类实例，
 * 不允许在别处再维护一份平衡曲线，避免两段平衡关系对不上。
 */
public final class EquilibriumRelation {

    private final double alpha;

    public EquilibriumRelation(double alpha) {
        if (!(Double.isFinite(alpha)) || alpha <= 0.0) {
            throw new IllegalArgumentException("相对挥发度必须为正数");
        }
        this.alpha = alpha;
    }

    /**
     * 平衡线：由液相组成 x 求平衡气相组成 y。
     */
    public double vaporInEquilibrium(double x) {
        return alpha * x / (1.0 + (alpha - 1.0) * x);
    }

    /**
     * 平衡线逆形式：由气相组成 y 反求与之平衡的液相组成 x。
     */
    public double liquidInEquilibrium(double y) {
        return y / (alpha - (alpha - 1.0) * y);
    }

    public double relativeVolatility() {
        return alpha;
    }
}
