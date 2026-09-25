package com.processgroup.distillation.service.gilliland;

import com.processgroup.distillation.domain.GillilandDesignPoint;
import com.processgroup.distillation.domain.RefluxToStagesRequest;
import com.processgroup.distillation.domain.StagesToRefluxRequest;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.validation.InputValidator;
import org.springframework.stereotype.Service;

/**
 * Gilliland 设计点服务：在「回流比 ↔ 理论板数」权衡曲线上双向定点。
 *
 * <p>两条路径共用同一条 {@link GillilandCorrelation} 与同一份 Nmin/Rmin 锚点
 * （锚点直接调用现有 Fenske / Underwood 逻辑，不为本功能重推公式）：
 * <pre>
 *   给 R 求 N：R → X=(R−Rmin)/(R+1) → Y=f(X)  → N=(Y+Nmin)/(1−Y)
 *   给 N 求 R：N → Y=(N−Nmin)/(N+1) → X=f⁻¹(Y) → R=(Rmin+X)/(1−X)
 * </pre>
 * 两个反解公式分别是两个无量纲定义式的恒等变形，不引入第二套近似，
 * 因此同一设计点沿两条路径正反往返必然闭合。
 */
@Service
public class GillilandDesignService {

    /** 边界判定容差（相对叠加绝对），与简捷法服务判定 R≤Rmin 的口径一致。 */
    private static final double REL_TOLERANCE = 1.0e-9;
    private static final double ABS_TOLERANCE = 1.0e-12;
    /** 1−Y 直接相减的精度保护阈值：Y 超过它即改用关系式的指数形式求 1−Y。 */
    private static final double GAP_DIRECT_THRESHOLD = 0.999999;

    private final InputValidator validator;
    private final FenskeCalculator fenskeCalculator;
    private final UnderwoodCalculator underwoodCalculator;
    private final GillilandCorrelation gilliland;

    public GillilandDesignService(InputValidator validator,
                                  FenskeCalculator fenskeCalculator,
                                  UnderwoodCalculator underwoodCalculator,
                                  GillilandCorrelation gilliland) {
        this.validator = validator;
        this.fenskeCalculator = fenskeCalculator;
        this.underwoodCalculator = underwoodCalculator;
        this.gilliland = gilliland;
    }

    /**
     * 路径一：给定实际回流比 R，沿 Gilliland 关联正读出所需理论板数 N。
     *
     * @throws ServiceException {@code REFLUX_INSUFFICIENT} 若 R ≤ Rmin：
     *         X 落到 0 或为负，曲线上不存在对应的有限板数
     */
    public GillilandDesignPoint stagesForReflux(RefluxToStagesRequest request) {
        validator.validate(request);
        Anchors anchors = anchors(request.feedComposition(), request.distillateComposition(),
                request.bottomsComposition(), request.feedThermalFactor(), request.relativeVolatility());

        double r = request.refluxRatio();
        double rTolerance = Math.max(ABS_TOLERANCE, REL_TOLERANCE * Math.max(1.0, anchors.minimumReflux()));
        if (r <= anchors.minimumReflux() + rTolerance) {
            throw new ServiceException(ErrorCode.REFLUX_INSUFFICIENT, String.format(
                    "回流不足以支撑任何有限塔高：实际回流比 R=%.9g 未大于最小回流比 Rmin=%.9g，"
                            + "横坐标 X=(R−Rmin)/(R+1) 落到 0 或为负，权衡曲线上不存在对应有限板数（容差 %.2e）",
                    r, anchors.minimumReflux(), rTolerance));
        }

        double x = (r - anchors.minimumReflux()) / (r + 1.0);
        double y = gilliland.yOf(x);
        // Y 贴 1 时 1−Y 相消丢精度，改取同一关系式的指数形式；N=(Y+Nmin)/(1−Y)
        double gap = y <= GAP_DIRECT_THRESHOLD ? 1.0 - y : gilliland.oneMinusYOf(x);
        double n = gap > 0.0 ? (y + anchors.minimumStages()) / gap : Double.NaN;
        if (!Double.isFinite(n)) {
            // R 距 Rmin 近到对应板数超出双精度可表示范围；按“需要无穷高塔”点破，不凑数
            throw new ServiceException(ErrorCode.REFLUX_INSUFFICIENT, String.format(
                    "回流不足以支撑任何可表示的有限塔高：R=%.9g 距 Rmin=%.9g 过近，"
                            + "权衡曲线对应板数已超出双精度表示范围（工程上视为需要无穷高塔）",
                    r, anchors.minimumReflux()));
        }
        return new GillilandDesignPoint(anchors.minimumReflux(), anchors.minimumStages(), r, n);
    }

    /**
     * 路径二：给定目标理论板数 N，在同一条 Gilliland 关联上反查所需实际回流比 R。
     *
     * @throws ServiceException {@code STAGES_BELOW_MINIMUM} 若 N ≤ Nmin：低于理论下限，物理上做不到
     */
    public GillilandDesignPoint refluxForStages(StagesToRefluxRequest request) {
        validator.validate(request);
        Anchors anchors = anchors(request.feedComposition(), request.distillateComposition(),
                request.bottomsComposition(), request.feedThermalFactor(), request.relativeVolatility());

        double n = request.targetStages();
        double nTolerance = Math.max(ABS_TOLERANCE, REL_TOLERANCE * Math.max(1.0, anchors.minimumStages()));
        if (n <= anchors.minimumStages() + nTolerance) {
            throw new ServiceException(ErrorCode.STAGES_BELOW_MINIMUM, String.format(
                    "板数不可达：目标理论板数 N=%.9g 未大于最少理论板数 Nmin=%.9g，"
                            + "比理论下限还少的塔物理上做不到（容差 %.2e）",
                    n, anchors.minimumStages(), nTolerance));
        }

        // Y=(N−Nmin)/(N+1)；写成 1−(1+Nmin)/(N+1) 避免 N 很大时分子分母相消丢精度
        double y = 1.0 - (1.0 + anchors.minimumStages()) / (n + 1.0);
        // N 大到 (1+Nmin)/(N+1) 低于双精度分辨率时 y 饱和为 1.0；此时对应回流比在机器精度内
        // 与 Rmin 不可区分，钳到开区间上界之内，反查照常给出该精度下的极限设计点
        y = Math.min(y, Math.nextAfter(1.0, 0.0));
        double x = gilliland.xOf(y);
        // X=(R−Rmin)/(R+1) 对 R 的恒等反解
        double r = (anchors.minimumReflux() + x) / (1.0 - x);
        return new GillilandDesignPoint(anchors.minimumReflux(), anchors.minimumStages(), r, n);
    }

    /**
     * 两个渐近端点锚点：直接调用现有 Fenske / Underwood 逻辑。
     * 工艺可行性（alpha ≤ 1 无法分离）沿用服务层既有判据。
     */
    private Anchors anchors(double zF, double xD, double xB, double q, double alpha) {
        if (alpha <= 1.0) {
            throw new ServiceException(ErrorCode.SEPARATION_IMPOSSIBLE,
                    "无法分离：相对挥发度 alpha=" + alpha + " 小于等于 1");
        }
        EquilibriumRelation equilibrium = new EquilibriumRelation(alpha);
        double minimumStages = fenskeCalculator.minimumStages(equilibrium, xD, xB);
        double minimumReflux = underwoodCalculator.minimumRefluxRatio(zF, xD, alpha, q);
        return new Anchors(minimumReflux, minimumStages);
    }

    /** 权衡曲线的两个渐近锚点。 */
    private record Anchors(double minimumReflux, double minimumStages) {
    }
}
