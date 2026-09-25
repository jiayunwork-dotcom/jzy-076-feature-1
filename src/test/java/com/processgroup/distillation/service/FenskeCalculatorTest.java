package com.processgroup.distillation.service;

import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Fenske 最少理论板数测试。
 */
class FenskeCalculatorTest {

    private final FenskeCalculator fenske = new FenskeCalculator();

    @ParameterizedTest
    @CsvSource({
            // xD,   xB,    alpha, 教材参考 Nmin
            "0.95,  0.05,  2.5,    6.42687",
            "0.90,  0.10,  2.0,    6.33985",
            "0.80,  0.05,  3.0,    3.94200"
    })
    void matchesHandCalculatedValues(double xD, double xB, double alpha, double expected) {
        double nMin = fenske.minimumStages(new EquilibriumRelation(alpha), xD, xB);
        assertThat(nMin).isCloseTo(expected, within(2.0e-4));
    }

    @org.junit.jupiter.api.Test
    void higherVolatilityMeansFewerStages() {
        EquilibriumRelation low = new EquilibriumRelation(1.5);
        EquilibriumRelation high = new EquilibriumRelation(4.0);
        double nLow = fenske.minimumStages(low, 0.9, 0.1);
        double nHigh = fenske.minimumStages(high, 0.9, 0.1);
        assertThat(nHigh).isLessThan(nLow);
    }
}
