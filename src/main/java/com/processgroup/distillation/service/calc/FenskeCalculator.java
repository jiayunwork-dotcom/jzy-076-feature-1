package com.processgroup.distillation.service.calc;

import com.processgroup.distillation.service.equilibrium.EquilibriumRelation;
import org.springframework.stereotype.Component;

/**
 * Fenske 模块：全回流下的最少理论板数 Nmin。
 *
 * <p>{@code Nmin = ln[(xD/(1-xD)) * ((1-xB)/xB)] / ln(alpha)}
 *
 * <p>该值是其它回流工况下理论板数的下界基准（逐板板数离散取整，允许差一块板）。
 */
@Component
public class FenskeCalculator {

    public double minimumStages(EquilibriumRelation equilibrium,
                                double distillateComposition, double bottomsComposition) {
        double xD = distillateComposition;
        double xB = bottomsComposition;
        double ratio = (xD / (1.0 - xD)) * ((1.0 - xB) / xB);
        return Math.log(ratio) / Math.log(equilibrium.relativeVolatility());
    }
}
