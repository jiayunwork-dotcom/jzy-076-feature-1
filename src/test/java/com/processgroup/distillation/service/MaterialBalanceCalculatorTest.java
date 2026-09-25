package com.processgroup.distillation.service;

import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.material.MaterialBalance;
import com.processgroup.distillation.service.material.MaterialBalanceCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 物料衡算模块测试：D、B 反推 + 衡算残差必须落在容差内。
 */
class MaterialBalanceCalculatorTest {

    private final MaterialBalanceCalculator calculator = new MaterialBalanceCalculator();

    @ParameterizedTest
    @CsvSource({
            // F,    zF,   xD,   xB
            "1.0,    0.50, 0.95, 0.05",
            "100.0,  0.40, 0.90, 0.10",
            "0.001,  0.30, 0.80, 0.05",
            "42.0,   0.45, 0.90, 0.10",
            "7.5,    0.211,0.923,0.032"
    })
    void balanceClosesWithinToleranceForEveryValidInput(double f, double zF, double xD, double xB) {
        MaterialBalance mb = calculator.solve(f, zF, xD, xB);

        assertThat(mb.distillateFlow() + mb.bottomsFlow()).isCloseTo(f, within(1.0e-12 * f + 1.0e-12));
        // 判据一：任何一组合法输入，物料衡算残差落在容差内
        assertThat(mb.totalResidual()).isLessThanOrEqualTo(
                Math.max(MaterialBalanceCalculator.ABSOLUTE_TOLERANCE,
                        MaterialBalanceCalculator.RELATIVE_TOLERANCE * f));
        assertThat(mb.componentResidual()).isLessThanOrEqualTo(
                Math.max(MaterialBalanceCalculator.ABSOLUTE_TOLERANCE,
                        MaterialBalanceCalculator.RELATIVE_TOLERANCE * f));
        // 显式复核两条衡算
        assertThat(mb.distillateFlow() * xD + mb.bottomsFlow() * xB)
                .isCloseTo(f * zF, within(1.0e-12 * f + 1.0e-12));
    }

    @Test
    void knownCaseFlows() {
        MaterialBalance mb = calculator.solve(1.0, 0.5, 0.95, 0.05);
        assertThat(mb.distillateFlow()).isCloseTo(0.5, within(1.0e-12));
        assertThat(mb.bottomsFlow()).isCloseTo(0.5, within(1.0e-12));
    }

    @Test
    void identicalProductCompositionsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(ServiceException.class,
                () -> calculator.solve(1.0, 0.5, 0.5, 0.5));
    }
}
