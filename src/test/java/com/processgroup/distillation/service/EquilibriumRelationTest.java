package com.processgroup.distillation.service;

import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 相平衡关系单元测试：正反向互逆 + 非法挥发度拒绝。
 */
class EquilibriumRelationTest {

    @Test
    void forwardAndInverseAreConsistent() {
        EquilibriumRelation eq = new EquilibriumRelation(2.5);
        for (double x = 0.01; x < 1.0; x += 0.037) {
            double y = eq.vaporInEquilibrium(x);
            double xBack = eq.liquidInEquilibrium(y);
            assertThat(y).isGreaterThan(x);
            assertThat(xBack).isCloseTo(x, org.assertj.core.data.Offset.offset(1.0e-12));
        }
    }

    @Test
    void rejectsNonPositiveVolatility() {
        assertThatThrownBy(() -> new EquilibriumRelation(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EquilibriumRelation(-1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
