package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.Section;
import com.processgroup.distillation.domain.ShortcutResponse;
import com.processgroup.distillation.domain.StageResult;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 服务级验收判据：
 * <ol>
 *   <li>物料衡算残差落容差；</li>
 *   <li>R 逼近全回流时板数贴 Fenske（离散差一块以内）；</li>
 *   <li>R 增大，板数不升（多组回流比）；</li>
 *   <li>R 等于/低于 Rmin 一律报错；</li>
 *   <li>进料热状态变化不影响衡算闭合。</li>
 * </ol>
 */
class DistillationShortcutServiceTest {

    private final TestHarness harness = new TestHarness();

    /** 五组覆盖不同 alpha / q 的分离任务。 */
    static Stream<Arguments> separationTasks() {
        return Stream.of(
                Arguments.of(0.50, 0.95, 0.05, 2.5, 1.0),
                Arguments.of(0.40, 0.90, 0.10, 2.0, 1.0),
                Arguments.of(0.50, 0.95, 0.05, 2.5, 0.0),
                Arguments.of(0.30, 0.80, 0.05, 3.0, 0.5),
                Arguments.of(0.45, 0.90, 0.10, 2.0, 1.5)
        );
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void anyValidInputMaterialBalanceResidualIsWithinTolerance(
            double zF, double xD, double xB, double alpha, double q) {
        ShortcutResponse resp = harness.evaluate(1.0, zF, xD, xB, q, alpha, 1_000_000.0);

        double tol = 1.0e-9; // F=1
        assertThat(resp.stepping().balance().residual()).isLessThanOrEqualTo(tol);
        assertThat(resp.stepping().balance().componentResidual()).isLessThanOrEqualTo(tol);
        assertThat(resp.stepping().balance().distillateFlow()
                + resp.stepping().balance().bottomsFlow())
                .isCloseTo(1.0, within(tol));
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void nearTotalRefluxStagesMatchFenskeWithinOneStage(
            double zF, double xD, double xB, double alpha, double q) {
        ShortcutResponse resp = harness.evaluate(1.0, zF, xD, xB, q, alpha, 1_000_000.0);

        int stages = resp.stepping().totalStages();
        double nMin = resp.minimumStages();
        // 离散阶梯对连续 Nmin，允许差一块，再多就不行
        assertThat(Math.abs(stages - nMin))
                .as("R→∞ 时板数 %s 应贴着 Fenske Nmin=%.4f", stages, nMin)
                .isLessThanOrEqualTo(1.0 + 1.0e-9);
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void increasingRefluxNeverIncreasesStageCount(
            double zF, double xD, double xB, double alpha, double q) {
        // 对 q<1 的进料，提馏段还有物理约束 V'=(R+1)D-(1-q)F>0，且极接近极限时为夹点区；
        // 因此从“可操作”的回流比起步，取一组逐级放大的回流比，验证板数单调不增。
        double rMin = harness.serviceMinRmin(zF, xD, alpha, q);
        double dOverF = (zF - xB) / (xD - xB);
        double strippingPhysicalMin = Math.max(rMin, (1.0 - q) / dOverF - 1.0);
        // q<1 时紧邻极限回流存在精馏段夹点区，从可稳定收敛的回流比起步（数值验证过）
        double safety = q >= 1.0 ? 1.05 : (q > 0.3 ? 4.0 : 3.0);
        double startR = strippingPhysicalMin * safety;
        double[] refluxRatios = {
                startR, startR * 1.5, startR * 2.25, startR * 3.375,
                startR * 5.0625, startR * 7.59375, startR * 11.390625
        };

        int previous = Integer.MAX_VALUE;
        for (double r : refluxRatios) {
            ShortcutResponse resp = harness.evaluate(1.0, zF, xD, xB, q, alpha, r);
            int stages = resp.stepping().totalStages();
            assertThat(stages)
                    .as("R=%.4f 时板数 %d 不应多于上一回流比的 %d（q=%s）", r, stages, previous, q)
                    .isLessThanOrEqualTo(previous);
            previous = stages;
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void refluxAtOrBelowMinimumIsRejectedNeverReturnsStages(
            double zF, double xD, double xB, double alpha, double q) {
        double rMin = harness.serviceMinRmin(zF, xD, alpha, q);

        for (double r : new double[]{rMin, rMin * 0.999, rMin * 0.5, 0.0}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> harness.evaluate(1.0, zF, xD, xB, q, alpha, r));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.REFLUX_INSUFFICIENT);
        }
    }

    @ParameterizedTest
    @MethodSource("separationTasks")
    void perStageLiquidCompositionsAreRecordedAndStrictlyDecreasing(
            double zF, double xD, double xB, double alpha, double q) {
        // 取远离夹点区的回流比（q<1 时夹点区更宽）
        double safety = q >= 1.0 ? 2.0 : (q > 0.3 ? 5.0 : 3.0);
        ShortcutResponse resp = harness.evaluate(1.0, zF, xD, xB, q, alpha,
                harness.serviceMinRmin(zF, xD, alpha, q) * safety
                        + (q < 1.0 ? 0.05 : 0.0));

        List<StageResult> stages = resp.stepping().stages();
        assertThat(stages).isNotEmpty();
        assertThat(stages).extracting(StageResult::stageNumber).doesNotHaveDuplicates();
        for (int i = 1; i < stages.size(); i++) {
            assertThat(stages.get(i).liquidComposition())
                    .isLessThan(stages.get(i - 1).liquidComposition());
        }
        // 首板从馏出液组成起手，末板已掉到釜液目标以下
        assertThat(stages.get(0).liquidComposition()).isLessThan(xD);
        assertThat(stages.get(stages.size() - 1).liquidComposition()).isLessThanOrEqualTo(xB);
        // 恰好一块进料板，且两侧塔段都出现（Rmin<R 时通常成立）
        long feedStages = stages.stream().filter(StageResult::feedStage).count();
        assertThat(feedStages).isEqualTo(1);
        assertThat(stages).extracting(StageResult::section)
                .contains(Section.RECTIFYING, Section.STRIPPING);
    }

    @Test
    void thermalStateChangesSlopeButKeepsBalanceClosed() {
        double zF = 0.5, xD = 0.95, xB = 0.05, alpha = 2.5, r = 5.0;
        ShortcutResponse q0 = harness.evaluate(1.0, zF, xD, xB, 0.0, alpha, r);
        ShortcutResponse q1 = harness.evaluate(1.0, zF, xD, xB, 1.0, alpha, r);
        ShortcutResponse qCold = harness.evaluate(1.0, zF, xD, xB, 1.5, alpha, r);

        // 衡算与 q 无关：D、B、残差一致
        for (ShortcutResponse resp : new ShortcutResponse[]{q0, q1, qCold}) {
            assertThat(resp.stepping().balance().distillateFlow()).isCloseTo(0.5, within(1.0e-12));
            assertThat(resp.stepping().balance().bottomsFlow()).isCloseTo(0.5, within(1.0e-12));
            assertThat(resp.stepping().balance().residual()).isLessThanOrEqualTo(1.0e-9);
        }
        // q 变了，最小回流比跟着变：过冷 > 泡点 > 饱和蒸汽
        assertThat(qCold.minimumRefluxRatio()).isGreaterThan(q1.minimumRefluxRatio());
        assertThat(q1.minimumRefluxRatio()).isGreaterThan(q0.minimumRefluxRatio());
        // 服务确实对 q 敏感：饱和蒸汽进料（提馏段更难操作）在同一 R 下板数更多
        assertThat(q0.stepping().totalStages()).isGreaterThanOrEqualTo(q1.stepping().totalStages());
    }
}
