package com.processgroup.distillation.service.material;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 物料衡算模块：由总进料量与三股组成反推馏出液、釜液流量。
 *
 * <p>二元体系（轻关键组分 + 重关键组分，摩尔分数）：
 * <pre>
 *   F = D + B
 *   F*zF = D*xD + B*xB
 * </pre>
 * 解得：
 * <pre>
 *   D = F * (zF - xB)/(xD - xB)
 *   B = F - D
 * </pre>
 */
@Component
public class MaterialBalanceCalculator {

    /** 衡算残差容差（按进料量归一的相对容差，再叠加绝对容差防 F 很小时失效）。 */
    public static final double RELATIVE_TOLERANCE = 1.0e-9;
    public static final double ABSOLUTE_TOLERANCE = 1.0e-10;

    /**
     * 反推 D、B 并校验衡算闭合。残差超容差直接抛 {@link ErrorCode#MATERIAL_NOT_CLOSED}。
     */
    public MaterialBalance solve(double feedFlow, double feedComposition,
                                 double distillateComposition, double bottomsComposition) {
        double zF = feedComposition;
        double xD = distillateComposition;
        double xB = bottomsComposition;
        double denom = xD - xB;
        if (!(Math.abs(denom) > 0.0)) {
            throw new ServiceException(ErrorCode.COMPOSITION_ORDER,
                    "馏出液组成与釜液组成相同，无法反推产品流量");
        }

        double distillateFlow = feedFlow * (zF - xB) / denom;
        double bottomsFlow = feedFlow - distillateFlow;

        double totalResidual = Math.abs(feedFlow - distillateFlow - bottomsFlow);
        double componentResidual = Math.abs(
                feedFlow * zF - distillateFlow * xD - bottomsFlow * xB);

        double tolerance = Math.max(ABSOLUTE_TOLERANCE, RELATIVE_TOLERANCE * feedFlow);
        if (totalResidual > tolerance || componentResidual > tolerance) {
            throw new ServiceException(ErrorCode.MATERIAL_NOT_CLOSED, String.format(
                    "衡算残差超容差：总残差=%.3e，轻组分残差=%.3e，容差=%.3e",
                    totalResidual, componentResidual, tolerance));
        }

        return new MaterialBalance(feedFlow, distillateFlow, bottomsFlow,
                zF, xD, xB, totalResidual, componentResidual);
    }
}
