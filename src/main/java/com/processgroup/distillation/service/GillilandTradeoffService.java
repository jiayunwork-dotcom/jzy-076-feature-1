package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.GillilandPointResponse;
import com.processgroup.distillation.domain.GillilandRefluxRequest;
import com.processgroup.distillation.domain.GillilandStagesRequest;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.gilliland.GillilandCorrelation;
import com.processgroup.distillation.service.validation.InputValidator;
import org.springframework.stereotype.Service;

/**
 * Gilliland 权衡曲线编排：在 Fenske 的 Nmin 与 Underwood 的 Rmin 之间，
 * 沿全服务唯一一份 {@link GillilandCorrelation} 正读/反查。
 *
 * <ol>
 *   <li>入参校验与工艺可行性（alpha &gt; 1）复用现有判据，不重复造轮子；</li>
 *   <li>Nmin 直接调用 {@link FenskeCalculator}，Rmin 直接调用 {@link UnderwoodCalculator}，
 *       本服务不重推任何最小极限公式；</li>
 *   <li>正读路径：R → X → Y → N；反查路径：N → Y → X → R，
 *       两条路径共用同一条关联式，同一设计点正反必然闭合。</li>
 * </ol>
 *
 * <p>坐标互化（由 X/Y 定义直接代数反解）：
 * <pre>
 *   X = (R-Rmin)/(R+1)      ⇔  R = (X+Rmin)/(1-X)
 *   Y = (N-Nmin)/(N+1)      ⇔  N = (Y+Nmin)/(1-Y)
 * </pre>
 */
@Service
public class GillilandTradeoffService {

    /** R 必须严格大于 Rmin 的判定容差，与简捷法服务保持同一把尺。 */
    private static final double R_REL_TOLERANCE = 1.0e-9;
    private static final double R_ABS_TOLERANCE = 1.0e-12;
    /** N 必须严格大于 Nmin 的判定容差（相对容差叠加绝对容差）。 */
    private static final double N_REL_TOLERANCE = 1.0e-9;
    private static final double N_ABS_TOLERANCE = 1.0e-9;

    private final InputValidator validator;
    private final FenskeCalculator fenskeCalculator;
    private final UnderwoodCalculator underwoodCalculator;
    private final GillilandCorrelation gilliland;

    public GillilandTradeoffService(InputValidator validator,
                                    FenskeCalculator fenskeCalculator,
                                    UnderwoodCalculator underwoodCalculator,
                                    GillilandCorrelation gilliland) {
        this.validator = validator;
        this.fenskeCalculator = fenskeCalculator;
        this.underwoodCalculator = underwoodCalculator;
        this.gilliland = gilliland;
    }

    /**
     * 路径一：给定实际回流比 R，沿曲线读出所需理论板数 N。
     */
    public GillilandPointResponse stagesForReflux(GillilandRefluxRequest request) {
        validator.validate(request);

        double zF = request.feedComposition();
        double xD = request.distillateComposition();
        double xB = request.bottomsComposition();
        double q = request.feedThermalFactor();
        double alpha = request.relativeVolatility();
        double r = request.refluxRatio();

        Limits limits = resolveLimits(zF, xD, xB, q, alpha);

        double rTolerance = Math.max(R_ABS_TOLERANCE, R_REL_TOLERANCE * Math.max(1.0, limits.rmin()));
        if (r <= limits.rmin() + rTolerance) {
            // X 落到 0 或为负：权衡曲线上不存在有限板数的点，点破而不是硬给一个数
            throw new ServiceException(ErrorCode.REFLUX_INSUFFICIENT, String.format(
                    "回流不足以支撑任何有限塔高：实际回流比 R=%.9g 未大于最小回流比 Rmin=%.9g"
                            + "（X=(R-Rmin)/(R+1) 必须为正，容差 %.2e）",
                    r, limits.rmin(), rTolerance));
        }

        double x = (r - limits.rmin()) / (r + 1.0);
        double y = gilliland.yAtX(x);
        double n = toStages(y, limits.nmin());
        return new GillilandPointResponse(limits.rmin(), limits.nmin(), r, n, x, y);
    }

    /**
     * 路径二：给定工程上愿意接受的理论板数 N，沿曲线反求所需实际回流比 R。
     */
    public GillilandPointResponse refluxForStages(GillilandStagesRequest request) {
        validator.validate(request);

        double zF = request.feedComposition();
        double xD = request.distillateComposition();
        double xB = request.bottomsComposition();
        double q = request.feedThermalFactor();
        double alpha = request.relativeVolatility();
        double n = request.targetStages();

        Limits limits = resolveLimits(zF, xD, xB, q, alpha);

        double nTolerance = Math.max(N_ABS_TOLERANCE, N_REL_TOLERANCE * Math.max(1.0, limits.nmin()));
        if (n <= limits.nmin() + nTolerance) {
            // Y <= 0：比全回流理论下限还少的板数，任何回流比都做不到
            throw new ServiceException(ErrorCode.STAGES_BELOW_MINIMUM, String.format(
                    "目标理论板数 N=%.9g 不高于最少理论板 Nmin=%.9g（容差 %.2e）："
                            + "全回流极限下也至少需要 Nmin 块板，物理上无法实现",
                    n, limits.nmin(), nTolerance));
        }

        double y = (n - limits.nmin()) / (n + 1.0);
        double x = gilliland.xAtY(y);
        double r = toReflux(x, limits.rmin());
        return new GillilandPointResponse(limits.rmin(), limits.nmin(), r, n, x, y);
    }

    /**
     * 取两个渐近端点：Nmin 走现成 Fenske 逻辑，Rmin 走现成 Underwood 逻辑。
     */
    private Limits resolveLimits(double zF, double xD, double xB, double q, double alpha) {
        if (alpha <= 1.0) {
            throw new ServiceException(ErrorCode.SEPARATION_IMPOSSIBLE,
                    "无法分离：相对挥发度 alpha=" + alpha + " 小于等于 1");
        }
        EquilibriumRelation equilibrium = new EquilibriumRelation(alpha);
        double nMin = fenskeCalculator.minimumStages(equilibrium, xD, xB);
        double rMin = underwoodCalculator.minimumRefluxRatio(zF, xD, alpha, q);
        return new Limits(rMin, nMin);
    }

    /** Y、Nmin ⇒ N = (Y+Nmin)/(1-Y)；Y=1 即 N 无界（X=0 渐近端点，正常不会走到）。 */
    private static double toStages(double y, double nMin) {
        double oneMinusY = 1.0 - y;
        if (oneMinusY <= 0.0) {
            throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                    "Gilliland 纵坐标在双精度内已达 1，对应理论板数超过可表达的有限值"
                            + "（实际回流比与最小回流比过分接近）");
        }
        return (y + nMin) / oneMinusY;
    }

    /** X、Rmin ⇒ R = (X+Rmin)/(1-X)，X∈(0,1) 时分母恒正。 */
    private static double toReflux(double x, double rMin) {
        return (x + rMin) / (1.0 - x);
    }

    /** 同一分离任务的两个渐近端点。 */
    private record Limits(double rmin, double nmin) {
    }
}
