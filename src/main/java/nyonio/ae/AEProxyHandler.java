package nyonio.ae;

import appeng.util.Platform;
import io.github.phantamanta44.threng.tile.base.TilePowered;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AEProxyHandler {

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;

        List<TileEntity> toRemove = new ArrayList<>();
        for (Map.Entry<TileEntity, ProxyHolder.LazyEnergyProxy> entry : ProxyHolder.getProxyEntries()) {
            TileEntity te = entry.getKey();
            if (te.isInvalid()) {
                entry.getValue().invalidate();
                toRemove.add(te);
            }
        }
        for (TileEntity te : toRemove) {
            ProxyHolder.removeProxy(te);
        }

        for (TileEntity te : event.world.loadedTileEntityList) {
            if (te instanceof TilePowered && !te.isInvalid()) {
                ProxyHolder.LazyEnergyProxy proxy = ProxyHolder.getOrCreateProxy(te);
                if (proxy == null) continue;

                if (!proxy.isValidated()) {
                    proxy.validate();
                }
                if (proxy.isValidated() && !proxy.isReady()) {
                    proxy.onReady();
                    Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());
                }
                if (proxy.isReady()) {
                    proxy.tryExtractSpeedCards();
                }
            }
        }
    }
}
