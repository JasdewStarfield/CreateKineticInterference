package yourscraft.jasdewstarfield.createkineticinterference.common.density;

import net.minecraft.nbt.CompoundTag;

/** 服务端提交的只读诊断；输出为绝对 SU，客户端不自行求解密度。 */
public record DensityDiagnostics(boolean enabled, String type, double raw, double output, double localDensity,
                                 double averageSupply, double radius, int sampleY, int estimatedSources,
                                 boolean pending, long version, double baseDensity) {
    public static final DensityDiagnostics EMPTY = new DensityDiagnostics(false,"",0,0,0,0,0,64,0,false,0,0);
    public interface View {
        DensityDiagnostics cki$getDiagnostics();
        void cki$setDiagnostics(DensityDiagnostics data);
    }
    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putBoolean("enabled",enabled); tag.putString("type",type); tag.putDouble("raw",raw);
        tag.putDouble("output",output); tag.putDouble("rho",localDensity); tag.putDouble("averageSupply",averageSupply);
        tag.putDouble("radius",radius); tag.putInt("sampleY",sampleY); tag.putInt("estimated",estimatedSources);
        tag.putBoolean("pending",pending); tag.putLong("version",version); tag.putDouble("baseDensity",baseDensity); return tag;
    }
    public static DensityDiagnostics read(CompoundTag tag) {
        return new DensityDiagnostics(tag.getBoolean("enabled"),tag.getString("type"),tag.getDouble("raw"),
                tag.getDouble("output"),tag.getDouble("rho"),tag.getDouble("averageSupply"),tag.getDouble("radius"),
                tag.getInt("sampleY"),tag.getInt("estimated"),tag.getBoolean("pending"),tag.getLong("version"),tag.getDouble("baseDensity"));
    }
}
