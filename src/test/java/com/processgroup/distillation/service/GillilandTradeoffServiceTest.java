package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.GillilandPointResponse;
import com.processgroup.distillation.domain.GillilandRefluxRequest;
import com.processgroup.distillation.domain.GillilandStagesRequest;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import com.processgroup.distillation.service.gilliland.GillilandCorrelation;
import com.processgroup.distillation.service.validation.InputValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gilliland 权衡曲线服务级验收判据：
 * <ol>
 *   <li>给 R 求 N、再给 N 求 R，正反闭合；反向亦然；两条路径共用同一设计点；</li>
 *   <li>R→Rmin 时 N 急剧发散（Y→1），R 很宽裕时 N→Nmin（Y→0）；</li>
 *   <li>R 单调增大，所需板数严格单调下降（多组分离任务、多组回流比）；</li>
 *   <li>R≤Rmin → REFLUX_INSUFFICIENT；N≤Nmin → STAGES_BELOW_MINIMUM；
 *       底层参数越界沿用现有结构化判据。</li>
 * </ol>
 */
class GillilandTradeoffServiceTest {

    private static final double CLOSE = 1.0e-7;

    private final FenskeCalculator fenske = new FenskeCalculator();
    private final UnderwoodCalculator underwood = new UnderwoodCalculator();
    private final GillilandTradeoffService service = new GillilandTradeoffService(
            new InputValidator(), fenske, underwood, new GillilandCorrelation());

    /** 五组覆盖不同 alpha / q 的分离任务（与简捷法服务测试同一套）。 */
    static Stream<Arguments> separationTasks() {
        return Stream.of(
                Arguments.of(0.50, 0.95, 0.05, 2.5, 1.0),
                Arguments.of(0.40, 0.90, 0.10, 2.0, 1.0),
                Arguments.of(0.50, 0.95, 0.05, 2.5, 0.0),
                Arguments.of(0.30, 0.80, 0.05, 3.0, 0.5),
                Arguments.of(0.45, 0.90, 0.10, 2.0, 1.5)
        );
    }

    // ---------------------------------------------------------------- 闭合

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxToStagesAndBackClosesOnSameCurve(double zF, double xD, double xB,
                                                double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        for (double factor : new double[]{1.2, 1.5, 2.0, 3.0, 5.0, 10.0}) {
            double r = rMin * factor;

            // 正向：R 读出 N
            GillilandPointResponse forward = service.stagesForReflux(refluxRequest(
                    zF, xD, xB, q, alpha, r));

            // 反向：拿这个 N 反求 R，必须回到最初的 R
            GillilandPointResponse back = service.refluxForStages(stagesRequest(
                    zF, xD, xB, q, alpha, forward.theoreticalStages()));

            assertThat(back.refluxRatio())
                    .as("R=%.6f → N=%.6f → R'=%.12f 正反不闭合", r,
                            forward.theoreticalStages(), back.refluxRatio())
                    .isCloseTo(r, within(Math.max(1.0e-10, CLOSE * Math.max(1.0, r))));
            // 两个路径吐出的是同一个曲线点：X、Y、Nmin、Rmin 必须一致
            assertThat(back.x()).isCloseTo(forward.x(), within(1.0e-9));
            assertThat(back.y()).isCloseTo(forward.y(), within(1.0e-9));
            assertThat(back.minimumRefluxRatio()).isCloseTo(forward.minimumRefluxRatio(), within(1.0e-12));
            assertThat(back.minimumStages()).isCloseTo(forward.minimumStages(), within(1.0e-12));
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void stagesToRefluxAndBackClosesOnSameCurve(double zF, double xD, double xB,
                                                double alpha, double q) {
        double nMin = fenske.minimumStages(
                new com.processgroup.distillation.service.equilibrium.EquilibriumRelation(alpha), xD, xB);
        for (double n : new double[]{nMin + 0.5, nMin + 1.0, nMin + 3.0,
                nMin * 1.5, nMin * 2.0, nMin * 5.0}) {

            // 反向：N 反求 R
            GillilandPointResponse reverse = service.refluxForStages(stagesRequest(
                    zF, xD, xB, q, alpha, n));

            // 再正向：拿这个 R 读出 N，必须回到最初的 N
            GillilandPointResponse back = service.stagesForReflux(refluxRequest(
                    zF, xD, xB, q, alpha, reverse.refluxRatio()));

            assertThat(back.theoreticalStages())
                    .as("N=%.6f → R=%.9f → N'=%.12f 正反不闭合", n,
                            reverse.refluxRatio(), back.theoreticalStages())
                    .isCloseTo(n, within(Math.max(1.0e-9, CLOSE * Math.max(1.0, n))));
            assertThat(back.x()).isCloseTo(reverse.x(), within(1.0e-9));
            assertThat(back.y()).isCloseTo(reverse.y(), within(1.0e-9));
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void pointCoordinatesAreConsistentWithDefinitions(double zF, double xD, double xB,
                                                      double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        double nMin = fenske.minimumStages(
                new com.processgroup.distillation.service.equilibrium.EquilibriumRelation(alpha), xD, xB);
        GillilandPointResponse p = service.stagesForReflux(refluxRequest(
                zF, xD, xB, q, alpha, rMin * 2.0));

        assertThat(p.minimumRefluxRatio()).isCloseTo(rMin, within(1.0e-12));
        assertThat(p.minimumStages()).isCloseTo(nMin, within(1.0e-12));
        // X、Y 定义自洽
        assertThat(p.x()).isCloseTo((p.refluxRatio() - rMin) / (p.refluxRatio() + 1.0), within(1.0e-12));
        assertThat(p.y()).isCloseTo((p.theoreticalStages() - nMin) / (p.theoreticalStages() + 1.0),
                within(1.0e-12));
        // 有限设计点必在开曲线内部
        assertThat(p.x()).isGreaterThan(0.0).isLessThan(1.0);
        assertThat(p.y()).isGreaterThan(0.0).isLessThan(1.0);
        assertThat(p.theoreticalStages()).isGreaterThan(nMin);
    }

    // ---------------------------------------------------------------- 端点

    @ParameterizedTest
    @MethodSource("separationTasks")
    void stagesDivergeSharplyAsRefluxApproachesMinimum(double zF, double xD, double xB,
                                                       double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        double nMin = fenske.minimumStages(
                new com.processgroup.distillation.service.equilibrium.EquilibriumRelation(alpha), xD, xB);

        double previous = Double.POSITIVE_INFINITY;
        // 越贴近 Rmin，板数越大，且加速发散
        for (double factor : new double[]{1.02, 1.01, 1.005, 1.001}) {
            GillilandPointResponse p = service.stagesForReflux(refluxRequest(
                    zF, xD, xB, q, alpha, rMin * factor));
            double lowerBound = previous == Double.POSITIVE_INFINITY ? nMin * 1.5 : previous;
            assertThat(p.theoreticalStages())
                    .as("R 距 Rmin 仅 %.3f%% 时板数应继续飙升", (factor - 1.0) * 100)
                    .isGreaterThan(lowerBound);
            previous = p.theoreticalStages();
        }
        // R=1.001×Rmin 时 X 已到 5e-4 量级、Y≈0.98，板数相对 Nmin 放大几十倍以上
        GillilandPointResponse nearPinch = service.stagesForReflux(refluxRequest(
                zF, xD, xB, q, alpha, rMin * 1.001));
        assertThat(nearPinch.y()).isGreaterThan(0.97);
        assertThat(nearPinch.theoreticalStages()).isGreaterThan(nMin * 30.0);
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void stagesApproachMinimumAtVeryGenerousReflux(double zF, double xD, double xB,
                                                   double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        double nMin = fenske.minimumStages(
                new com.processgroup.distillation.service.equilibrium.EquilibriumRelation(alpha), xD, xB);

        GillilandPointResponse generous = service.stagesForReflux(refluxRequest(
                zF, xD, xB, q, alpha, 1.0e9));
        // X→1、Y→0，N 从上方逼近 Nmin
        assertThat(generous.x()).isGreaterThan(0.999999);
        assertThat(generous.y()).isLessThan(1.0e-6);
        assertThat(generous.theoreticalStages())
                .isGreaterThan(nMin)
                .isCloseTo(nMin, within(1.0e-5));

        // 反查侧同端点：N 只比 Nmin 多一点点时，所需回流比已经非常大
        GillilandPointResponse nearMin = service.refluxForStages(stagesRequest(
                zF, xD, xB, q, alpha, nMin + 1.0e-6));
        assertThat(nearMin.refluxRatio()).isGreaterThan(1.0e5 * Math.max(1.0, rMin));
    }

    // ---------------------------------------------------------------- 单调

    @ParameterizedTest
    @MethodSource("separationTasks")
    void increasingRefluxStrictlyDecreasesRequiredStages(double zF, double xD, double xB,
                                                         double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        double[] factors = {1.05, 1.1, 1.2, 1.5, 2.0, 3.0, 5.0, 8.0, 12.0, 50.0, 1.0e3, 1.0e6};

        double previousStages = Double.POSITIVE_INFINITY;
        for (double factor : factors) {
            GillilandPointResponse p = service.stagesForReflux(refluxRequest(
                    zF, xD, xB, q, alpha, rMin * factor));
            assertThat(p.theoreticalStages())
                    .as("R=%.5f（%.3g×Rmin）时 N=%.6f 不应回升，上一值 %.6f",
                            p.refluxRatio(), factor, p.theoreticalStages(), previousStages)
                    .isLessThan(previousStages);
            previousStages = p.theoreticalStages();
        }
    }

    // ---------------------------------------------------------------- 非法工况

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxAtOrBelowMinimumIsRejectedNoFakeStages(double zF, double xD, double xB,
                                                      double alpha, double q) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        for (double badR : new double[]{rMin, rMin * 0.999, rMin * 0.5, 0.0}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.stagesForReflux(refluxRequest(zF, xD, xB, q, alpha, badR)));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.REFLUX_INSUFFICIENT);
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void targetStagesAtOrBelowMinimumIsRejected(double zF, double xD, double xB,
                                                double alpha, double q) {
        double nMin = fenske.minimumStages(
                new com.processgroup.distillation.service.equilibrium.EquilibriumRelation(alpha), xD, xB);
        for (double badN : new double[]{nMin, nMin * 0.999, nMin - 1.0, 1.0}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.refluxForStages(stagesRequest(zF, xD, xB, q, alpha, badN)));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.STAGES_BELOW_MINIMUM);
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void underlyingParameterViolationsUseExistingChecks(double zF, double xD, double xB,
                                                        double alpha, double q) {
        // α ≤ 1：无法分离，两条路径都顺着现有工艺判据拒绝
        ServiceException ex1 = assertThrows(ServiceException.class,
                () -> service.stagesForReflux(refluxRequest(zF, xD, xB, q, 1.0, 5.0)));
        assertThat(ex1.errorCode()).isEqualTo(ErrorCode.SEPARATION_IMPOSSIBLE);
        ServiceException ex2 = assertThrows(ServiceException.class,
                () -> service.refluxForStages(stagesRequest(zF, xD, xB, q, 1.0, 20.0)));
        assertThat(ex2.errorCode()).isEqualTo(ErrorCode.SEPARATION_IMPOSSIBLE);

        // 组成顺序颠倒 / 回流比为负 / 目标板数非正 / 字段缺失：统一 INVALID_INPUT
        assertThat(assertThrows(ServiceException.class, () -> service.stagesForReflux(
                refluxRequest(zF, xB, xD, q, alpha, 5.0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(assertThrows(ServiceException.class, () -> service.stagesForReflux(
                refluxRequest(zF, xD, xB, q, alpha, -1.0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(assertThrows(ServiceException.class, () -> service.refluxForStages(
                stagesRequest(zF, xD, xB, q, alpha, -2.0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(assertThrows(ServiceException.class, () -> service.stagesForReflux(
                new GillilandRefluxRequest(null, xD, xB, q, alpha, 5.0))).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(assertThrows(ServiceException.class, () -> service.refluxForStages(
                new GillilandStagesRequest(zF, xD, xB, q, alpha, null))).errorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // ---------------------------------------------------------------- helpers

    private static GillilandRefluxRequest refluxRequest(double zF, double xD, double xB,
                                                        double q, double alpha, double r) {
        return new GillilandRefluxRequest(zF, xD, xB, q, alpha, r);
    }

    private static GillilandStagesRequest stagesRequest(double zF, double xD, double xB,
                                                        double q, double alpha, double n) {
        return new GillilandStagesRequest(zF, xD, xB, q, alpha, n);
    }
}
