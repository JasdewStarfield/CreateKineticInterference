package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.SourceCapacityAdapter;

/** 在共用父类中组合干扰效果，避免与水车/风车附属的同名覆写争用。 */
@Mixin(KineticBlockEntity.class)
public abstract class MixinKineticBlockEntity implements SourceCapacityAdapter.CapacityCache {
    @Shadow protected float lastCapacityProvided;

    // 仅暴露已核对的容量缓存，原始需求采样不得改变网络下一次读到的缓存。
    @Override public float cki$getLastCapacity() { return lastCapacityProvided; }
    @Override public void cki$setLastCapacity(float value) { lastCapacityProvided = value; }

    @Inject(method = "calculateAddedStressCapacity", at = @At("RETURN"), cancellable = true)
    private void kineticInterference$scaleCapacity(CallbackInfoReturnable<Float> cir) {
        if (!SourceCapacityAdapter.sampling() && (Object) this instanceof IKineticInterference source) {
            // Picky Wheels 在 super 返回后继续乘环境倍率；这里仅应用一次干扰系数。
            float capacity = cir.getReturnValueF() * source.getEfficiencyFactor();
            lastCapacityProvided = capacity;
            cir.setReturnValue(capacity);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void kineticInterference$tick(CallbackInfo ci) {
        // Picky Wheels 即使取消风车 tick，仍会调用这条父类路径。
        if ((Object) this instanceof IKineticInterference source) {
            source.tickInterference();
        }
    }
}
