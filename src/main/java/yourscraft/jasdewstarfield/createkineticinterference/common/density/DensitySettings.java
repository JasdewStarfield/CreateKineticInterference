package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig;
import java.util.Map;

/** 启动时冻结配置，计算批次不会混用文件热更新前后的半径与精度。 */
public record DensitySettings(double step,double power,double environmentStep,double blend,int sampleY,
                              int interval,long budgetNanos,Map<String,Double> radii,
                              Map<String,Double> capacities,Map<String,Double> estimates,Map<String,String> profiles) {
    public static DensitySettings read() {
        var config = CreatekineticinterferenceConfig.SERVER;
        var water = config.waterDensity; var wind = config.windDensity;
        var radii = Map.of("water",water.collectionRadius.get(),"wind",wind.collectionRadius.get());
        double step = config.integrationStep.get();
        for (double radius : radii.values()) if (!Double.isFinite(radius) || !Double.isFinite(step)
                || step <= 0 || step > radius/4 || Math.pow(2*radius/step+2,2)>65536)
            throw new IllegalArgumentException("density.integrationStep must be <= each collectionRadius/4 and <= 65536 samples/source");
        return new DensitySettings(step,config.softCapPower.get(),config.environmentGridStep.get(),
                config.biomeBlendRadius.get(),config.biomeSampleY.get(),config.recheckInterval.get(),
                (long)(config.workBudgetMs.get()*1e6),radii,
                Map.of("water",water.referenceCapacitySU.get(),"wind",wind.referenceCapacitySU.get()),
                Map.of("water",water.legacyUnloadedPotentialSU.get(),"wind",wind.legacyUnloadedPotentialSU.get()),
                Map.of("water",water.profile.get(),"wind",wind.profile.get()));
    }
}
