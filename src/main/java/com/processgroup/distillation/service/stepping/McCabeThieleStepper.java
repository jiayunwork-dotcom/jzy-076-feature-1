package com.processgroup.distillation.service.stepping;

import com.processgroup.distillation.domain.Section;
import com.processgroup.distillation.domain.StageResult;
import com.processgroup.distillation.error.ErrorCode;
import com.processgroup.distillation.error.ServiceException;
import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import com.processgroup.distillation.service.material.MaterialBalance;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * McCabe-Thiele 逐板阶梯迭代。
 *
 * <p>从塔顶 (xD, xD) 起手，每一级阶梯：
 * <ol>
 *   <li>水平走到平衡线：由当前蒸汽组成 y 经 <b>唯一</b>的 {@link EquilibriumRelation}
 *       反求平衡液相组成 x_n（一块理论板）；</li>
 *   <li>垂直走到当前塔段操作线，得到下一级蒸汽组成 y_{n+1}；</li>
 *   <li>当阶梯越过两操作线交点（x_n 首次不大于 xq）切换到提馏段操作线，该级即进料板。</li>
 * </ol>
 * 直到液相组成掉到釜液目标 xB 以下为止。硬上限与“不下降”检测保证永不陷入死循环。
 */
@Component
public class McCabeThieleStepper {

    /** 阶梯数硬上限：正常设计板数远小于此，触顶说明夹点/不收敛。 */
    public static final int MAX_STAGES = 100_000;

    /** 单级液相组成最小下降量，低于此视为夹点停滞。 */
    private static final double MIN_PROGRESS = 1.0e-14;

    /**
     * 执行逐板阶梯。
     *
     * @param equilibrium 全服务唯一相平衡关系（精馏段/提馏段共用）
     * @param balance     物料衡算结果
     * @param lines       同一物料衡算 + q 推出的一对操作线
     * @return 从塔顶到塔釜的逐板记录
     */
    public List<StageResult> step(EquilibriumRelation equilibrium, MaterialBalance balance,
                                  OperatingLines lines) {
        double xB = balance.bottomsComposition();
        double xq = lines.intersectionX();

        List<StageResult> stages = new ArrayList<>();
        double x = balance.distillateComposition(); // 当前点横坐标，起手 x0 = xD
        double y = balance.distillateComposition(); // 当前点纵坐标，全凝器 y1 = xD
        boolean feedSwitched = false;
        int feedStageIndex = -1;

        while (x > xB) {
            if (stages.size() >= MAX_STAGES) {
                throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING, String.format(
                        "逐板阶梯达到 %d 级上限仍未到达釜液组成 xB=%.6f（当前 x=%.6f）",
                        MAX_STAGES, xB, x));
            }

            // 1) 水平到平衡线：理论板，液相组成
            double xNext = equilibrium.liquidInEquilibrium(y);
            if (!Double.isFinite(xNext) || xNext < 0.0 || xNext > 1.0) {
                throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING, String.format(
                        "平衡线反算液相组成越界：y=%.6f -> x=%.6f", y, xNext));
            }
            if (xNext >= x - MIN_PROGRESS) {
                throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING, String.format(
                        "逐板组成不再下降，疑似夹点：x 由 %.9f 变为 %.9f（R 过于接近最小回流比）",
                        x, xNext));
            }

            // 2) 进料板切换：阶梯越过操作线交点
            if (!feedSwitched && xNext <= xq) {
                feedSwitched = true;
                feedStageIndex = stages.size() + 1;
            }

            // 3) 垂直到操作线（两段共用同一平衡关系，操作线由同一衡算体系推出）
            OperatingLine activeLine = feedSwitched ? lines.stripping() : lines.rectifying();
            double yNext = activeLine.yAt(xNext);
            if (!Double.isFinite(yNext)) {
                throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING, String.format(
                        "操作线给出非有限蒸汽组成：x=%.6f", xNext));
            }

            stages.add(new StageResult(
                    stages.size() + 1,
                    xNext,
                    yNext,
                    feedSwitched ? Section.STRIPPING : Section.RECTIFYING,
                    feedSwitched && stages.size() + 1 == feedStageIndex));

            x = xNext;
            y = yNext;
        }

        if (feedStageIndex < 0) {
            // 理论上 xq >= xB 时总会切换；保险起见给出明确错误而非静默
            throw new ServiceException(ErrorCode.STEP_NOT_CONVERGING,
                    "逐板过程中未能确定进料板（操作线交点低于釜液组成）");
        }
        return stages;
    }
}
