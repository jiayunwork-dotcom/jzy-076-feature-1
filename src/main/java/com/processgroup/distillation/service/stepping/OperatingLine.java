package com.processgroup.distillation.service.stepping;

/**
 * 一条直线形式的操作线：y = slope * x + intercept。
 */
public record OperatingLine(double slope, double intercept) {

    public double yAt(double x) {
        return slope * x + intercept;
    }
}
