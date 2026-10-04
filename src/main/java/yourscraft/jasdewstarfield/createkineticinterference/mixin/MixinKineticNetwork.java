package yourscraft.jasdewstarfield.createkineticinterference.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.*;

/** Create 在最终 SU 读取时再乘转速；这里把尚未重算的旧系数限制到已提交绝对额度。 */
@Mixin(KineticNetwork.class)
public class MixinKineticNetwork {
    @Inject(method="getActualCapacityOf",at=@At("RETURN"),cancellable=true)
    private void cki$limitPendingCapacity(KineticBlockEntity be,CallbackInfoReturnable<Float> cir) {
        if(!SourceCapacityAdapter.sampling() && be instanceof IKineticInterference source && DensityUpdateScheduler.enabled(source))
            cir.setReturnValue(DensityUpdateScheduler.boundNetworkCapacity(be,cir.getReturnValueF()));
    }
}
