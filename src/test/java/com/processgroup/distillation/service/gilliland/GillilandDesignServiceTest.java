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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gilliland 设计点服务验收判据：
 * <ol>
 *   <li>给 R 求 N 再反求 R、给 N 求 R 再反求 N，两条路径正反闭合；</li>
 *   <li>R 压向 Rmin 时板数急剧发散，R 宽裕时板数贴到 Nmin；</li>
 *   <li>增大回流比，所需板数严格单调下降；</li>
 *   <li>R≤Rmin、N≤Nmin、底层参数越界分别落到对应结构化失败；</li>
 *   <li>锚点与现有 Fenske / Underwood 逻辑逐位一致（不重推公式）。</li>
 * </ol>
 */
class GillilandDesignServiceTest {

    private final FenskeCalculator fenske = new FenskeCalculator();
    private final UnderwoodCalculator underwood = new UnderwoodCalculator();
    private final GillilandDesignService service = new GillilandDesignService(
            new InputValidator(), fenske, underwood, new GillilandCorrelation());

    /** 与简捷法测试相同的五组分离任务（覆盖不同 alpha / q）。 */
    static Stream<Arguments> separationTasks() {
        return Stream.of(
                Arguments.of(0.50, 0.95, 0.05, 2.5, 1.0),
                Arguments.of(0.40, 0.90, 0.10, 2.0, 1.0),
                Arguments.of(0.50, 0.95, 0.05, 2.5, 0.0),
                Arguments.of(0.30, 0.80, 0.05, 3.0, 0.5),
                Arguments.of(0.45, 0.90, 0.10, 2.0, 1.5)
        );
    }

    private double rmin(double zF, double xD, double alpha, double q) {
        return underwood.minimumRefluxRatio(zF, xD, alpha, q);
    }

    private double nmin(double xD, double xB, double alpha) {
        return fenske.minimumStages(new EquilibriumRelation(alpha), xD, xB);
    }

    private GillilandDesignPoint byReflux(double zF, double xD, double xB,
                                          double alpha, double q, double r) {
        return service.stagesForReflux(new RefluxToStagesRequest(zF, xD, xB, q, alpha, r));
    }

    private GillilandDesignPoint byStages(double zF, double xD, double xB,
                                          double alpha, double q, double n) {
        return service.refluxForStages(new StagesToRefluxRequest(zF, xD, xB, q, alpha, n));
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxToStagesAndBackClosesTheLoop(double zF, double xD, double xB,
                                            double alpha, double q) {
        double rMin = rmin(zF, xD, alpha, q);
        for (double m : new double[]{1.01, 1.1, 1.5, 3.0, 20.0}) {
            double r = rMin * m;
            double n = byReflux(zF, xD, xB, alpha, q, r).theoreticalStages();
            double rBack = byStages(zF, xD, xB, alpha, q, n).refluxRatio();
            assertThat(rBack)
                    .as("R=%.9g → N=%.9g → R'=%.9g 应闭合回原回流比", r, n, rBack)
                    .isCloseTo(r, within(Math.max(1.0e-6, 1.0e-6 * r)));
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void stagesToRefluxAndBackClosesTheLoop(double zF, double xD, double xB,
                                            double alpha, double q) {
        double nMin = nmin(xD, xB, alpha);
        for (double m : new double[]{1.2, 2.0, 5.0, 50.0}) {
            double n = nMin * m;
            double r = byStages(zF, xD, xB, alpha, q, n).refluxRatio();
            double nBack = byReflux(zF, xD, xB, alpha, q, r).theoreticalStages();
            assertThat(nBack)
                    .as("N=%.9g → R=%.9g → N'=%.9g 应闭合回原板数", n, r, nBack)
                    .isCloseTo(n, within(Math.max(1.0e-6, 1.0e-6 * n)));
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxNearMinimumRequiresHugeStageCount(double zF, double xD, double xB,
                                                 double alpha, double q) {
        double rMin = rmin(zF, xD, alpha, q);
        double nMin = nmin(xD, xB, alpha);

        double nClose = byReflux(zF, xD, xB, alpha, q, rMin * 1.001).theoreticalStages();
        double nVeryClose = byReflux(zF, xD, xB, alpha, q, rMin * (1.0 + 1.0e-6)).theoreticalStages();

        // X → 0 时 Y → 1：板数急剧上升、趋于很大，远超 Fenske 下界
        assertThat(nClose).isGreaterThan(10.0 * nMin);
        assertThat(nVeryClose).isGreaterThan(1.0e6 * nMin);
        assertThat(nVeryClose).isGreaterThan(nClose);
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void generousRefluxApproachesFenskeMinimum(double zF, double xD, double xB,
                                               double alpha, double q) {
        double nMin = nmin(xD, xB, alpha);

        double n = byReflux(zF, xD, xB, alpha, q, 1.0e6).theoreticalStages();

        // X → 1 时 Y → 0：板数逼近最少理论板，且绝不跌破它
        assertThat(n).isGreaterThanOrEqualTo(nMin);
        assertThat(n - nMin).isLessThan(1.0e-3);
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void increasingRefluxStrictlyDecreasesRequiredStages(double zF, double xD, double xB,
                                                         double alpha, double q) {
        double rMin = rmin(zF, xD, alpha, q);
        double previous = Double.POSITIVE_INFINITY;
        for (double m : new double[]{1.02, 1.1, 1.5, 2.5, 5.0, 12.0, 60.0}) {
            double r = rMin * m;
            double n = byReflux(zF, xD, xB, alpha, q, r).theoreticalStages();
            assertThat(n)
                    .as("回流换板数：R=%.9g 的板数 %.9g 应严格小于上一回流比的 %.9g", r, n, previous)
                    .isLessThan(previous);
            previous = n;
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxAtOrBelowMinimumIsRejected(double zF, double xD, double xB,
                                          double alpha, double q) {
        double rMin = rmin(zF, xD, alpha, q);
        for (double r : new double[]{rMin, rMin * 0.999, rMin * 0.5, 0.0}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> byReflux(zF, xD, xB, alpha, q, r));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.REFLUX_INSUFFICIENT);
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void stagesAtOrBelowFenskeMinimumAreRejected(double zF, double xD, double xB,
                                                 double alpha, double q) {
        double nMin = nmin(xD, xB, alpha);
        for (double n : new double[]{nMin, nMin * 0.9, Math.floor(nMin), 1.0}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> byStages(zF, xD, xB, alpha, q, n));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.STAGES_BELOW_MINIMUM);
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void anchorsComeFromExistingFenskeAndUnderwood(double zF, double xD, double xB,
                                                   double alpha, double q) {
        // 锚点必须与现有 Fenske / Underwood 逻辑逐位一致：本功能不重推这两个公式
        GillilandDesignPoint point = byReflux(zF, xD, xB, alpha, q,
                rmin(zF, xD, alpha, q) * 1.8);
        assertThat(point.minimumRefluxRatio()).isEqualTo(rmin(zF, xD, alpha, q));
        assertThat(point.minimumStages()).isEqualTo(nmin(xD, xB, alpha));
    }

    @Test
    void invalidUnderlyingParametersFollowExistingValidation() {
        // 组成越界
        ServiceException ex = assertThrows(ServiceException.class,
                () -> byReflux(1.5, 0.95, 0.05, 2.5, 1.0, 5.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        // 组成顺序颠倒
        ex = assertThrows(ServiceException.class,
                () -> byStages(0.9, 0.5, 0.1, 2.5, 1.0, 10.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        // 相对挥发度非正
        ex = assertThrows(ServiceException.class,
                () -> byReflux(0.5, 0.95, 0.05, 0.0, 1.0, 5.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        // alpha ≤ 1：沿用现有「无法分离」判据
        ex = assertThrows(ServiceException.class,
                () -> byReflux(0.5, 0.95, 0.05, 1.0, 1.0, 5.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.SEPARATION_IMPOSSIBLE);
        // 负回流比、非正目标板数：参数非法
        ex = assertThrows(ServiceException.class,
                () -> byReflux(0.5, 0.95, 0.05, 2.5, 1.0, -1.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        ex = assertThrows(ServiceException.class,
                () -> byStages(0.5, 0.95, 0.05, 2.5, 1.0, 0.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        // 缺字段
        ex = assertThrows(ServiceException.class,
                () -> service.stagesForReflux(new RefluxToStagesRequest(0.5, null, 0.05, 1.0, 2.5, 5.0)));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
