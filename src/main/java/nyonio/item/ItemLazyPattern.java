package nyonio.item;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import nyonio.LazyEnergy;
import nyonio.ae.LazyEnergyPatternDetails;

import java.util.List;

public class ItemLazyPattern extends Item implements ICraftingPatternItem {

    public ItemLazyPattern() {
        setMaxStackSize(1);
        setUnlocalizedName(LazyEnergy.MODID + ".lazy_pattern");
        setRegistryName(LazyEnergy.MODID, "lazy_pattern");
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack is, World w) {
        if (!is.hasTagCompound()) return null;
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null || !tag.hasKey("pattern")) return null;
        return LazyEnergyPatternDetails.fromNBT(tag.getCompoundTag("pattern"));
    }

    public static ItemStack createPatternStack(ICraftingPatternDetails details) {
        if (!(details instanceof LazyEnergyPatternDetails)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(LazyEnergy.LAZY_PATTERN);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("pattern", ((LazyEnergyPatternDetails) details).toNBT());
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        ICraftingPatternDetails details = getPatternForItem(stack, worldIn);
        if (details != null) {
            tooltip.add("\u00a77Lazy AE Pattern");
        }
    }
}
