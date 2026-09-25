package com.processgroup.distillation.error;

import java.util.List;
import java.util.Map;

/**
 * 结构化错误响应体。
 *
 * @param code    机器可读错误码
 * @param message 错误概要
 * @param detail  具体说明
 * @param fields  逐字段错误（参数校验时填充，key=字段名，value=该字段的问题）
 */
public record ApiError(
        String code,
        String message,
        String detail,
        Map<String, List<String>> fields
) {
}
