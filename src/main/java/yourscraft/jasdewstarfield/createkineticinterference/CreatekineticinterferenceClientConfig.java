package yourscraft.jasdewstarfield.createkineticinterference;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CreatekineticinterferenceClientConfig {
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final Pair<ClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class ClientConfig {
        public final ModConfigSpec.BooleanValue enableDebugHighlights;
        public final ModConfigSpec.IntValue debugHighlightsDuration;

        ClientConfig(ModConfigSpec.Builder builder) {
            builder.push("visuals");

            enableDebugHighlights = builder
                    .comment("Show debug outliner when shift + right-clicking at a windmill bearing (with goggles on)")
                    .comment("It will mark other windmill bearings that could potentially interfere with it")
                    .define("enableDebugHighlights", false);

            debugHighlightsDuration = builder
                    .comment("Time that debug outliner persists after clicking (ms)")
                    .defineInRange("debugHighlightsDuration", 3000, 0, 10000);

            builder.pop();
        }
    }
}
