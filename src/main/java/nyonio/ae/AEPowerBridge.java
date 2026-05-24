package nyonio.ae;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import io.github.phantamanta44.threng.tile.base.IAutoExporting;
import io.github.phantamanta44.threng.tile.base.TileMachine;
import io.github.phantamanta44.threng.tile.base.TileSimpleProcessor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import nyonio.config.LazyEnergyConfig;

public class AEPowerBridge {

    public static void onTick(TileMachine machine) {
        if (machine.getWorld().isRemote) return;

        extractAEPower(machine);

        if (machine instanceof IAutoExporting && ((IAutoExporting) machine).isAutoExporting()) {
            autoExportToNetwork(machine);
        }
    }

    private static void extractAEPower(TileMachine machine) {
        IEnergyStorage energy = machine.getCapability(CapabilityEnergy.ENERGY, null);
        if (energy == null || !energy.canReceive()) return;

        ProxyHolder.LazyEnergyProxy proxy = ProxyHolder.getOrCreateProxy(machine);
        if (proxy == null || !proxy.isReady()) return;

        int feNeeded = energy.getMaxEnergyStored() - energy.getEnergyStored();
        if (feNeeded <= 0) return;

        IGridNode node = proxy.getProxy().getNode();
        if (node == null || !node.isActive()) return;

        IGrid grid = node.getGrid();
        if (grid == null) return;

        IEnergyGrid energyGrid = grid.getCache(IEnergyGrid.class);
        if (energyGrid == null || !energyGrid.isNetworkPowered()) return;

        int maxFePerTick = LazyEnergyConfig.maxFEPerTick;
        if (maxFePerTick > 0 && feNeeded > maxFePerTick) {
            feNeeded = maxFePerTick;
        }

        double aeToExtract = feNeeded / LazyEnergyConfig.fePerAE;
        double aeExtracted = energyGrid.extractAEPower(aeToExtract, Actionable.MODULATE, PowerMultiplier.ONE);

        if (aeExtracted > 0) {
            int feGained = (int) Math.floor(aeExtracted * LazyEnergyConfig.fePerAE);
            if (feGained > 0) {
                energy.receiveEnergy(feGained, false);
            }
        }
    }

    private static void autoExportToNetwork(TileMachine machine) {
        if (!(machine instanceof TileSimpleProcessor)) return;

        ProxyHolder.LazyEnergyProxy proxy = ProxyHolder.getOrCreateProxy(machine);
        if (proxy == null || !proxy.isReady()) return;

        IGridNode node = proxy.getProxy().getNode();
        if (node == null || !node.isActive()) return;

        IGrid grid = node.getGrid();
        if (grid == null) return;

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return;

        IItemHandler outputHandler = ProxyHolder.getOutputHandler(machine);
        if (outputHandler == null) return;

        MachineSource source = new MachineSource(proxy);
        IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        for (int slot = 0; slot < outputHandler.getSlots(); slot++) {
            ItemStack stack = outputHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
            IAEItemStack notInserted = itemMonitor.injectItems(aeStack, Actionable.MODULATE, source);
            if (notInserted == null || notInserted.getStackSize() == 0) {
                outputHandler.extractItem(slot, stack.getCount(), false);
            } else {
                int inserted = (int) (stack.getCount() - notInserted.getStackSize());
                if (inserted > 0) {
                    outputHandler.extractItem(slot, inserted, false);
                }
            }
        }
    }
}
