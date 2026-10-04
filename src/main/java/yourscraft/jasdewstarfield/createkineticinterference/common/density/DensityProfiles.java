package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.packs.resources.ResourceManager;
import yourscraft.jasdewstarfield.createkineticinterference.Createkineticinterference;

import java.util.*;

/** 规则先完全解析并验证，再一次替换；失败时保留整套可用快照。 */
public final class DensityProfiles {
    public record Rule(ResourceLocation tag, int priority, double multiplier) {}
    public record Profile(String type, double defaultMultiplier, List<Rule> rules,
                          Map<ResourceLocation,Double> dimensions, Map<ResourceLocation,Double> biomeValues) {
        public double multiplier(Holder<Biome> biome) {
            if (biomeValues != null) return biome.unwrapKey().map(key -> biomeValues.getOrDefault(key.location(),defaultMultiplier)).orElse(defaultMultiplier);
            return rules.stream().filter(r -> biome.is(TagKey.create(Registries.BIOME, r.tag())))
                    .max(Comparator.comparingInt(Rule::priority)).map(Rule::multiplier).orElse(defaultMultiplier);
        }
    }
    private static Map<ResourceLocation, Profile> profiles = defaults();
    private static long version;
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Createkineticinterference.MODID, path);
    }
    private static Map<ResourceLocation, Profile> defaults() {
        Map<ResourceLocation,Double> water = new HashMap<>(), wind = new HashMap<>();
        for (String name : List.of("river","frozen_river")) water.put(ResourceLocation.withDefaultNamespace(name),2.0);
        for (String name : List.of("ocean","deep_ocean","cold_ocean","deep_cold_ocean","lukewarm_ocean","deep_lukewarm_ocean",
                "warm_ocean","frozen_ocean","deep_frozen_ocean","windswept_hills","windswept_forest","windswept_gravelly_hills",
                "meadow","grove","snowy_slopes","frozen_peaks","jagged_peaks","stony_peaks"))
            wind.put(ResourceLocation.withDefaultNamespace(name),2.0);
        return Map.of(id("water"), new Profile("water", 1, List.of(new Rule(id("water_abundant"),100,2.0)),Map.of(),Map.copyOf(water)),
                id("wind"), new Profile("wind",1,List.of(new Rule(id("wind_abundant"),100,2.0)),Map.of(),Map.copyOf(wind)));
    }
    public static long version() { return version; }
    public static Profile get(String id, String type) {
        Profile profile = profiles.get(ResourceLocation.parse(id));
        if (profile == null || !profile.type().equals(type)) {
            Createkineticinterference.LOGGER.error("Missing/wrong selected density profile {}; using built-in {} after rejected reload",id,type);
            return defaults().get(id(type));
        }
        return profile;
    }
    public static Profile parse(JsonObject json) {
        if (json.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported schema_version");
        String type = json.get("resource_type").getAsString();
        if (!type.equals("water") && !type.equals("wind")) throw new IllegalArgumentException("Unknown resource_type: " + type);
        double fallback = number(json.get("default_multiplier"));
        var rules = new ArrayList<Rule>();
        for (var element : json.getAsJsonArray("rules")) {
            var rule = element.getAsJsonObject();
            rules.add(new Rule(ResourceLocation.parse(rule.get("biome_tag").getAsString()),
                    rule.get("priority").getAsInt(), number(rule.get("multiplier"))));
        }
        Map<ResourceLocation,Double> dimensions = new HashMap<>();
        if (json.has("dimension_multipliers")) json.getAsJsonObject("dimension_multipliers").entrySet()
                .forEach(entry -> dimensions.put(ResourceLocation.parse(entry.getKey()),number(entry.getValue())));
        return new Profile(type, fallback, List.copyOf(rules),Map.copyOf(dimensions),null);
    }
    private static double number(JsonElement element) {
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Multiplier must be finite and nonnegative");
        return value;
    }
    public static boolean reload(ResourceManager manager, RegistryAccess access, String water, String wind) {
        String path = "cki_density_profiles";
        try {
            Map<ResourceLocation, Profile> candidate = new HashMap<>(defaults());
            for (var entry : manager.listResources(path, id -> id.getPath().endsWith(".json")).entrySet()) {
                path = entry.getKey().toString();
                try (var reader = entry.getValue().openAsReader()) {
                    String file = entry.getKey().getPath();
                    var key = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                            file.substring("cki_density_profiles/".length(), file.length()-5));
                    candidate.put(key, parse(JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
            Map<ResourceLocation,Profile> resolved = new HashMap<>();
            for (var entry : candidate.entrySet()) {
                path = entry.getKey().toString();
                var registry = access.registryOrThrow(Registries.BIOME);
                for (var rule : entry.getValue().rules()) {
                    if (registry.getTag(TagKey.create(Registries.BIOME, rule.tag())).isEmpty())
                        throw new IllegalArgumentException("Missing required biome tag " + rule.tag());
                }
                // 相同优先级只有确实重叠才歧义；枚举注册表覆盖模组群系。
                Map<ResourceLocation,Double> values = new HashMap<>();
                for (var biome : registry.holders().toList()) {
                    Set<Integer> priorities = new HashSet<>();
                    for (var rule : entry.getValue().rules()) if (biome.is(TagKey.create(Registries.BIOME,rule.tag()))
                            && !priorities.add(rule.priority()))
                        throw new IllegalArgumentException("Ambiguous priority " + rule.priority() + " for " + biome.key().location());
                    values.put(biome.key().location(),entry.getValue().rules().stream()
                            .filter(rule -> biome.is(TagKey.create(Registries.BIOME,rule.tag())))
                            .max(Comparator.comparingInt(Rule::priority)).map(Rule::multiplier).orElse(entry.getValue().defaultMultiplier()));
                }
                var profile = entry.getValue();
                var config=yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig.SERVER;
                var typeConfig=profile.type().equals("water")?config.waterDensity:config.windDensity;
                boolean configLoaded=yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceConfig.SERVER_SPEC.isLoaded();
                double capacity=configLoaded?typeConfig.referenceCapacitySU.get():profile.type().equals("water")?4096:6144;
                double radius=configLoaded?typeConfig.collectionRadius.get():16;
                double maximumBiome=Math.max(profile.defaultMultiplier(),profile.rules().stream().mapToDouble(Rule::multiplier).max().orElse(0));
                double maximumDimension=Math.max(1,profile.dimensions().values().stream().mapToDouble(Double::doubleValue).max().orElse(1));
                if(!Double.isFinite(capacity/(Math.PI*radius*radius)*maximumDimension*maximumBiome))
                    throw new IllegalArgumentException("Profile supply density overflows with selected capacity/dimension multiplier");
                resolved.put(entry.getKey(),new Profile(profile.type(),profile.defaultMultiplier(),profile.rules(),
                        profile.dimensions(),Map.copyOf(values)));
            }
            for (var selected : Map.of("water",water,"wind",wind).entrySet()) {
                path = selected.getValue();
                var profile = candidate.get(ResourceLocation.parse(path));
                if (profile == null || !profile.type().equals(selected.getKey()))
                    throw new IllegalArgumentException("Missing or mismatched selected profile");
            }
            profiles = Map.copyOf(resolved);
            version++;
            return true;
        } catch (Exception error) {
            Createkineticinterference.LOGGER.error("Density profile reload rejected at {}; keeping version {}", path, version, error);
            return false;
        }
    }
    /** 集成服务器退出后恢复内置规则，避免下一存档继承上一世界的数据包。 */
    public static void reset() { profiles = defaults(); version = 0; }
}
