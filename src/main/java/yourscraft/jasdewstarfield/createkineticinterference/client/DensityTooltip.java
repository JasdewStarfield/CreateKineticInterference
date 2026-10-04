package yourscraft.jasdewstarfield.createkineticinterference.client;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensityDiagnostics;
import java.util.List;

/** 护目镜只展示同步的服务端数值；原始容量合并到 Create 产出行，其余诊断独立显示。 */
public final class DensityTooltip {
    public static boolean append(IKineticInterference source,List<Component> tooltip,boolean sneaking) {
        var data = ((DensityDiagnostics.View)source).cki$getDiagnostics();
        if (!data.enabled()) return false;
        if (data.raw()>0) {
            line(tooltip,"hint.density.efficiency",source.getEfficiencyFactor()*100,"%");
            // 用基础的倍数说明当地条件；首次分配前没有有效基础值时等待同步。
            if (data.baseDensity()>0) CreateLang.translate("hint.density.conditions").style(ChatFormatting.GRAY)
                    .add(CreateLang.translate("hint.density.baseline_multiplier",
                            CreateLang.number(data.localDensity()/data.baseDensity()).component()).style(ChatFormatting.GOLD))
                    .forGoggles(tooltip);
        }
        if (data.pending()) CreateLang.translate("hint.density.pending").style(ChatFormatting.GOLD).forGoggles(tooltip);
        // 停转时隐藏效率、当地条件和旧覆盖统计，等待再次运行后更新。
        if (data.raw()==0) return true;
        if (sneaking) {
            CreateLang.translate("hint.density.type."+data.type()).style(ChatFormatting.GRAY).forGoggles(tooltip);
            line(tooltip,"hint.density.local",data.localDensity()," SU/m²");
            line(tooltip,"hint.density.average",data.averageSupply()," SU/m²");
            line(tooltip,"hint.density.radius",data.radius()," m");
            line(tooltip,"hint.density.height",data.sampleY(),"");
            line(tooltip,"hint.density.competitors",source.getNearbyCount(),"");
            if (data.estimatedSources()>0) line(tooltip,"hint.density.estimated",data.estimatedSources(),"");
            if (source.getNearbyCount()>64) CreateLang.translate("hint.density.truncated",64)
                    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip);
        }
        return true;
    }
    private static void line(List<Component> tooltip,String key,double value,String unit) {
        CreateLang.translate(key).style(ChatFormatting.GRAY)
                .add(CreateLang.number(value).text(unit).style(ChatFormatting.GOLD)).forGoggles(tooltip);
    }
}
