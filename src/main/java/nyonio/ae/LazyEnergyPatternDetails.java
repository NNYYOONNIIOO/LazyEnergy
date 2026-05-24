package nyonio.ae;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import io.github.phantamanta44.libnine.recipe.IRcp;
import io.github.phantamanta44.libnine.recipe.input.IRcpIn;
import io.github.phantamanta44.libnine.recipe.input.ItemStackInput;
import io.github.phantamanta44.libnine.recipe.output.IRcpOut;
import io.github.phantamanta44.libnine.util.IDisplayableMatcher;
import io.github.phantamanta44.libnine.recipe.output.ItemStackOutput;
import io.github.phantamanta44.threng.recipe.component.ItemEnergyInput;
import io.github.phantamanta44.threng.recipe.component.TriItemInput;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import nyonio.item.ItemLazyPattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class LazyEnergyPatternDetails implements ICraftingPatternDetails {

    private final IAEItemStack[] inputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedOutputs;
    private int priority = 0;

    private LazyEnergyPatternDetails(IAEItemStack[] inputs, IAEItemStack[] outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.condensedInputs = condense(inputs);
        this.condensedOutputs = condense(outputs);
    }

    @Nullable
    public static LazyEnergyPatternDetails fromRecipe(IRcp<?, ?, ?> recipe) {
        try {
            List<IAEItemStack> inputList = new ArrayList<>();
            List<IAEItemStack> outputList = new ArrayList<>();

            IRcpIn<?> recipeInput = recipe.input();
            if (recipeInput instanceof TriItemInput) {
                TriItemInput triInput = (TriItemInput) recipeInput;
                for (IDisplayableMatcher<ItemStack> matcher : triInput.getInputs()) {
                    ItemStack visual = matcher.getVisual();
                    if (visual != null && !visual.isEmpty()) {
                        ItemStack copy = visual.copy();
                        copy.setCount(1);
                        inputList.add(AEItemStack.fromItemStack(copy));
                    } else {
                        inputList.add(null);
                    }
                }
            } else if (recipeInput instanceof ItemStackInput) {
                ItemStackInput itemInput = (ItemStackInput) recipeInput;
                ItemStack visual = itemInput.getMatcher().getVisual();
                if (visual != null && !visual.isEmpty()) {
                    ItemStack copy = visual.copy();
                    copy.setCount(1);
                    inputList.add(AEItemStack.fromItemStack(copy));
                } else {
                    return null;
                }
            } else if (recipeInput instanceof ItemEnergyInput) {
                ItemEnergyInput energyInput = (ItemEnergyInput) recipeInput;
                ItemStack visual = energyInput.getMatcher().getVisual();
                if (visual != null && !visual.isEmpty()) {
                    ItemStack copy = visual.copy();
                    copy.setCount(1);
                    inputList.add(AEItemStack.fromItemStack(copy));
                } else {
                    return null;
                }
            } else {
                return null;
            }

            IRcpOut<?> recipeOutput = recipe.mapToOutput(null);
            if (recipeOutput instanceof ItemStackOutput) {
                ItemStack outputStack = ((ItemStackOutput) recipeOutput).getOutput();
                if (!outputStack.isEmpty()) {
                    outputList.add(AEItemStack.fromItemStack(outputStack));
                }
            } else {
                return null;
            }

            if (inputList.isEmpty() || outputList.isEmpty()) return null;

            IAEItemStack[] inputs = new IAEItemStack[16];
            for (int i = 0; i < inputList.size() && i < 16; i++) {
                inputs[i] = inputList.get(i);
            }
            IAEItemStack[] outputs = outputList.toArray(new IAEItemStack[0]);

            return new LazyEnergyPatternDetails(inputs, outputs);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static LazyEnergyPatternDetails fromNBT(NBTTagCompound tag) {
        try {
            IAEItemStack[] inputs = new IAEItemStack[16];
            NBTTagList inputList = tag.getTagList("inputs", 10);
            for (int i = 0; i < inputList.tagCount() && i < 16; i++) {
                NBTTagCompound itemTag = inputList.getCompoundTagAt(i);
                if (itemTag.hasNoTags()) {
                    inputs[i] = null;
                } else {
                    inputs[i] = AEItemStack.fromNBT(itemTag);
                }
            }

            List<IAEItemStack> outputList = new ArrayList<>();
            NBTTagList outputTagList = tag.getTagList("outputs", 10);
            for (int i = 0; i < outputTagList.tagCount(); i++) {
                IAEItemStack aeStack = AEItemStack.fromNBT(outputTagList.getCompoundTagAt(i));
                if (aeStack != null) {
                    outputList.add(aeStack);
                }
            }

            if (outputList.isEmpty()) return null;

            IAEItemStack[] outputs = outputList.toArray(new IAEItemStack[0]);
            LazyEnergyPatternDetails details = new LazyEnergyPatternDetails(inputs, outputs);
            if (tag.hasKey("priority")) {
                details.priority = tag.getInteger("priority");
            }
            return details;
        } catch (Exception e) {
            return null;
        }
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList inputList = new NBTTagList();
        for (IAEItemStack input : inputs) {
            if (input != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                input.writeToNBT(itemTag);
                inputList.appendTag(itemTag);
            } else {
                inputList.appendTag(new NBTTagCompound());
            }
        }
        tag.setTag("inputs", inputList);

        NBTTagList outputList = new NBTTagList();
        for (IAEItemStack output : outputs) {
            if (output != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                output.writeToNBT(itemTag);
                outputList.appendTag(itemTag);
            }
        }
        tag.setTag("outputs", outputList);

        tag.setInteger("priority", priority);

        return tag;
    }

    private static IAEItemStack[] condense(IAEItemStack[] stacks) {
        Map<IAEItemStack, IAEItemStack> map = new LinkedHashMap<>();
        for (IAEItemStack stack : stacks) {
            if (stack == null) continue;
            IAEItemStack existing = map.get(stack);
            if (existing == null) {
                map.put(stack, stack.copy());
            } else {
                existing.add(stack);
            }
        }
        return map.values().toArray(new IAEItemStack[0]);
    }

    @Override
    public ItemStack getPattern() {
        return ItemLazyPattern.createPatternStack(this);
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        if (slotIndex < 0 || slotIndex >= inputs.length) return false;
        IAEItemStack input = inputs[slotIndex];
        if (input == null) return itemStack.isEmpty();
        return input.isSameType(AEItemStack.fromItemStack(itemStack));
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return condensedInputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return condensedOutputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return outputs;
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return outputs.length > 0 ? outputs[0].createItemStack() : ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ICraftingPatternDetails)) return false;
        ICraftingPatternDetails that = (ICraftingPatternDetails) o;
        return Arrays.equals(condensedInputs, that.getCondensedInputs())
                && Arrays.equals(condensedOutputs, that.getCondensedOutputs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(condensedInputs), Arrays.hashCode(condensedOutputs));
    }
}
