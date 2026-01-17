package yourscraft.jasdewstarfield.createkineticinterference;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CreatekineticinterferenceConfig {
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public static class ServerConfig {
        public final ModConfigSpec.BooleanValue windmillEnable;
        public final ModConfigSpec.DoubleValue windmillInterferenceRadius;
        public final ModConfigSpec.DoubleValue windmillInterferenceFactor;
        public final ModConfigSpec.IntValue windmillCheckInterval;
        public final ModConfigSpec.BooleanValue waterwheelEnable;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceRadius;
        public final ModConfigSpec.DoubleValue waterwheelInterferenceFactor;

        ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("general");

            builder.push("windmill");

            windmillEnable = builder
                    .comment("Enable interference for windmills")
                    .define("", true);

            windmillInterferenceRadius = builder
                    .comment("Detection radius for windmill interference (blocks)")
                    .defineInRange("interferenceRadius", 32.0, 1.0, 1024.0);

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

            waterwheelEnable = builder
                    .comment("Enable interference for waterwheels")
                    .comment("Create has a Lazytick logic for waterwheels, so the interval cannot be defined.")
                    .define("", true);

            waterwheelInterferenceRadius = builder
                    .comment("Detection radius for waterwheel interference (blocks)")
                    .defineInRange("interferenceRadius", 32.0, 1.0, 1024.0);

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
