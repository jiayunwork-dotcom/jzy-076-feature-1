package com.processgroup.distillation.service.validation;

import com.processgroup.distillation.domain.GillilandRefluxRequest;
import com.processgroup.distillation.domain.GillilandStagesRequest;
import com.processgroup.distillation.domain.ShortcutRequest;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入站参数校验：组成范围、组成顺序、进料量、相对挥发度正值、回流比有限非负。
 *
 * <p>校验失败汇总全部字段问题，抛结构化 {@link ErrorCode#INVALID_INPUT}。
 * 工艺可行性判据（alpha&lt;=1 无法分离、R&lt;=Rmin 回流不足）在服务层判定，
 * 以便区分“参数非法”与“工艺不可行”两类错误。
 */
@Component
public class InputValidator {

    public void validate(ShortcutRequest req) {
        if (req == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "请求体为空");
        }
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        validateColumnSpecification(fieldErrors,
                req.feedComposition(), req.distillateComposition(), req.bottomsComposition(),
                req.feedThermalFactor(), req.relativeVolatility());

        requireFinitePositive(fieldErrors, "feedFlow", req.feedFlow(),
                ErrorCode.NON_POSITIVE_FEED_FLOW.message());

        Double r = req.refluxRatio();
        if (r == null || !Double.isFinite(r) || r < 0.0) {
            fieldErrors.put("refluxRatio", List.of(ErrorCode.INVALID_REFLUX_RATIO.message()));
        }

        raiseIfAnyFieldError(fieldErrors);
    }

    /**
     * 校验「给 R 求 N」请求：分离任务列与回流比取值；R 与 Rmin 的工艺关系
     * （R 必须严格大于 Rmin）由服务层用同一套容差判据把关。
     */
    public void validate(GillilandRefluxRequest req) {
        if (req == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "请求体为空");
        }
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        validateColumnSpecification(fieldErrors,
                req.feedComposition(), req.distillateComposition(), req.bottomsComposition(),
                req.feedThermalFactor(), req.relativeVolatility());

        Double r = req.refluxRatio();
        if (r == null || !Double.isFinite(r) || r < 0.0) {
            fieldErrors.put("refluxRatio", List.of(ErrorCode.INVALID_REFLUX_RATIO.message()));
        }

        raiseIfAnyFieldError(fieldErrors);
    }

    /**
     * 校验「给 N 求 R」请求：分离任务列与目标板数取值；N 与 Nmin 的工艺关系
     * （N 必须严格大于 Nmin）由服务层判据把关，因为 Nmin 要现算。
     */
    public void validate(GillilandStagesRequest req) {
        if (req == null) {
            throw new ServiceException(ErrorCode.INVALID_INPUT, "请求体为空");
        }
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        validateColumnSpecification(fieldErrors,
                req.feedComposition(), req.distillateComposition(), req.bottomsComposition(),
                req.feedThermalFactor(), req.relativeVolatility());

        requireFinitePositive(fieldErrors, "targetStages", req.targetStages(),
                "目标理论板数必须为正的有限值");

        raiseIfAnyFieldError(fieldErrors);
    }

    /**
     * 分离任务列的共用校验：组成范围/顺序/端点、q 有限、alpha 有限为正。
     * 三个入口（简捷法全量、Gilliland 两条路径）共用同一套判据，不重复造轮子。
     */
    private void validateColumnSpecification(Map<String, List<String>> fieldErrors,
                                             Double zF, Double xD, Double xB,
                                             Double q, Double alpha) {
        requireFinite(fieldErrors, "feedThermalFactor", q);

        requireFiniteInUnitRange(fieldErrors, "feedComposition", zF);
        requireFiniteInUnitRange(fieldErrors, "distillateComposition", xD);
        requireFiniteInUnitRange(fieldErrors, "bottomsComposition", xB);

        if (alpha == null || !Double.isFinite(alpha)) {
            fieldErrors.put("relativeVolatility", List.of("相对挥发度缺失或不是有限数值"));
        } else if (alpha <= 0.0) {
            fieldErrors.put("relativeVolatility",
                    List.of(ErrorCode.NON_POSITIVE_VOLATILITY.message()));
        }

        // 组成顺序：馏出液 > 进料 > 釜液（端点 0/1 也不允许，保证 Fenske 对数有定义）
        if (zF != null && xD != null && xB != null
                && Double.isFinite(zF) && Double.isFinite(xD) && Double.isFinite(xB)
                && inUnitRange(zF) && inUnitRange(xD) && inUnitRange(xB)
                && !(xD > zF && zF > xB)) {
            fieldErrors.put("compositionOrder", List.of(ErrorCode.COMPOSITION_ORDER.message()));
        }

        // 端点组成拒绝（在顺序检查之后，避免同一字段重复登记）
        if (zF != null && Double.isFinite(zF) && !isStrictlyInternal(zF)) {
            fieldErrors.put("feedComposition", List.of(
                    ErrorCode.COMPOSITION_OUT_OF_RANGE.message() + "（进料组成不能取 0 或 1）"));
        }
        if (xD != null && Double.isFinite(xD) && !isStrictlyInternal(xD)) {
            fieldErrors.put("distillateComposition", List.of(
                    ErrorCode.COMPOSITION_OUT_OF_RANGE.message() + "（馏出液组成不能取 0 或 1）"));
        }
        if (xB != null && Double.isFinite(xB) && !isStrictlyInternal(xB)) {
            fieldErrors.put("bottomsComposition", List.of(
                    ErrorCode.COMPOSITION_OUT_OF_RANGE.message() + "（釜液组成不能取 0 或 1）"));
        }
    }

    private static void raiseIfAnyFieldError(Map<String, List<String>> fieldErrors) {
        if (!fieldErrors.isEmpty()) {
            ServiceException ex = new ServiceException(ErrorCode.INVALID_INPUT,
                    "存在 " + fieldErrors.size() + " 类字段校验错误");
            ex.initCause(new FieldValidationException(fieldErrors));
            throw ex;
        }
    }

    private static boolean inUnitRange(double v) {
        return v >= 0.0 && v <= 1.0;
    }

    /**
     * 组成必须严格落在开区间 (0,1)：端点 0/1 会使 Fenske 对数无定义，
     * 且与“严格顺序 xB &lt; zF &lt; xD”矛盾，按组成越界结构化拒绝。
     */
    private static boolean isStrictlyInternal(double v) {
        return v > 0.0 && v < 1.0;
    }

    private void requireFiniteInUnitRange(Map<String, List<String>> errors, String field, Double v) {
        if (v == null || !Double.isFinite(v)) {
            errors.put(field, List.of("字段缺失或不是有限数值"));
        } else if (!inUnitRange(v)) {
            errors.put(field, List.of(ErrorCode.COMPOSITION_OUT_OF_RANGE.message()));
        }
    }

    private void requireFinitePositive(Map<String, List<String>> errors, String field,
                                       Double v, String reason) {
        if (v == null || !Double.isFinite(v)) {
            errors.put(field, List.of("字段缺失或不是有限数值"));
        } else if (v <= 0.0) {
            errors.put(field, List.of(reason));
        }
    }

    private void requireFinite(Map<String, List<String>> errors, String field, Double v) {
        if (v == null || !Double.isFinite(v)) {
            errors.put(field, List.of("字段缺失或不是有限数值"));
        }
    }

    /** 携带逐字段错误明细，供全局异常处理器展开到响应体。 */
    public static final class FieldValidationException extends RuntimeException {
        private final transient Map<String, List<String>> fieldErrors;

        public FieldValidationException(Map<String, List<String>> fieldErrors) {
            super("字段校验错误");
            this.fieldErrors = fieldErrors;
        }

        public Map<String, List<String>> fieldErrors() {
            return fieldErrors;
        }
    }
}
