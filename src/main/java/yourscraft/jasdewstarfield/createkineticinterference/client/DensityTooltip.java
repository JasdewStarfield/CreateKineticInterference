package yourscraft.jasdewstarfield.createkineticinterference.client;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.createkineticinterference.common.IKineticInterference;
import yourscraft.jasdewstarfield.createkineticinterference.common.density.DensityDiagnostics;
import java.util.List;

/** 护目镜只展示同步的服务端数值；每个量独立成行，便于不同语言与缩放阅读。 */
public final class DensityTooltip {
    public static boolean append(IKineticInterference source,List<Component> tooltip,boolean sneaking) {
        var data = ((DensityDiagnostics.View)source).cki$getDiagnostics();
        if (!data.enabled()) return false;
        if(data.raw()==0)CreateLang.translate("hint.density.inactive").style(ChatFormatting.GRAY).forGoggles(tooltip);
        else line(tooltip,"hint.density.efficiency",source.getEfficiencyFactor()*100,"%");
        CreateLang.translate("hint.density.output").style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.number(data.output()).text(" / ").add(CreateLang.number(data.raw())).text(" SU")
                .style(ChatFormatting.AQUA).forGoggles(tooltip,1);
        if (data.pending()) CreateLang.translate("hint.density.pending").style(ChatFormatting.GOLD).forGoggles(tooltip);
        // 停转时没有覆盖分配，保留 N/A 与零输出，旧覆盖统计等待再次运行后更新。
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
