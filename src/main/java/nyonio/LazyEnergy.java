package nyonio;

import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.registry.GameRegistry;
import nyonio.ae.AEProxyHandler;
import nyonio.ae.MemoryCardHandler;
import nyonio.item.ItemLazyPattern;
import org.apache.logging.log4j.Logger;

@Mod(modid = LazyEnergy.MODID, name = LazyEnergy.NAME, version = LazyEnergy.VERSION,
        dependencies = "required-after:appliedenergistics2;required-after:threng")
public class LazyEnergy
{
    public static final String MODID = "lazy_energy";
    public static final String NAME = "\u61d2\u4eba\u80fd\u6e90";
    public static final String VERSION = "1.0";

    public static final ItemLazyPattern LAZY_PATTERN = new ItemLazyPattern();

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        MinecraftForge.EVENT_BUS.register(AEProxyHandler.class);
        MinecraftForge.EVENT_BUS.register(MemoryCardHandler.class);
        GameRegistry.findRegistry(Item.class).register(LAZY_PATTERN);
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        logger.info("{} v{} loaded - AE2 power bridge active", NAME, VERSION);
    }
}
