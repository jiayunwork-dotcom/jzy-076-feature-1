package com.processgroup.distillation.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码：携带 HTTP 状态、机器可读 code 与中文说明。
 */
public enum ErrorCode {

    // ---- 入参结构 / 取值非法（400）----
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "输入参数不合法"),
    COMPOSITION_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "COMPOSITION_OUT_OF_RANGE", "组成必须落在 0 到 1 之间"),
    COMPOSITION_ORDER(HttpStatus.BAD_REQUEST, "COMPOSITION_ORDER", "组成顺序必须满足 馏出液组成 > 进料组成 > 釜液组成"),
    NON_POSITIVE_FEED_FLOW(HttpStatus.BAD_REQUEST, "NON_POSITIVE_FEED_FLOW", "进料量必须为正"),
    NON_POSITIVE_VOLATILITY(HttpStatus.BAD_REQUEST, "NON_POSITIVE_VOLATILITY", "相对挥发度必须为正"),
    INVALID_REFLUX_RATIO(HttpStatus.BAD_REQUEST, "INVALID_REFLUX_RATIO", "回流比必须为非负有限值"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "缺少必填参数"),

    // ---- 工艺不可行（422）----
    SEPARATION_IMPOSSIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "SEPARATION_IMPOSSIBLE", "无法分离：相对挥发度小于等于 1"),
    MATERIAL_NOT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "MATERIAL_NOT_CLOSED", "物料衡算不闭合"),
    REFLUX_INSUFFICIENT(HttpStatus.UNPROCESSABLE_ENTITY, "REFLUX_INSUFFICIENT", "回流不足：实际回流比必须大于最小回流比"),
    STRIPPING_FLOW_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "STRIPPING_FLOW_INVALID", "该进料热状态下提馏段气相流量非正，无法构造提馏段操作线"),
    FEED_LINE_PARALLEL(HttpStatus.UNPROCESSABLE_ENTITY, "FEED_LINE_PARALLEL", "q 线与精馏段操作线平行，无法确定进料级交点"),
    STEP_NOT_CONVERGING(HttpStatus.UNPROCESSABLE_ENTITY, "STEP_NOT_CONVERGING", "逐板阶梯不收敛：达到阶梯数上限或组成不再下降"),
    STAGES_BELOW_MINIMUM(HttpStatus.UNPROCESSABLE_ENTITY, "STAGES_BELOW_MINIMUM", "板数不可达：目标理论板数必须大于最少理论板数"),
    INVERSION_NOT_CONVERGING(HttpStatus.UNPROCESSABLE_ENTITY, "INVERSION_NOT_CONVERGING", "Gilliland 关联反查未收敛");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
