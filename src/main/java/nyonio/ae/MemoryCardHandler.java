package nyonio.ae;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.item.AEItemStack;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import io.github.phantamanta44.libnine.util.world.BlockSide;
import io.github.phantamanta44.threng.tile.base.IAutoExporting;
import io.github.phantamanta44.threng.tile.base.TileSimpleProcessor;
import io.github.phantamanta44.threng.util.SlotType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

public class MemoryCardHandler {

    private static final boolean HAS_BAUBLES = Loader.isModLoaded("baubles");

    private static final String DATA_AUTO_EXPORT = "lazy_autoExport";
    private static final String DATA_SPEED_CARDS = "lazy_speedCards";
    private static final String DATA_SIDE_IO = "lazy_sideIO";
    private static final String SETTINGS_NAME = "lazy_energy.machine_config";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote) return;

        EntityPlayer player = event.getEntityPlayer();
        ItemStack heldItem = player.getHeldItem(event.getHand());

        TileEntity te = event.getWorld().getTileEntity(event.getPos());
        if (!(te instanceof TileSimpleProcessor)) return;

        if (player.isSneaking() && isSpeedCard(heldItem)) {
            event.setCanceled(true);
            insertSpeedCard((TileSimpleProcessor<?, ?, ?, ?, ?>) te, heldItem, player);
            return;
        }

        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) return;

        event.setCanceled(true);

        IMemoryCard memoryCard = (IMemoryCard) heldItem.getItem();
        TileSimpleProcessor<?, ?, ?, ?, ?> tile = (TileSimpleProcessor<?, ?, ?, ?, ?>) te;

        if (player.isSneaking()) {
            copyConfig(memoryCard, heldItem, tile, player);
        } else {
            pasteConfig(memoryCard, heldItem, tile, player);
        }
    }

    private static boolean isSpeedCard(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemStack template = AEApi.instance().definitions().materials().cardSpeed().maybeStack(1).orElse(ItemStack.EMPTY);
        if (template.isEmpty()) return false;
        return stack.getItem() == template.getItem() && stack.getMetadata() == template.getMetadata();
    }

    private static void insertSpeedCard(TileSimpleProcessor<?, ?, ?, ?, ?> tile, ItemStack heldItem, EntityPlayer player) {
        IItemHandler upgradeSlot = tile.getUpgradeSlot();
        ItemStack current = upgradeSlot.getStackInSlot(0);
        int currentCount = current.isEmpty() ? 0 : current.getCount();
        int maxSlots = upgradeSlot.getSlotLimit(0);
        int canInsert = maxSlots - currentCount;
        if (canInsert <= 0) return;

        int toInsert = Math.min(canInsert, heldItem.getCount());
        ItemStack toInsertStack = heldItem.copy();
        toInsertStack.setCount(toInsert);

        ItemStack leftover = upgradeSlot.insertItem(0, toInsertStack, false);
        int actuallyInserted = toInsert - (leftover.isEmpty() ? 0 : leftover.getCount());

        heldItem.shrink(actuallyInserted);
    }

    private static void copyConfig(IMemoryCard memoryCard, ItemStack card,
                                   TileSimpleProcessor<?, ?, ?, ?, ?> tile, EntityPlayer player) {
        NBTTagCompound data = new NBTTagCompound();

        if (tile instanceof IAutoExporting) {
            data.setBoolean(DATA_AUTO_EXPORT, ((IAutoExporting) tile).isAutoExporting());
        }

        ItemStack upgradeStack = tile.getUpgradeSlot().getStackInSlot(0);
        int speedCardCount = upgradeStack.isEmpty() ? 0 : upgradeStack.getCount();
        data.setInteger(DATA_SPEED_CARDS, speedCardCount);

        int[] sideIO = new int[BlockSide.values().length];
        for (int i = 0; i < BlockSide.values().length; i++) {
            sideIO[i] = tile.getFace(BlockSide.values()[i]).ordinal();
        }
        data.setIntArray(DATA_SIDE_IO, sideIO);

        memoryCard.setMemoryCardContents(card, SETTINGS_NAME, data);
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
    }

    private static void pasteConfig(IMemoryCard memoryCard, ItemStack card,
                                    TileSimpleProcessor<?, ?, ?, ?, ?> tile, EntityPlayer player) {
        NBTTagCompound data = memoryCard.getData(card);
        if (data.hasNoTags()) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }

        if (data.hasKey(DATA_AUTO_EXPORT) && tile instanceof IAutoExporting) {
            ((IAutoExporting) tile).setAutoExporting(data.getBoolean(DATA_AUTO_EXPORT));
        }

        if (data.hasKey(DATA_SPEED_CARDS)) {
            int targetCount = data.getInteger(DATA_SPEED_CARDS);
            adjustSpeedCards(tile, targetCount, player);
        }

        if (data.hasKey(DATA_SIDE_IO)) {
            int[] sideIO = data.getIntArray(DATA_SIDE_IO);
            BlockSide[] sides = BlockSide.values();
            for (int i = 0; i < sides.length && i < sideIO.length; i++) {
                SlotType.BasicIO io = SlotType.BasicIO.get(sideIO[i]);
                tile.setFace(sides[i], io);
            }
        }

        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
    }

    private static void adjustSpeedCards(TileSimpleProcessor<?, ?, ?, ?, ?> tile,
                                         int targetCount, EntityPlayer player) {
        ItemStack upgradeStack = tile.getUpgradeSlot().getStackInSlot(0);
        int currentCount = upgradeStack.isEmpty() ? 0 : upgradeStack.getCount();

        if (currentCount == targetCount) return;

        ItemStack speedCardTemplate = getSpeedCardTemplate();
        if (speedCardTemplate.isEmpty()) return;

        if (targetCount > currentCount) {
            int needed = targetCount - currentCount;
            ItemStack extracted = extractSpeedCardsFromSource(player, speedCardTemplate, needed);
            if (extracted.isEmpty()) return;

            ItemStack current = tile.getUpgradeSlot().getStackInSlot(0);
            if (current.isEmpty()) {
                tile.getUpgradeSlot().setStackInSlot(0, extracted);
            } else {
                current.grow(extracted.getCount());
            }
        } else {
            int toRemove = currentCount - targetCount;
            ItemStack current = tile.getUpgradeSlot().getStackInSlot(0);
            ItemStack removed = current.splitStack(toRemove);
            if (!removed.isEmpty()) {
                insertSpeedCardsToDestination(player, removed);
            }
            if (current.isEmpty()) {
                tile.getUpgradeSlot().setStackInSlot(0, ItemStack.EMPTY);
            }
        }
    }

    private static ItemStack extractSpeedCardsFromSource(EntityPlayer player, ItemStack template, int needed) {
        WirelessTerminalGuiObject wTerminal = findWirelessTerminal(player);
        if (wTerminal != null) {
            ItemStack result = extractSpeedCardsFromNetwork(wTerminal, player, template, needed);
            if (!result.isEmpty()) return result;
        }
        return extractSpeedCardsFromInventory(player, template, needed);
    }

    private static void insertSpeedCardsToDestination(EntityPlayer player, ItemStack cards) {
        WirelessTerminalGuiObject wTerminal = findWirelessTerminal(player);
        if (wTerminal != null) {
            insertSpeedCardsToNetwork(wTerminal, player, cards);
            return;
        }
        player.inventory.placeItemBackInInventory(player.world, cards);
    }

    @Nullable
    private static WirelessTerminalGuiObject findWirelessTerminal(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack item = player.inventory.getStackInSlot(i);
            WirelessTerminalGuiObject obj = tryCreateWirelessTerminal(item, player, i, 0);
            if (obj != null) return obj;
        }

        if (HAS_BAUBLES) {
            try {
                IBaublesItemHandler baublesHandler = BaublesApi.getBaublesHandler(player);
                for (int i = 0; i < baublesHandler.getSlots(); i++) {
                    ItemStack item = baublesHandler.getStackInSlot(i);
                    WirelessTerminalGuiObject obj = tryCreateWirelessTerminal(item, player, i, 1);
                    if (obj != null) return obj;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    @Nullable
    private static WirelessTerminalGuiObject tryCreateWirelessTerminal(ItemStack item, EntityPlayer player,
                                                                        int slot, int isBauble) {
        if (item.isEmpty() || !(item.getItem() instanceof IWirelessTermHandler)) return null;
        IWirelessTermHandler handler = (IWirelessTermHandler) item.getItem();
        if (!handler.canHandle(item)) return null;
        try {
            WirelessTerminalGuiObject obj = new WirelessTerminalGuiObject(
                    handler, item, player, player.world, slot, isBauble, 0);
            if (obj.rangeCheck()) return obj;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ItemStack extractSpeedCardsFromNetwork(WirelessTerminalGuiObject wTerminal,
                                                          EntityPlayer player, ItemStack template, int needed) {
        try {
            IGridNode node = wTerminal.getActionableNode();
            if (node == null) return ItemStack.EMPTY;

            IGrid grid = node.getGrid();
            if (grid == null) return ItemStack.EMPTY;

            ISecurityGrid security = grid.getCache(ISecurityGrid.class);
            if (!security.hasPermission(player, SecurityPermissions.EXTRACT)) return ItemStack.EMPTY;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            IAEItemStack request = AEItemStack.fromItemStack(template);
            request.setStackSize(needed);

            IAEItemStack extracted = itemMonitor.extractItems(request, Actionable.MODULATE,
                    new appeng.me.helpers.PlayerSource(player, wTerminal));
            if (extracted == null || extracted.getStackSize() <= 0) return ItemStack.EMPTY;

            ItemStack result = extracted.createItemStack();
            result.setCount((int) extracted.getStackSize());

            if (extracted.getStackSize() < needed) {
                int remaining = needed - (int) extracted.getStackSize();
                ItemStack fromInv = extractSpeedCardsFromInventory(player, template, remaining);
                if (!fromInv.isEmpty()) {
                    result.grow(fromInv.getCount());
                }
            }

            return result;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void insertSpeedCardsToNetwork(WirelessTerminalGuiObject wTerminal,
                                                   EntityPlayer player, ItemStack cards) {
        try {
            IGridNode node = wTerminal.getActionableNode();
            if (node == null) {
                player.inventory.placeItemBackInInventory(player.world, cards);
                return;
            }

            IGrid grid = node.getGrid();
            if (grid == null) {
                player.inventory.placeItemBackInInventory(player.world, cards);
                return;
            }

            ISecurityGrid security = grid.getCache(ISecurityGrid.class);
            if (!security.hasPermission(player, SecurityPermissions.INJECT)) {
                player.inventory.placeItemBackInInventory(player.world, cards);
                return;
            }

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            IAEItemStack aeStack = AEItemStack.fromItemStack(cards);
            IAEItemStack leftover = itemMonitor.injectItems(aeStack, Actionable.MODULATE,
                    new appeng.me.helpers.PlayerSource(player, wTerminal));

            if (leftover != null && leftover.getStackSize() > 0) {
                player.inventory.placeItemBackInInventory(player.world, leftover.createItemStack());
            }
        } catch (Exception e) {
            player.inventory.placeItemBackInInventory(player.world, cards);
        }
    }

    private static ItemStack extractSpeedCardsFromInventory(EntityPlayer player,
                                                            ItemStack template, int needed) {
        int extracted = 0;
        ItemStack result = ItemStack.EMPTY;

        for (int i = 0; i < player.inventory.getSizeInventory() && extracted < needed; i++) {
            ItemStack slotStack = player.inventory.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;
            if (!isSameItem(slotStack, template)) continue;

            int toTake = Math.min(needed - extracted, slotStack.getCount());
            if (result.isEmpty()) {
                result = new ItemStack(slotStack.getItem(), toTake, slotStack.getMetadata());
                if (slotStack.hasTagCompound()) {
                    result.setTagCompound(slotStack.getTagCompound().copy());
                }
            } else {
                result.grow(toTake);
            }
            slotStack.shrink(toTake);
            if (slotStack.isEmpty()) {
                player.inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            }
            extracted += toTake;
        }

        return result;
    }

    private static boolean isSameItem(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem()) return false;
        if (a.getMetadata() != b.getMetadata()) return false;
        return ItemStack.areItemStackTagsEqual(a, b);
    }

    private static ItemStack getSpeedCardTemplate() {
        return AEApi.instance().definitions().materials().cardSpeed().maybeStack(1).orElse(ItemStack.EMPTY);
    }
}
