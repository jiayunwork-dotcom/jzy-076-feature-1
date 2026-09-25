package com.processgroup.distillation.service;

import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Underwood 最小回流比测试，覆盖不同进料热状态。
 */
class UnderwoodCalculatorTest {

    private final UnderwoodCalculator underwood = new UnderwoodCalculator();

    @ParameterizedTest
    @CsvSource({
            // zF,   xD,   alpha, q,    Rmin（与 McCabe-Thiele 图解结果对照）
            "0.50,  0.95, 2.5,   1.0,  1.10000",  // 泡点进料
            "0.50,  0.95, 2.5,   0.0,  0.70000",  // 饱和蒸汽进料
            "0.30,  0.80, 3.0,   0.5,  0.39879",  // 汽液混合
            "0.45,  0.90, 2.0,   1.5,  2.15009"   // 过冷进料
    })
    void matchesGraphicalValues(double zF, double xD, double alpha, double q, double expected) {
        double rMin = underwood.minimumRefluxRatio(zF, xD, alpha, q);
        assertThat(rMin).isCloseTo(expected, within(2.0e-4));
    }

    @org.junit.jupiter.api.Test
    void colderFeedRequiresLowerRefluxThanSaturatedVapor() {
        double rVapor = underwood.minimumRefluxRatio(0.5, 0.95, 2.5, 0.0);
        double rBubble = underwood.minimumRefluxRatio(0.5, 0.95, 2.5, 1.0);
        double rCold = underwood.minimumRefluxRatio(0.5, 0.95, 2.5, 1.5);
        assertThat(rCold).isGreaterThan(rBubble);
        assertThat(rBubble).isGreaterThan(rVapor);
    }
}
