package nyonio.config;

import net.minecraftforge.common.config.Config;

@Config(modid = "lazy_energy")
public class LazyEnergyConfig {

    @Config.Name("FE per AE")
    @Config.Comment("How much Forge Energy equals 1 AE power. Default: 2 FE = 1 AE")
    public static double fePerAE = 2.0;

    @Config.Name("Max FE per tick from AE")
    @Config.Comment("Maximum FE extracted from AE2 network per tick per machine. 0 = unlimited")
    public static int maxFEPerTick = 0;

}
