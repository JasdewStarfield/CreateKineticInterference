package yourscraft.jasdewstarfield.createkineticinterference;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import yourscraft.jasdewstarfield.createkineticinterference.common.DistanceType;

public class CreatekineticinterferenceConfig {
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.DoubleValue windmillInterferenceRadius;
        public final ModConfigSpec.EnumValue<DistanceType> windmillDistanceType;
        public final ModConfigSpec.DoubleValue windmillInterferenceFactor;
        public final ModConfigSpec.IntValue windmillCheckInterval;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceRadius;
        public final ModConfigSpec.EnumValue<DistanceType> waterwheelDistanceType;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceFactor;

        ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("general");

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
}
