package com.processgroup.distillation.service.stepping;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.material.MaterialBalance;
import org.springframework.stereotype.Component;

/**
 * 操作线工厂：精馏段、提馏段两条操作线统一由物料衡算与进料热状态 q 推出。
 *
 * <p>两段操作线都只承载物料衡算关系；相平衡关系不在此重复定义，
 * 统一引用传入的同一个 {@link EquilibriumRelation}。
 *
 * <h3>精馏段操作线</h3>
 * <pre>
 *   L = R*D,  V = (R+1)*D
 *   y = (L/V) x + D*xD/V = R/(R+1) x + xD/(R+1)
 * </pre>
 *
 * <h3>q 线（两操作线交点轨迹）</h3>
 * <pre>
 *   y = q/(q-1) x - zF/(q-1)      (q != 1)
 *   x = zF                         (q = 1，泡点进料竖直线)
 * </pre>
 *
 * <h3>提馏段操作线（由进料板上下衡算推出）</h3>
 * <pre>
 *   L' = L + q*F,  V' = V - (1-q)*F
 *   V'*y = L'*x - B*xB
 *   y = (L'/V') x - B*xB/V'
 * </pre>
 * 构造时校验：两线交点确实落在 q 线上，且提馏段线过 (xB, xB)。
 */
@Component
public class OperatingLineFactory {

    private static final double INTERSECTION_TOLERANCE = 1.0e-9;

    /**
     * 构造一对共用同一相平衡关系的操作线。
     */
    public OperatingLines create(EquilibriumRelation equilibrium, MaterialBalance balance,
                                 double refluxRatio, double q) {
        double r = refluxRatio;
        double d = balance.distillateFlow();
        double b = balance.bottomsFlow();
        double xD = balance.distillateComposition();
        double xB = balance.bottomsComposition();
        double zF = balance.feedComposition();
        double f = balance.feedFlow();

        // 精馏段
        double rectSlope = r / (r + 1.0);
        double rectIntercept = xD / (r + 1.0);
        OperatingLine rectifying = new OperatingLine(rectSlope, rectIntercept);

        // 提馏段流量
        double liquidRect = r * d;                 // L
        double vaporRect = (r + 1.0) * d;          // V
        double liquidStrip = liquidRect + q * f;   // L'
        double vaporStrip = vaporRect - (1.0 - q) * f; // V'
        if (!(vaporStrip > 0.0)) {
            throw new ServiceException(ErrorCode.STRIPPING_FLOW_INVALID, String.format(
                    "提馏段气相流量 V'=%.6e 非正（R=%.6g, q=%.6g）", vaporStrip, r, q));
        }
        double stripSlope = liquidStrip / vaporStrip;
        double stripIntercept = -b * xB / vaporStrip;
        OperatingLine stripping = new OperatingLine(stripSlope, stripIntercept);

        double[] intersection = intersection(rectifying, stripping, q, zF);
        double xq = intersection[0];
        double yq = intersection[1];
        crossCheck(equilibrium, rectifying, stripping, xB, xq, yq, q, zF);

        return new OperatingLines(rectifying, stripping, xq, yq,
                liquidRect, vaporRect, liquidStrip, vaporStrip);
    }

    /**
     * 求精馏段操作线与提馏段操作线的交点，交点必须在 q 线上。
     * q=1 时 q 线为竖直线 x=zF，直接代入，避免除零。
     */
    private double[] intersection(OperatingLine rect, OperatingLine strip, double q, double zF) {
        double xq;
        if (Math.abs(q - 1.0) < 1.0e-12) {
            xq = zF;
        } else {
            double qSlope = q / (q - 1.0);
            double qIntercept = -zF / (q - 1.0);
            double denom = rect.slope() - qSlope;
            if (Math.abs(denom) < 1.0e-15) {
                throw new ServiceException(ErrorCode.FEED_LINE_PARALLEL,
                        "q 线与精馏段操作线平行，无法确定进料级交点");
            }
            // rect: y = m_r x + b_r ; q-line: y = m_q x + b_q
            xq = (qIntercept - rect.intercept()) / denom;
        }
        double yq = rect.yAt(xq);

        if (!Double.isFinite(xq) || !Double.isFinite(yq)) {
            throw new ServiceException(ErrorCode.FEED_LINE_PARALLEL,
                    "操作线交点为非有限值，无法确定进料级");
        }
        return new double[]{xq, yq};
    }

    /**
     * 交叉验证，保证两段操作线不是“各算各的、对不上”：
     * <ol>
     *   <li>交点同时满足精馏段线、提馏段线与 q 线；</li>
     *   <li>提馏段线过 (xB, xB)；</li>
     *   <li>交点落在两线之上（数值一致性）。</li>
     * </ol>
     */
    private void crossCheck(EquilibriumRelation equilibrium, OperatingLine rect, OperatingLine strip,
                            double xB, double xq, double yq, double q, double zF) {
        double yOnRect = rect.yAt(xq);
        double yOnStrip = strip.yAt(xq);
        double yOnQLine = Math.abs(q - 1.0) < 1.0e-12
                ? yOnRect
                : q / (q - 1.0) * xq - zF / (q - 1.0);
        double yReboiler = strip.yAt(xB);

        double scale = Math.max(1.0, Math.abs(yq));
        double tol = INTERSECTION_TOLERANCE * scale;
        if (Math.abs(yOnRect - yq) > tol
                || Math.abs(yOnStrip - yq) > tol
                || Math.abs(yOnQLine - yq) > tol
                || Math.abs(yReboiler - xB) > tol) {
            throw new ServiceException(ErrorCode.MATERIAL_NOT_CLOSED, String.format(
                    "两段操作线不一致：交点(%.9f,%.9f)，精馏段=%.9f，提馏段=%.9f，q线=%.9f，再沸器点偏差=%.2e",
                    xq, yq, yOnRect, yOnStrip, yOnQLine, Math.abs(yReboiler - xB)));
        }
        // 两段操作线必须绑定同一个平衡关系实例：用它在交点处做一次平衡线健全性检查，
        // 既保留显式引用，也避免别处再出现第二份平衡曲线实现
        double yEquilibriumAtIntersection = equilibrium.vaporInEquilibrium(xq);
        if (!Double.isFinite(yEquilibriumAtIntersection)) {
            throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                    "进料板交点处平衡线计算为非有限值");
        }
    }
}
