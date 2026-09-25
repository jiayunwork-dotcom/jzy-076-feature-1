package com.processgroup.distillation.service;

import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.domain.ShortcutResponse;
import com.processgroup.distillation.domain.StageResult;
import com.processgroup.distillation.domain.SteppingResponse;
import com.processgroup.distillation.domain.StreamFlows;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.calc.FenskeCalculator;
import com.processgroup.distillation.service.calc.UnderwoodCalculator;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.material.MaterialBalance;
import com.processgroup.distillation.service.material.MaterialBalanceCalculator;
import com.processgroup.distillation.service.stepping.McCabeThieleStepper;
import com.processgroup.distillation.service.stepping.OperatingLineFactory;
import com.processgroup.distillation.service.stepping.OperatingLines;
import com.processgroup.distillation.service.validation.InputValidator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简捷法核算服务编排，按职责串联各独立模块：
 * <ol>
 *   <li>入参校验（范围 / 顺序 / 正值）；</li>
 *   <li>工艺可行性：alpha &gt; 1，否则“无法分离”；</li>
 *   <li>物料闭合：反推 D、B 并压残差；</li>
 *   <li>Fenske 求 Nmin、Underwood 求 Rmin；</li>
 *   <li>R &gt; Rmin 检查（绝不带死循环地进入逐板）；</li>
 *   <li>两段共用同一平衡关系与同一衡算体系，McCabe-Thiele 逐板。</li>
 * </ol>
 */
@Service
public class DistillationShortcutService {

    /** R 必须严格大于 Rmin 的判定容差（相对容差叠加绝对容差）。 */
    private static final double R_REL_TOLERANCE = 1.0e-9;
    private static final double R_ABS_TOLERANCE = 1.0e-12;

    private final InputValidator validator;
    private final MaterialBalanceCalculator materialBalanceCalculator;
    private final FenskeCalculator fenskeCalculator;
    private final UnderwoodCalculator underwoodCalculator;
    private final OperatingLineFactory operatingLineFactory;
    private final McCabeThieleStepper stepper;

    public DistillationShortcutService(InputValidator validator,
                                       MaterialBalanceCalculator materialBalanceCalculator,
                                       FenskeCalculator fenskeCalculator,
                                       UnderwoodCalculator underwoodCalculator,
                                       OperatingLineFactory operatingLineFactory,
                                       McCabeThieleStepper stepper) {
        this.validator = validator;
        this.materialBalanceCalculator = materialBalanceCalculator;
        this.fenskeCalculator = fenskeCalculator;
        this.underwoodCalculator = underwoodCalculator;
        this.operatingLineFactory = operatingLineFactory;
        this.stepper = stepper;
    }

    public ShortcutResponse compute(ShortcutRequest request) {
        validator.validate(request);

        double f = request.feedFlow();
        double zF = request.feedComposition();
        double xD = request.distillateComposition();
        double xB = request.bottomsComposition();
        double q = request.feedThermalFactor();
        double alpha = request.relativeVolatility();
        double r = request.refluxRatio();

        // 工艺可行性：alpha <= 1 直接“无法分离”，后续一律不算
        if (alpha <= 1.0) {
            throw new ServiceException(ErrorCode.SEPARATION_IMPOSSIBLE,
                    "无法分离：相对挥发度 alpha=" + alpha + " 小于等于 1");
        }

        // 相平衡关系：全程唯一实例，Fenske/操作线/逐板都引用它
        EquilibriumRelation equilibrium = new EquilibriumRelation(alpha);

        // 物料闭合
        MaterialBalance balance = materialBalanceCalculator.solve(f, zF, xD, xB);

        // Fenske 基准
        double minimumStages = fenskeCalculator.minimumStages(equilibrium, xD, xB);

        // Underwood 最小回流比
        double minimumReflux = underwoodCalculator.minimumRefluxRatio(zF, xD, alpha, q);

        // 回流不足必须在逐板之前拒绝，避免夹点死循环
        double tolerance = Math.max(R_ABS_TOLERANCE, R_REL_TOLERANCE * Math.max(1.0, minimumReflux));
        if (r <= minimumReflux + tolerance) {
            throw new ServiceException(ErrorCode.REFLUX_INSUFFICIENT, String.format(
                    "回流不足：实际回流比 R=%.9g 未大于最小回流比 Rmin=%.9g（容差 %.2e）",
                    r, minimumReflux, tolerance));
        }

        // 两段操作线由同一衡算 + q 推出，并绑定同一平衡关系
        OperatingLines lines = operatingLineFactory.create(equilibrium, balance, r, q);

        // 逐板阶梯
        List<StageResult> stages = stepper.step(equilibrium, balance, lines);
        int feedStageIndex = stages.stream()
                .filter(StageResult::feedStage)
                .mapToInt(StageResult::stageNumber)
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                        "逐板结果中缺少进料板标记"));

        StreamFlows flows = new StreamFlows(
                balance.feedFlow(), balance.distillateFlow(), balance.bottomsFlow(),
                balance.totalResidual(), balance.componentResidual());

        SteppingResponse stepping = new SteppingResponse(
                r, stages.size(), feedStageIndex, flows, stages);

        return new ShortcutResponse(minimumReflux, minimumStages, stepping);
    }
}
