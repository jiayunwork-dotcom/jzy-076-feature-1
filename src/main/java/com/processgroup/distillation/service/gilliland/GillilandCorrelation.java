package com.processgroup.distillation.service.gilliland;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.springframework.stereotype.Component;

/**
 * Gilliland 关联 —— 全服务唯一定义处。
 *
 * <p>把「回流余裕」与「板数余裕」两个无量纲量绑在同一条经验曲线上：
 * <pre>
 *   X = (R − Rmin)/(R + 1)   横坐标：实际回流比比最小回流比宽裕多少
 *   Y = (N − Nmin)/(N + 1)   纵坐标：实际板数比最少板数多用了几成
 * </pre>
 *
 * <p>关系式采用 Molokanov 等人对 Gilliland 原始图线的解析拟合：
 * <pre>
 *   Y = 1 − exp[ (1 + 54.4·X)/(11 + 117.2·X) · (X − 1)/√X ]
 * </pre>
 * 选它而不用更粗糙的 Eduljee 直线式，是因为它把两个渐近端点精确钉死：
 * X→0（R→Rmin）时 Y→1（板数发散，塔要做无穷高）；X→1（R→∞）时 Y→0（板数贴到 Nmin）。
 * 整条曲线在 [0,1] 上严格单调下降。
 *
 * <p>正读 {@link #yOf(double)}、反查 {@link #xOf(double)} 全服务只此一份，
 * 「给 R 求 N」「给 N 求 R」两条路径都引用它，不允许别处再抄一份关系式。
 * 反查利用单调性做带括号的 Illinois 试位求根：单调保证括号内恰有一根且括号永不丢根，
 * 插值又比一味二分收敛快——快且稳。
 */
@Component
public class GillilandCorrelation {

    private static final int MAX_INVERSE_ITERATIONS = 200;
    /** 反查终止判据：横坐标括号宽度。曲线在 X→0 端指数级平坦，按残差判停不可靠，只认括号宽度。 */
    private static final double X_BRACKET_TOLERANCE = 1.0e-14;

    /**
     * 正读：由横坐标 X 求纵坐标 Y。定义域 [0,1]，端点按渐近极限钉死。
     *
     * @param x 回流余裕 X=(R−Rmin)/(R+1)
     * @return 板数余裕 Y=(N−Nmin)/(N+1)
     */
    public double yOf(double x) {
        if (x <= 0.0) {
            return 1.0;   // R → Rmin：板数发散
        }
        if (x >= 1.0) {
            return 0.0;   // R → ∞：板数贴到 Nmin
        }
        return 1.0 - Math.exp(exponent(x));
    }

    /**
     * 1−Y 的数值稳健形式：与 {@link #yOf} 是同一条关系式，只是按指数形式求值。
     * X 很小时 Y 指数级贴向 1，直接算 {@code 1 − yOf(x)} 会相消丢光有效位
     * （Y 在双精度下饱和为 1.0 时，对应的有限板数仍可能完全可表示），
     * 需要 1−Y 的调用方在相消保护区应改用本方法。
     */
    public double oneMinusYOf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        if (x >= 1.0) {
            return 1.0;
        }
        return Math.exp(exponent(x));
    }

    /**
     * Molokanov 指数项：(1 + 54.4·X)/(11 + 117.2·X) · (X − 1)/√X，在 (0,1) 上为负。
     * 关系式只定义于这一处，yOf / oneMinusYOf 是它的两种数值视图。
     */
    private double exponent(double x) {
        double coefficient = (1.0 + 54.4 * x) / (11.0 + 117.2 * x);
        return coefficient * (x - 1.0) / Math.sqrt(x);
    }

    /**
     * 反查：由纵坐标 Y 求横坐标 X。Y 随 X 严格单调下降，故 (0,1) 内恰有一解。
     *
     * <p>Illinois 试位法：始终保住异号括号（单调性给的保证），插值步超线性收敛；
     * 同一端点连续被保留时将其函数值减半，打破试位法的单边停滞。
     *
     * @param y 板数余裕 Y=(N−Nmin)/(N+1)，必须落在开区间 (0,1)
     * @return 对应的回流余裕 X
     */
    public double xOf(double y) {
        if (!(y > 0.0 && y < 1.0)) {
            throw new IllegalArgumentException("Gilliland 纵坐标 Y 必须落在开区间 (0,1)，当前 y=" + y);
        }
        // g(x) = yOf(x) − y 在 [0,1] 严格单调下降：g(0) = 1−y > 0，g(1) = −y < 0
        double lo = 0.0;
        double hi = 1.0;
        double gLo = 1.0 - y;
        double gHi = -y;
        int replacedSide = 0;   // 上一次被替换的端点：-1=hi，+1=lo，0=尚无
        for (int i = 0; i < MAX_INVERSE_ITERATIONS; i++) {
            double x = lo - gLo * (hi - lo) / (gHi - gLo);
            if (!(x > lo && x < hi)) {
                x = 0.5 * (lo + hi);   // 数值保险：插值点因舍入掉出括号则退为中点
            }
            double gx = yOf(x) - y;
            if (gx == 0.0 || hi - lo <= X_BRACKET_TOLERANCE) {
                return x;
            }
            if (gx < 0.0) {
                hi = x;                // 根在 [lo, x]，换掉 hi，lo 被保留
                gHi = gx;
                if (replacedSide < 0) {
                    gLo *= 0.5;        // lo 连续被保留：减半其函数值（Illinois 修正）
                }
                replacedSide = -1;
            } else {
                lo = x;                // 根在 [x, hi]，换掉 lo，hi 被保留
                gLo = gx;
                if (replacedSide > 0) {
                    gHi *= 0.5;
                }
                replacedSide = 1;
            }
        }
        throw new ServiceException(ErrorCode.INVERSION_NOT_CONVERGING,
                "Gilliland 关联反查在 " + MAX_INVERSE_ITERATIONS + " 次迭代内未收敛");
    }
}
