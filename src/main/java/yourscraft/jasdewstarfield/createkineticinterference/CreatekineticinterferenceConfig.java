package yourscraft.jasdewstarfield.createkineticinterference;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import yourscraft.jasdewstarfield.createkineticinterference.common.DistanceType;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.CalculationModel;

public class CreatekineticinterferenceConfig {
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.EnumValue<CalculationModel> calculationModel;
        public final ModConfigSpec.DoubleValue integrationStep, softCapPower, environmentGridStep, biomeBlendRadius, workBudgetMs;
        public final ModConfigSpec.IntValue biomeSampleY, recheckInterval;
        public final DensityTypeConfig waterDensity, windDensity;
        public final ModConfigSpec.DoubleValue windmillInterferenceRadius;
        public final ModConfigSpec.EnumValue<DistanceType> windmillDistanceType;
        public final ModConfigSpec.DoubleValue windmillInterferenceFactor;
        public final ModConfigSpec.IntValue windmillCheckInterval;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceRadius;
        public final ModConfigSpec.EnumValue<DistanceType> waterwheelDistanceType;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceFactor;

        ServerConfig(ModConfigSpec.Builder builder) {
            calculationModel = builder.comment("AUTO: new worlds use DENSITY, existing worlds keep their saved model.",
                    "Back up world and config before changing. Model changes require restart.")
                    .worldRestart().defineEnum("calculationModel", CalculationModel.AUTO);
            builder.push("density");
            integrationStep = builder.comment("Shared XZ integration spacing; must be <= each radius/4. Restart required.")
                    .worldRestart().defineInRange("integrationStep", 2d, 0.5, 16);
            softCapPower = builder.comment("Saturation knee sharpness; larger values keep full output closer to the local supply limit.")
                    .worldRestart().defineInRange("softCapPower", 8d, 2, 8);
            environmentGridStep = builder.worldRestart().defineInRange("environmentGridStep", 4d, 1, 16);
            biomeBlendRadius = builder.worldRestart().defineInRange("biomeBlendRadius", 8d, 0, 64);
            biomeSampleY = builder.comment("Fixed biome-source sampling height; clamped to dimension build limits.")
                    .worldRestart().defineInRange("biomeSampleY", 64, -2048, 2048);
            recheckInterval = builder.worldRestart().defineInRange("recheckInterval", 40, 1, 1000);
            workBudgetMs = builder.comment("Target solver work per tick; an individual source/node may exceed this target.")
                    .worldRestart().defineInRange("workBudgetMs", 1.25d, 0.1, 20);
            waterDensity = new DensityTypeConfig(builder,"water",4096,256);
            windDensity = new DensityTypeConfig(builder,"wind",6144,4096);
            builder.pop();
            builder.comment("Legacy count-model settings; ignored by DENSITY.").push("general");

            builder.push("windmill");

            windmillInterferenceRadius = builder
                    .comment("Detection radius for windmill interference (blocks)")
                    .defineInRange("interferenceRadius", 32.0, 1.0, 1024.0);

            windmillDistanceType = builder
                    .comment("Distance calculation mode for windmills")
                    .comment("EUCLIDEAN_3D: Standard 3D distance (Spherical)")
                    .comment("EUCLIDEAN_2D: Ignore height difference (Cylindrical)")
                    .comment("MANHATTAN_3D: Manhattan distance (Grid based)")
                    .comment("MANHATTAN_2D: Manhattan distance in 2D (Plane-grid based)")
                    .defineEnum("distanceCalculationMode", DistanceType.EUCLIDEAN_2D);

            windmillInterferenceFactor = builder
                    .comment("Interference factor for windmills")
                    .comment("Efficiency = 1 / (1 + Factor * Number of nearby windmills)")
                    .comment("Set to 0 to disable")
                    .comment("Suggested value: 0.1 ~ 1")
                    .defineInRange("interferenceFactor", 0.2, 0.0, 10.0);

            windmillCheckInterval = builder
                    .comment("Time interval of windmills updating their interference status (ticks)")
                    .comment("The smaller, the faster the feedback, but also the greater the performance consumption")
                    .defineInRange("checkInterval", 40, 1, 1000);

            builder.pop();

            builder.push("waterwheel");

            waterwheelInterferenceRadius = builder
                    .comment("Detection radius for waterwheel interference (blocks)")
                    .defineInRange("interferenceRadius", 32.0, 1.0, 1024.0);

            waterwheelDistanceType = builder
                    .comment("Distance calculation mode for waterwheels")
                    .comment("EUCLIDEAN_3D: Standard 3D distance (Spherical)")
                    .comment("EUCLIDEAN_2D: Ignore height difference (Cylindrical)")
                    .comment("MANHATTAN_3D: Manhattan distance (Grid based)")
                    .comment("MANHATTAN_2D: Manhattan distance in 2D (Plane-grid based)")
                    .defineEnum("distanceCalculationMode", DistanceType.EUCLIDEAN_2D);

            waterwheelInterferenceFactor = builder
                    .comment("Interference factor for waterwheels")
                    .comment("Efficiency = 1 / (1 + Factor * Number of nearby waterwheels)")
                    .comment("Set to 0 to disable")
                    .comment("Suggested value: 0.1 ~ 1")
                    .defineInRange("interferenceFactor", 0.1, 0.0, 10.0);

            builder.pop();
        }
    }

    /** 固定 SU 配置便于服主调节集中建设规模，不根据设备台数反推容量。 */
    public static class DensityTypeConfig {
        public final ModConfigSpec.DoubleValue collectionRadius, referenceCapacitySU, legacyUnloadedPotentialSU;
        public final ModConfigSpec.ConfigValue<String> profile;
        DensityTypeConfig(ModConfigSpec.Builder builder,String type,double reference,double legacyPotential) {
            builder.push(type);
            collectionRadius = builder.comment("Euclidean XZ collection radius; competitors can overlap up to 2R away.")
                    .worldRestart().defineInRange("collectionRadius",16d,2,64);
            referenceCapacitySU = builder.comment("Ordinary-biome supply within one collection circle, in SU.")
                    .worldRestart().defineInRange("referenceCapacitySU",reference,0,1e12);
            legacyUnloadedPotentialSU = builder.comment("Estimated SU for old unloaded coordinate-only records.")
                    .worldRestart().defineInRange("legacyUnloadedPotentialSU",legacyPotential,0,1e12);
            profile = builder.worldRestart().define("profile","createkineticinterference:"+type);
            builder.pop();
        }
    }
}
