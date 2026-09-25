package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.domain.ShortcutResponse;
import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import com.processgroup.distillation.service.material.MaterialBalanceCalculator;
import com.processgroup.distillation.service.stepping.McCabeThieleStepper;
import com.processgroup.distillation.service.stepping.OperatingLineFactory;
import com.processgroup.distillation.service.validation.InputValidator;

/**
 * 测试用手工装配（与 Spring 装配一致的依赖关系），方便直接调用服务与取 Rmin。
 */
final class TestHarness {

    private final UnderwoodCalculator underwood = new UnderwoodCalculator();
    private final DistillationShortcutService service = new DistillationShortcutService(
            new InputValidator(),
            new MaterialBalanceCalculator(),
            new FenskeCalculator(),
            underwood,
            new OperatingLineFactory(),
            new McCabeThieleStepper());

    ShortcutResponse evaluate(ShortcutRequest request) {
        return service.compute(request);
    }

    ShortcutResponse evaluate(double f, double zF, double xD, double xB, double q,
                              double alpha, double r) {
        return service.compute(new ShortcutRequest(f, zF, xD, xB, q, alpha, r));
    }

    double serviceMinRmin(double zF, double xD, double alpha, double q) {
        return underwood.minimumRefluxRatio(zF, xD, alpha, q);
    }
}
