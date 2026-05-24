package nyonio.ae;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import io.github.phantamanta44.threng.tile.base.TileSimpleProcessor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class SpeedCardExtractor {

    public static void extractSpeedCards(TileSimpleProcessor<?, ?, ?, ?, ?> tile) {
        ProxyHolder.LazyEnergyProxy proxy = ProxyHolder.getOrCreateProxy(tile);
        if (proxy == null || !proxy.isReady()) return;

        IGridNode node = proxy.getProxy().getNode();
        if (node == null || !node.isActive()) return;

        IGrid grid = node.getGrid();
        if (grid == null) return;

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return;

        ItemStack speedCardTemplate = getSpeedCardTemplate();
        if (speedCardTemplate.isEmpty()) return;

        IItemHandler upgradeSlot = tile.getUpgradeSlot();
        if (upgradeSlot == null) return;

        ItemStack currentStack = upgradeSlot.getStackInSlot(0);
        int currentCount = currentStack.isEmpty() ? 0 : currentStack.getCount();
        int needed = 8 - currentCount;
        if (needed <= 0) return;

        IAEItemStack request = AEItemStack.fromItemStack(speedCardTemplate);
        request.setStackSize(needed);

        IActionSource source = new MachineSource(proxy);

        IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        IAEItemStack extracted = itemMonitor.extractItems(request, Actionable.MODULATE, source);
        if (extracted == null || extracted.getStackSize() <= 0) return;

        ItemStack toInsert = extracted.createItemStack();
        toInsert.setCount((int) extracted.getStackSize());

        ItemStack leftover = upgradeSlot.insertItem(0, toInsert, false);

        if (!leftover.isEmpty() && leftover.getCount() > 0) {
            IAEItemStack returnStack = AEItemStack.fromItemStack(leftover);
            itemMonitor.injectItems(returnStack, Actionable.MODULATE, source);
        }
    }

    private static ItemStack getSpeedCardTemplate() {
        return AEApi.instance().definitions().materials().cardSpeed().maybeStack(1).orElse(ItemStack.EMPTY);
    }
}
