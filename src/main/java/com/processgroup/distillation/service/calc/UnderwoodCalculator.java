package com.processgroup.distillation.service.calc;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.springframework.stereotype.Component;

/**
 * Underwood 模块：最小回流比 Rmin（恒相对挥发度二元体系）。
 *
 * <p>第一步，求满足下式的根 theta（{@code 1 < theta < alpha}）：
 * <pre>
 *   -alpha*zF/(alpha - theta) - (1-zF)/(1 - theta) = 1 - q
 *   （等价写法：alpha*zF/(theta - alpha) + (1-zF)/(theta - 1) = 1 - q）
 * </pre>
 * 左端在区间内由 +无穷严格单调降到 -无穷，唯一根，二分求解。
 *
 * <p>第二步：
 * <pre>
 *   Rmin + 1 = alpha*xD/(alpha - theta) + (1-xD)/(1 - theta)
 * </pre>
 */
@Component
public class UnderwoodCalculator {

    private static final int MAX_BISECTION_ITERATIONS = 200;
    private static final double BRACKET_EPS = 1.0e-12;
    private static final double ROOT_TOLERANCE = 1.0e-13;

    public double minimumRefluxRatio(double feedComposition, double distillateComposition,
                                     double alpha, double q) {
        double theta = solveTheta(feedComposition, alpha, q);

        double rminPlusOne = alpha * distillateComposition / (alpha - theta)
                + (1.0 - distillateComposition) / (1.0 - theta);
        return rminPlusOne - 1.0;
    }

    /**
     * 二分求 Underwood 第一式在 (1, alpha) 内的唯一根。
     */
    private double solveTheta(double zF, double alpha, double q) {
        double lo = 1.0 + BRACKET_EPS;
        double hi = alpha - BRACKET_EPS;
        if (!(hi > lo)) {
            throw new ServiceException(ErrorCode.SEPARATION_IMPOSSIBLE,
                    "相对挥发度过分接近 1，无法求解 Underwood 根");
        }
        double target = 1.0 - q;

        // g(t) = -alpha*zF/(alpha-t) - (1-zF)/(1-t) 在 (1,alpha) 严格递减：g(lo)>0，g(hi)<0
        double gLo = firstEquation(zF, alpha, lo) - target;
        double gHi = firstEquation(zF, alpha, hi) - target;
        if (!(gLo > 0.0 && gHi < 0.0)) {
            throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                    "Underwood 方程根未落在预期区间 (1, alpha) 内");
        }

        for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
            double mid = 0.5 * (lo + hi);
            double gMid = firstEquation(zF, alpha, mid) - target;
            if (gMid > 0.0) {
                lo = mid;
            } else {
                hi = mid;
            }
            if (hi - lo < ROOT_TOLERANCE) {
                return 0.5 * (lo + hi);
            }
        }
        throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                "Underwood 方程二分求解未收敛");
    }

    /**
     * Underwood 第一式左端（(alpha-theta) 分母形式，整体带负号）。
     */
    private double firstEquation(double zF, double alpha, double theta) {
        // 重关键组分挥发度取 1
        return -alpha * zF / (alpha - theta) - (1.0 - zF) / (1.0 - theta);
    }
}
