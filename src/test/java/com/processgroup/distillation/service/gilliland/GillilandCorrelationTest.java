package com.processgroup.distillation.service.gilliland;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gilliland 关联本体判据：端点渐近、严格单调、正反互逆、关系式被钉死。
 */
class GillilandCorrelationTest {

    private final GillilandCorrelation gilliland = new GillilandCorrelation();

    @Test
    void endpointsArePinnedToTheTwoAsymptotes() {
        assertThat(gilliland.yOf(0.0)).isEqualTo(1.0);
        assertThat(gilliland.yOf(1.0)).isEqualTo(0.0);
        // X → 0⁺（R 压向 Rmin）：Y → 1，板数发散
        assertThat(gilliland.yOf(1.0e-4)).isGreaterThan(0.999);
        // X → 1⁻（R 极宽裕）：Y → 0，板数贴向 Nmin
        assertThat(gilliland.yOf(1.0 - 1.0e-9)).isLessThan(1.0e-6);
    }

    @Test
    void correlationIsStrictlyDecreasingAcrossTheDomain() {
        // 对数等距扫 500 个点：Y 必须一路严格下降，绝不回升
        double xPrevious = 1.0e-4;
        double yPrevious = gilliland.yOf(xPrevious);
        for (int i = 1; i <= 500; i++) {
            double x = 1.0e-4 * Math.pow((1.0 - 1.0e-6) / 1.0e-4, i / 500.0);
            double y = gilliland.yOf(x);
            assertThat(y).as("x=%.8g 处 Y=%.12g 应严格小于前一点的 %.12g", x, y, yPrevious)
                    .isLessThan(yPrevious);
            yPrevious = y;
        }
    }

    @Test
    void inverseUndoesForwardAcrossTheDomain() {
        for (int i = 0; i <= 200; i++) {
            double x = 1.0e-3 + (0.999 - 1.0e-3) * i / 200.0;
            double xBack = gilliland.xOf(gilliland.yOf(x));
            assertThat(xBack).as("xOf(yOf(%.6g)) 应回到原横坐标", x)
                    .isCloseTo(x, within(1.0e-9));
        }
    }

    @Test
    void forwardUndoesInverseAcrossTheDomain() {
        for (int i = 1; i <= 199; i++) {
            double y = i / 200.0;
            assertThat(gilliland.yOf(gilliland.xOf(y))).as("yOf(xOf(%.6g)) 应回到原纵坐标", y)
                    .isCloseTo(y, within(1.0e-10));
        }
    }

    @Test
    void knownMolokanovPointPinsTheCorrelation() {
        // Molokanov 拟合式在 X=0.5 的取值：钉死这条关系式本身，防止被替换成别的近似
        assertThat(gilliland.yOf(0.5)).isCloseTo(0.24911304, within(1.0e-6));
    }

    @Test
    void inverseRejectsOutOfDomainOrdinates() {
        for (double y : new double[]{0.0, 1.0, -0.1, 1.1}) {
            assertThrows(IllegalArgumentException.class, () -> gilliland.xOf(y));
        }
    }
}
