package nyonio.ae;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import io.github.phantamanta44.libnine.LibNine;
import io.github.phantamanta44.libnine.recipe.IRcp;
import io.github.phantamanta44.threng.recipe.AggRecipe;
import io.github.phantamanta44.threng.recipe.EnergizeRecipe;
import io.github.phantamanta44.threng.recipe.EtchRecipe;
import io.github.phantamanta44.threng.recipe.PurifyRecipe;
import io.github.phantamanta44.threng.tile.TileAggregator;
import io.github.phantamanta44.threng.tile.TileCentrifuge;
import io.github.phantamanta44.threng.tile.TileEnergizer;
import io.github.phantamanta44.threng.tile.TileEtcher;
import io.github.phantamanta44.threng.tile.base.TilePowered;
import io.github.phantamanta44.threng.tile.base.TileSimpleProcessor;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class ProxyHolder {

    private static final Map<TileEntity, LazyEnergyProxy> proxies = new WeakHashMap<>();

    @Nullable
    public static IGridNode getGridNode(TileEntity te, AEPartLocation dir) {
        LazyEnergyProxy proxy = getOrCreateProxy(te);
        if (proxy != null) {
            return proxy.getGridNode(dir);
        }
        return null;
    }

    @Nullable
    public static LazyEnergyProxy getOrCreateProxy(TileEntity te) {
        if (!(te instanceof TilePowered) || te.isInvalid()) return null;
        LazyEnergyProxy proxy = proxies.get(te);
        if (proxy == null) {
            proxy = new LazyEnergyProxy((TilePowered) te);
            proxies.put(te, proxy);
        }
        return proxy;
    }

    public static void removeProxy(TileEntity te) {
        LazyEnergyProxy proxy = proxies.remove(te);
        if (proxy != null) {
            proxy.invalidate();
        }
    }

    public static Iterable<Map.Entry<TileEntity, LazyEnergyProxy>> getProxyEntries() {
        return proxies.entrySet();
    }

    @Nullable
    private static Collection<? extends IRcp<?, ?, ?>> getRecipesForTile(TileEntity te) {
        if (te instanceof TileAggregator) return LibNine.PROXY.getRecipeManager().getRecipeList(AggRecipe.class).recipes();
        if (te instanceof TileCentrifuge) return LibNine.PROXY.getRecipeManager().getRecipeList(PurifyRecipe.class).recipes();
        if (te instanceof TileEnergizer) return LibNine.PROXY.getRecipeManager().getRecipeList(EnergizeRecipe.class).recipes();
        if (te instanceof TileEtcher) return LibNine.PROXY.getRecipeManager().getRecipeList(EtchRecipe.class).recipes();
        return null;
    }

    @Nullable
    public static IItemHandler getInputHandler(TileEntity te) {
        if (te instanceof TileAggregator) return ((TileAggregator) te).getInputSlots();
        if (te instanceof TileEtcher) return ((TileEtcher) te).getInputSlots();
        if (te instanceof TileCentrifuge) return ((TileCentrifuge) te).getInputSlot();
        if (te instanceof TileEnergizer) return ((TileEnergizer) te).getInputSlot();
        return null;
    }

    @Nullable
    public static IItemHandler getOutputHandler(TileEntity te) {
        if (te instanceof TileAggregator) return ((TileAggregator) te).getOutputSlot();
        if (te instanceof TileEtcher) return ((TileEtcher) te).getOutputSlot();
        if (te instanceof TileCentrifuge) return ((TileCentrifuge) te).getOutputSlot();
        if (te instanceof TileEnergizer) return ((TileEnergizer) te).getOutputSlot();
        return null;
    }

    public static class LazyEnergyProxy implements IGridProxyable, IActionHost, ICraftingProvider {

        private final TilePowered tile;
        private AENetworkProxy aeProxy;
        private boolean validated;
        private boolean ready;
        private boolean speedCardsExtracted;
        private int speedCardWaitTicks;

        public LazyEnergyProxy(TilePowered tile) {
            this.tile = tile;
        }

        private AENetworkProxy getAeProxy() {
            if (aeProxy == null) {
                aeProxy = new AENetworkProxy(this, "lazyenergy_aeproxy", ItemStack.EMPTY, true);
                aeProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
                aeProxy.setIdlePowerUsage(0);
            }
            return aeProxy;
        }

        @Override
        public AENetworkProxy getProxy() {
            return getAeProxy();
        }

        @Nullable
        @Override
        public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
            ensureReady();
            return getAeProxy().getNode();
        }

        @Nonnull
        @Override
        public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
            return AECableType.SMART;
        }

        @Nonnull
        @Override
        public DimensionalCoord getLocation() {
            return new DimensionalCoord(tile);
        }

        @Override
        public void gridChanged() {
        }

        @Override
        public void securityBreak() {
            tile.getWorld().destroyBlock(tile.getPos(), true);
        }

        @Nullable
        @Override
        public IGridNode getActionableNode() {
            ensureReady();
            return getAeProxy().getNode();
        }

        public void validate() {
            if (!validated) {
                getAeProxy().validate();
                validated = true;
            }
        }

        public void onReady() {
            if (validated && !ready && !tile.isInvalid()) {
                getAeProxy().onReady();
                ready = true;
            }
        }

        private void ensureReady() {
            if (ready) return;
            if (tile.getWorld() == null || tile.isInvalid()) return;
            if (!validated) {
                validate();
            }
            if (!ready) {
                onReady();
            }
        }

        public void tryExtractSpeedCards() {
            if (speedCardsExtracted) return;
            if (!(tile instanceof TileSimpleProcessor)) return;

            speedCardWaitTicks++;
            if (speedCardWaitTicks > 60) {
                speedCardsExtracted = true;
                return;
            }

            IGridNode node = getProxy().getNode();
            if (node == null || !node.isActive()) return;

            speedCardsExtracted = true;
            SpeedCardExtractor.extractSpeedCards((TileSimpleProcessor<?, ?, ?, ?, ?>) tile);
        }

        public void invalidate() {
            if (validated) {
                getAeProxy().invalidate();
                validated = false;
                ready = false;
            }
        }

        public void onChunkUnload() {
            if (validated) {
                getAeProxy().onChunkUnload();
            }
        }

        public boolean isValidated() {
            return validated;
        }

        public boolean isReady() {
            return ready;
        }

        @Override
        public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
            if (!(tile instanceof TileSimpleProcessor)) return false;

            IGridNode node = getProxy().getNode();
            if (node == null || !node.isActive()) return false;

            IGrid grid = node.getGrid();
            if (grid == null) return false;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return false;

            IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (itemMonitor == null) return false;

            MachineSource source = new MachineSource(this);

            IAEItemStack[] requiredInputs = patternDetails.getCondensedInputs();
            if (requiredInputs == null || requiredInputs.length == 0) return false;

            for (IAEItemStack aeInput : requiredInputs) {
                if (aeInput == null) continue;
                IAEItemStack simulated = itemMonitor.extractItems(aeInput.copy(), Actionable.SIMULATE, source);
                if (simulated == null || simulated.getStackSize() < aeInput.getStackSize()) return false;
            }

            IItemHandler inputHandler = getInputHandler(tile);
            if (inputHandler == null) return false;

            List<ItemStack> extractedItems = new ArrayList<>();
            for (IAEItemStack aeInput : requiredInputs) {
                if (aeInput == null) continue;
                IAEItemStack extracted = itemMonitor.extractItems(aeInput.copy(), Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    extractedItems.add(extracted.createItemStack());
                }
            }

            boolean success = true;
            List<ItemStack> leftovers = new ArrayList<>();
            for (ItemStack stack : extractedItems) {
                ItemStack leftover = stack;
                for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
                    leftover = inputHandler.insertItem(slot, leftover, false);
                    if (leftover.isEmpty()) break;
                }
                if (!leftover.isEmpty()) {
                    success = false;
                    leftovers.add(leftover);
                }
            }

            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers) {
                    IAEItemStack aeLeftover = AEItemStack.fromItemStack(leftover);
                    itemMonitor.injectItems(aeLeftover, Actionable.MODULATE, source);
                }
            }

            return success;
        }

        @Override
        public boolean isBusy() {
            if (!(tile instanceof TileSimpleProcessor)) return true;
            IItemHandler inputHandler = getInputHandler(tile);
            if (inputHandler == null) return true;
            for (int i = 0; i < inputHandler.getSlots(); i++) {
                if (inputHandler.getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public void provideCrafting(ICraftingProviderHelper helper) {
            if (!(tile instanceof TileSimpleProcessor)) return;

            Collection<? extends IRcp<?, ?, ?>> recipes = getRecipesForTile(tile);
            if (recipes == null) return;

            for (IRcp<?, ?, ?> recipe : recipes) {
                LazyEnergyPatternDetails details = LazyEnergyPatternDetails.fromRecipe(recipe);
                if (details != null) {
                    helper.addCraftingOption(this, details);
                }
            }
        }
    }
}
