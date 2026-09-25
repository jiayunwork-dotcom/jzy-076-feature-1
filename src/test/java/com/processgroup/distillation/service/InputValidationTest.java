package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 边界输入：组成越界、进料量非正、相对挥发度非正/<=1、组成顺序颠倒、缺字段。
 */
class InputValidationTest {

    private final TestHarness harness = new TestHarness();

    private void expectInvalid(ShortcutRequest req, String fieldKey) {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> harness.evaluate(req.feedFlow(), req.feedComposition(),
                        req.distillateComposition(), req.bottomsComposition(),
                        req.feedThermalFactor(), req.relativeVolatility(), req.refluxRatio()));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        if (fieldKey != null) {
            assertThat(ex.getCause()).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, ?> fields = (Map<String, ?>)
                    ((com.processgroup.distillation.service.validation.InputValidator
                            .FieldValidationException) ex.getCause()).fieldErrors();
            assertThat(fields).containsKey(fieldKey);
        }
    }

    @Test
    void compositionOutsideUnitRangeRejected() {
        expectInvalid(new ShortcutRequest(1.0, 1.2, 0.95, 0.05, 1.0, 2.5, 5.0), "feedComposition");
        expectInvalid(new ShortcutRequest(1.0, 0.5, 0.95, -0.1, 1.0, 2.5, 5.0), "bottomsComposition");
        expectInvalid(new ShortcutRequest(1.0, 0.5, 1.01, 0.05, 1.0, 2.5, 5.0), "distillateComposition");
    }

    @Test
    void nonPositiveFeedFlowRejected() {
        expectInvalid(new ShortcutRequest(0.0, 0.5, 0.95, 0.05, 1.0, 2.5, 5.0), "feedFlow");
        expectInvalid(new ShortcutRequest(-3.0, 0.5, 0.95, 0.05, 1.0, 2.5, 5.0), "feedFlow");
    }

    @Test
    void nonPositiveVolatilityRejectedAsInvalidInput() {
        expectInvalid(new ShortcutRequest(1.0, 0.5, 0.95, 0.05, 1.0, 0.0, 5.0), "relativeVolatility");
        expectInvalid(new ShortcutRequest(1.0, 0.5, 0.95, 0.05, 1.0, -2.0, 5.0), "relativeVolatility");
    }

    @Test
    void volatilityAtOrBelowOneMeansSeparationImpossible() {
        // alpha>0 但 <=1：入参合法，工艺上“无法分离”
        for (double alpha : new double[]{1.0, 0.9}) {
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> harness.evaluate(1.0, 0.5, 0.95, 0.05, 1.0, alpha, 5.0));
            assertThat(ex.errorCode()).isEqualTo(ErrorCode.SEPARATION_IMPOSSIBLE);
        }
    }

    @Test
    void reversedCompositionOrderRejected() {
        // xD < zF < xB 的各种颠倒
        expectInvalid(new ShortcutRequest(1.0, 0.9, 0.5, 0.1, 1.0, 2.5, 5.0), "compositionOrder");
        expectInvalid(new ShortcutRequest(1.0, 0.05, 0.5, 0.5, 1.0, 2.5, 5.0), "compositionOrder");
    }

    @Test
    void missingFieldsRejected() {
        expectInvalidReq(new ShortcutRequest(null, 0.5, 0.95, 0.05, 1.0, 2.5, 5.0), "feedFlow");
        expectInvalidReq(new ShortcutRequest(1.0, null, 0.95, 0.05, 1.0, 2.5, 5.0), "feedComposition");
        expectInvalidReq(new ShortcutRequest(1.0, 0.5, 0.95, 0.05, 1.0, 2.5, null), "refluxRatio");
    }

    private void expectInvalidReq(ShortcutRequest req, String fieldKey) {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> harness.evaluate(req));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(ex.getCause()).isNotNull();
        Map<String, ?> fields = ((com.processgroup.distillation.service.validation.InputValidator
                .FieldValidationException) ex.getCause()).fieldErrors();
        assertThat(fields).containsKey(fieldKey);
    }

    @Test
    void negativeRefluxRejectedAsInvalidInput() {
        expectInvalid(new ShortcutRequest(1.0, 0.5, 0.95, 0.05, 1.0, 2.5, -1.0), "refluxRatio");
    }

    @Test
    void nonFiniteValuesRejected() {
        // NaN / 无穷一律视为非法输入（既不是缺字段也不是有限数值）
        ServiceException ex = assertThrows(ServiceException.class,
                () -> harness.evaluate(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        Double.NaN, Double.NaN, Double.NaN));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
