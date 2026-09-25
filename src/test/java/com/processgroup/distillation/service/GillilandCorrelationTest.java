package com.processgroup.distillation.service;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.gilliland.GillilandCorrelation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gilliland 关联模块独立测试：
 * <ol>
 *   <li>与经典 Gilliland 关联图的教材对照点一致；</li>
 *   <li>两个渐近端点落位：X=0→Y=1、X=1→Y=0；</li>
 *   <li>Y 随 X 在稠密网格上严格单调递减；</li>
 *   <li>正读反查同一条曲线、在 X 全域正反闭合；</li>
 *   <li>越界坐标结构化拒绝。</li>
 * </ol>
 */
class GillilandCorrelationTest {

    private final GillilandCorrelation gilliland = new GillilandCorrelation();

    @ParameterizedTest(name = "X={0} 时 Y≈{1}（经典 Gilliland 图对照点）")
    @CsvSource({
            "0.05, 0.608",
            "0.10, 0.554",
            "0.20, 0.460",
            "0.30, 0.381",
            "0.40, 0.311",
            "0.50, 0.249",
            "0.60, 0.192",
            "0.70, 0.140",
            "0.80, 0.091",
            "0.90, 0.044"
    })
    void matchesClassicGillilandChartPoints(double x, double expectedY) {
        assertThat(gilliland.yAtX(x)).isCloseTo(expectedY, within(1.5e-3));
    }

    @Test
    void asymptoteAtZeroXIsUnity() {
        assertThat(gilliland.yAtX(0.0)).isEqualTo(1.0);
        // 紧邻 X=0：Y 已极其接近 1，板数裕量几乎吃满
        assertThat(gilliland.yAtX(1.0e-4)).isGreaterThan(0.999);
    }

    @Test
    void endpointAtUnitXIsZero() {
        assertThat(gilliland.yAtX(1.0)).isEqualTo(0.0);
        // 宽裕回流极限 Y 趋近 0
        assertThat(gilliland.yAtX(1.0 - 1.0e-6)).isLessThan(1.0e-3);
    }

    @Test
    void yIsStrictlyDecreasingOnDenseGrid() {
        double previousY = gilliland.yAtX(1.0e-9);
        for (int i = 1; i <= 10_000; i++) {
            double x = i / 10_000.0;
            double y = gilliland.yAtX(x);
            assertThat(y)
                    .as("X=%.6f 处 Y=%.12f 必须严格小于前一点 %.12f", x, y, previousY)
                    .isLessThan(previousY);
            previousY = y;
        }
    }

    @ParameterizedTest(name = "X={0} 正读再反查必须回到同一 X")
    @ValueSource(doubles = {
            1.0e-5, 1.0e-4, 1.0e-3, 0.01, 0.02, 0.05, 0.1, 0.2, 0.35,
            0.5, 0.65, 0.8, 0.9, 0.95, 0.99, 0.999, 0.999999, 1.0
    })
    void forwardThenInverseRoundTripsAcrossWholeDomain(double x) {
        double y = gilliland.yAtX(x);
        double xBack = gilliland.xAtY(y);
        // X=1 端点精确闭合；全域相对误差在双精度量级
        double tol = x == 1.0 ? 0.0 : Math.max(1.0e-10, 1.0e-9 * x);
        assertThat(xBack).as("X=%.3e → Y=%.12f → X'=%.3e", x, y, xBack)
                .isCloseTo(x, within(tol));
    }

    @ParameterizedTest(name = "Y={0} 反查再正读必须回到同一 Y")
    @ValueSource(doubles = {
            1.0e-10, 1.0e-6, 0.001, 0.05, 0.25, 0.5, 0.75, 0.9, 0.99,
            0.999, 0.9999, 0.999999, 1.0 - 1.0e-9
    })
    void inverseThenForwardRoundTripsAcrossWholeDomain(double y) {
        double x = gilliland.xAtY(y);
        double yBack = gilliland.yAtX(x);
        // Y 接近 1 时 1-Y 只有很少几位有效数字，容差按可分辨精度放宽
        double tol = Math.max(1.0e-12, 1.0e-9 * (1.0 - y));
        assertThat(yBack).as("Y=%.12f → X=%.6e → Y'=%.12f", y, x, yBack)
                .isCloseTo(y, within(tol));
    }

    @Test
    void inverseIsUniqueAndOrderedWithForward() {
        // 反查解必须与正读一致且随 Y 单调（同一条曲线，不是另凑的近似式）
        double previousX = Double.MAX_VALUE;
        for (int i = 1; i < 1_000; i++) {
            double y = i / 1000.0;
            double x = gilliland.xAtY(y);
            assertThat(x).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
            assertThat(x).as("Y=%.4f 反查 X=%.12f 应严格递增", y, x)
                    .isLessThan(previousX);
            previousX = x;
        }
        assertThat(gilliland.xAtY(0.0)).isEqualTo(1.0);
    }

    @Test
    void inverseStaysFastAndAccurateInSteepRegionNearUnity() {
        // 对应 N 上千万的极端设计点：单调括号 + 解析导数牛顿仍在极少步数内收敛，
        // 且正读回来与目标 Y 的差异只来自双精度有效位数
        for (double oneMinusY : new double[]{1.0e-4, 1.0e-6, 1.0e-8, 1.0e-10}) {
            double y = 1.0 - oneMinusY;
            double x = gilliland.xAtY(y);
            assertThat(x).isPositive();
            assertThat(gilliland.yAtX(x)).isCloseTo(y, within(2.0 * Math.ulp(y)));
        }
    }

    @Test
    void xOutsideUnitIntervalIsRejected() {
        for (double bad : new double[]{-0.1, -1.0, 1.0001, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            ServiceException ex = assertThrows(ServiceException.class, () -> gilliland.yAtX(bad));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Test
    void yAtUnityHasNoFiniteXAndIsRejected() {
        // Y=1 对应 X=0、板数无界，反查路径不接受该端点
        ServiceException ex = assertThrows(ServiceException.class, () -> gilliland.xAtY(1.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        for (double bad : new double[]{-0.1, 1.5, Double.NaN}) {
            assertThat(assertThrows(ServiceException.class, () -> gilliland.xAtY(bad)).errorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }
}
