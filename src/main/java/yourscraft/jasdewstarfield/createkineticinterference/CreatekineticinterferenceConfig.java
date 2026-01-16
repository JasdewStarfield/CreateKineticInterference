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
        public final ModConfigSpec.DoubleValue interferenceRadius;
        public final ModConfigSpec.DoubleValue interferenceFactor;
        public final ModConfigSpec.IntValue checkInterval;
        public final ModConfigSpec.BooleanValue showDebugOutliner;

        ServerConfig(ModConfigSpec.Builder builder) {
            builder.push("general");

            interferenceRadius = builder
                    .comment("Detection radius for windmill interference (blocks)")
                    .defineInRange("interferenceRadius", 32.0, 1.0, 1024.0);

            interferenceFactor = builder
                    .comment("Interference factor")
                    .comment("Efficiency = 1 / (1 + Factor * Number of nearby windmills)")
                    .comment("Set to 0 to disable")
                    .comment("Suggested value: 0.1 ~ 1")
                    .defineInRange("interferenceFactor", 0.3, 0.0, 10.0);

            checkInterval = builder
                    .comment("Time interval of windmills updating their interference status (ticks)")
                    .comment("The smaller, the faster the feedback, but also the greater the performance consumption")
                    .defineInRange("checkInterval", 40, 1, 1000);

            showDebugOutliner = builder
                    .comment("Show debug outliner when shift + right-clicking at a windmill bearing")
                    .comment("It will mark other windmill bearings that could potentially interfere with it")
                    .define("showDebugOutliner", false);

            builder.pop();
        }
    }
}
