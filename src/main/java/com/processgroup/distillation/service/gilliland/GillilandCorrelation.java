package com.processgroup.distillation.service.gilliland;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.springframework.stereotype.Component;

/**
 * Gilliland 经验关联 —— 全服务唯一定义处。
 *
 * <p>把两个无量纲量绑成一条单调权衡曲线：
 * <ul>
 *   <li>{@code X = (R - Rmin)/(R + 1)}，实际回流比相对最小回流比的宽裕度；</li>
 *   <li>{@code Y = (N - Nmin)/(N + 1)}，实际理论板数相对最少理论板的多用程度。</li>
 * </ul>
 *
 * <p>采用 Gilliland（1940）对其经典关联图的指数拟合：
 * <pre>
 *   Y = 1 - exp( A(X) * (X - 1)/sqrt(X) )，其中 A(X) = (1 + 54.4 X)/(11 + 117.2 X)
 * </pre>
 * 两个渐近端点严格落位：{@code X→0 ⇒ Y→1}（R→Rmin 时无有限板数够用，塔要无穷高），
 * {@code X=1 ⇒ Y=0}（全回流极限，板数退化为 Nmin）。曲线在 (0,1) 上严格单调递减。
 *
 * <p>正读 {@link #yAtX(double)} 与反查 {@link #xAtY(double)} 共用同一条关系式：
 * 反查不是另凑一套近似式，而是在同一条曲线上解同一个方程，因此任意设计点正反
 * 读数必然对得上。反查利用 Y 对 X 的严格单调性：在对数空间 {@code h(X)=ln(1-Y)}
 * 上做带解析导数的牛顿迭代（二次收敛），并用单调括号做保护步——牛顿跨出括号时
 * 退一步二分，保证稳定收敛、绝不闷头二分。
 *
 * <p>本模块只负责这条经验曲线本身，不重推 Nmin（Fenske）/ Rmin（Underwood），
 * 也不认识 R、N；坐标量与物理量的互化由编排层完成。
 */
@Component
public class GillilandCorrelation {

    private static final double C1 = 54.4;
    private static final double C2 = 11.0;
    private static final double C3 = 117.2;

    /** 反查最大迭代步数（带括号保护，实际通常 4~6 步即收敛）。 */
    private static final int MAX_INVERSE_ITERATIONS = 60;
    /** 对数空间根的相对收敛容差。 */
    private static final double ROOT_TOLERANCE = 1.0e-13;

    /**
     * 正读：由横坐标 X 求纵坐标 Y。
     *
     * @param x 无量纲回流宽裕度，{@code 0 <= x <= 1}
     * @return 无量纲板数裕量，{@code 0 <= y <= 1}
     */
    public double yAtX(double x) {
        if (!Double.isFinite(x) || x < 0.0 || x > 1.0) {
            throw new ServiceException(ErrorCode.INVALID_INPUT,
                    "Gilliland 横坐标 X 必须落在闭区间 [0,1] 内，实际 X=" + x);
        }
        double y = -Math.expm1(logOneMinusY(x));
        // 数值上钳回 [0,1]：端点附近指数运算可能给出 -0.0 或极微小越界
        if (y < 0.0) {
            return 0.0;
        }
        if (y > 1.0) {
            return 1.0;
        }
        return y;
    }

    /**
     * 反查：由纵坐标 Y 反求唯一横坐标 X（Y 随 X 严格单调，反解唯一）。
     *
     * @param y 无量纲板数裕量，{@code 0 <= y < 1}；{@code y=1} 对应 X=0、
     *          即无有限板数够用，不接受该端点
     * @return 无量纲回流宽裕度，{@code 0 < x <= 1}
     */
    public double xAtY(double y) {
        if (!Double.isFinite(y) || y < 0.0 || y >= 1.0) {
            throw new ServiceException(ErrorCode.INVALID_INPUT,
                    "Gilliland 纵坐标 Y 必须落在半开区间 [0,1) 内，实际 Y=" + y);
        }
        if (y == 0.0) {
            return 1.0;
        }

        // 在对数空间求解 h(X) = ln(1-Y)；h 在 (0,1) 上由 -无穷严格递增到 0
        double target = Math.log1p(-y);

        // 先把单调括号 [lo, hi] 支起来：h(lo) < target < h(hi)=0
        double hi = 1.0;
        double lo = initialGuess(target);
        while (logOneMinusY(lo) >= target) {
            lo *= 0.25;
            if (!Double.isFinite(lo) || lo == 0.0) {
                throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                        "Gilliland 反查无法在双精度内支起根括号（Y 过分接近 1，"
                                + "对应板数已超过双精度可表达的有限值）");
            }
        }

        double x = initialGuess(target);
        for (int i = 0; i < MAX_INVERSE_ITERATIONS; i++) {
            double residual = logOneMinusY(x) - target;
            if (Math.abs(residual) <= ROOT_TOLERANCE * Math.max(1.0, Math.abs(target))) {
                return x;
            }
            double next = x - residual / derivativeLogOneMinusY(x);
            // 单调性保护：牛顿步跨出括号就退一步二分，括号每步必然收紧
            if (!(next > lo && next < hi)) {
                next = 0.5 * (lo + hi);
            }
            if (logOneMinusY(next) < target) {
                lo = next;
            } else {
                hi = next;
            }
            x = next;
        }
        throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                "Gilliland 反查牛顿迭代未在 " + MAX_INVERSE_ITERATIONS + " 步内收敛");
    }

    /**
     * {@code h(X) = A(X) * (X-1)/sqrt(X) = ln(1-Y)}。
     * X→0 时 h→-无穷（Y→1），X=1 时 h=0（Y=0）。
     */
    private static double logOneMinusY(double x) {
        double a = (1.0 + C1 * x) / (C2 + C3 * x);
        double u = (x - 1.0) / Math.sqrt(x);
        return a * u;
    }

    /**
     * h(X) 的解析导数 {@code A'(X)u(X) + A(X)u'(X)}，全程为正（h 严格递增）。
     */
    private static double derivativeLogOneMinusY(double x) {
        double denom = C2 + C3 * x;
        double a = (1.0 + C1 * x) / denom;
        double aPrime = (C1 * C2 - C3) / (denom * denom);
        double u = (x - 1.0) / Math.sqrt(x);
        double uPrime = (x + 1.0) / (2.0 * x * Math.sqrt(x));
        return aPrime * u + a * uPrime;
    }

    /**
     * 由 X→0 的渐近形式 {@code h(X) ≈ -11/sqrt(X)} 给出的初值：
     * {@code X0 ≈ 121/t²}，对 Y 接近 1 的陡区同样好用；常规区截到 0.25。
     */
    private static double initialGuess(double target) {
        return Math.min(0.25, (C2 * C2) / (target * target));
    }
}
