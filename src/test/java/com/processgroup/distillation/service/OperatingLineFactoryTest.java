package com.processgroup.distillation.service;

import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.material.MaterialBalance;
import com.processgroup.distillation.service.material.MaterialBalanceCalculator;
import com.processgroup.distillation.service.stepping.OperatingLineFactory;
import com.processgroup.distillation.service.stepping.OperatingLines;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 操作线模块测试：
 * <ul>
 *   <li>两条操作线必须共用同一相平衡关系（工厂强制注入同一实例）；</li>
 *   <li>q 变化时提馏段斜率随之变化，但物料衡算不参与这一变化；</li>
 *   <li>两线交点落在 q 线上、提馏段线过 (xB, xB)。</li>
 * </ul>
 */
class OperatingLineFactoryTest {

    private final OperatingLineFactory factory = new OperatingLineFactory();
    private final MaterialBalanceCalculator balances = new MaterialBalanceCalculator();

    private OperatingLines linesAt(double q, double r) {
        EquilibriumRelation eq = new EquilibriumRelation(2.5);
        MaterialBalance mb = balances.solve(1.0, 0.5, 0.95, 0.05);
        return factory.create(eq, mb, r, q);
    }

    @Test
    void strippingSlopeMovesWithFeedThermalState() {
        double r = 5.0;
        OperatingLines vapor = linesAt(0.0, r);
        OperatingLines bubble = linesAt(1.0, r);
        OperatingLines cold = linesAt(1.5, r);

        double mV = vapor.stripping().slope();
        double mB = bubble.stripping().slope();
        double mC = cold.stripping().slope();
        // L'/V' 随 q 增大而减小（汽相进料时提馏段线更陡）
        assertThat(mV).isGreaterThan(mB);
        assertThat(mB).isGreaterThan(mC);
        // q=1: L'=L+F, V'=V
        double expectedBubble = (r * 0.5 + 1.0) / ((r + 1.0) * 0.5);
        assertThat(mB).isCloseTo(expectedBubble, within(1.0e-12));
    }

    @Test
    void intersectionLiesOnBothLinesAndStripLinePassesReboilerPoint() {
        for (double q : new double[]{0.0, 0.5, 1.0, 1.5}) {
            OperatingLines lines = linesAt(q, 5.0);
            double xq = lines.intersectionX();
            double yq = lines.intersectionY();

            assertThat(lines.rectifying().yAt(xq)).isCloseTo(yq, within(1.0e-9));
            assertThat(lines.stripping().yAt(xq)).isCloseTo(yq, within(1.0e-9));
            assertThat(lines.stripping().yAt(0.05)).isCloseTo(0.05, within(1.0e-9));
            if (Math.abs(q - 1.0) < 1.0e-12) {
                assertThat(xq).isCloseTo(0.5, within(1.0e-12));
            }
        }
    }

    @Test
    void bothSectionsMustShareTheSameEquilibriumInstance() {
        // 工厂只接受一个 EquilibriumRelation 实例并贯穿两条线，结构上杜绝两份实现：
        // 这里验证同一实例喂入时结果自洽（平衡交点 y* = 平衡线(xq) 仅在 Rmin 处重合，
        // R>Rmin 时交点位于平衡线下方）。
        EquilibriumRelation eq = new EquilibriumRelation(2.5);
        MaterialBalance mb = balances.solve(1.0, 0.5, 0.95, 0.05);
        OperatingLines lines = factory.create(eq, mb, 5.0, 1.0);
        double yStar = eq.vaporInEquilibrium(lines.intersectionX());
        assertThat(lines.intersectionY()).isLessThan(yStar);
    }

    @Test
    void invalidStrippingVaporFlowRejected() {
        // q=0 饱和蒸汽进料时 V'=V-F；取极小 R 使 V<=F，构造出非正 V'
        EquilibriumRelation eq = new EquilibriumRelation(2.5);
        MaterialBalance mb = balances.solve(1.0, 0.5, 0.95, 0.05);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> factory.create(eq, mb, 0.5, 0.0));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.STRIPPING_FLOW_INVALID);
    }
}
