package nyonio.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class LazyEnergyTransformer implements IClassTransformer {

    private static final String TILE_AGGREGATOR = "io.github.phantamanta44.threng.tile.TileAggregator";
    private static final String TILE_CENTRIFUGE = "io.github.phantamanta44.threng.tile.TileCentrifuge";
    private static final String TILE_ENERGIZER = "io.github.phantamanta44.threng.tile.TileEnergizer";
    private static final String TILE_ETCHER = "io.github.phantamanta44.threng.tile.TileEtcher";
    private static final String TILE_MACHINE = "io.github.phantamanta44.threng.tile.base.TileMachine";

    private static final String IGRID_HOST = "appeng/api/networking/IGridHost";
    private static final String PROXY_HOLDER = "nyonio/ae/ProxyHolder";
    private static final String AE_PART_LOCATION = "appeng/api/util/AEPartLocation";
    private static final String IGRID_NODE = "appeng/api/networking/IGridNode";
    private static final String AE_CABLE_TYPE = "appeng/api/util/AECableType";
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final String AE_POWER_BRIDGE = "nyonio/ae/AEPowerBridge";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (transformedName.equals(TILE_AGGREGATOR)
                || transformedName.equals(TILE_CENTRIFUGE)
                || transformedName.equals(TILE_ENERGIZER)
                || transformedName.equals(TILE_ETCHER)) {
            return addInterfaces(basicClass);
        }

        if (transformedName.equals(TILE_MACHINE)) {
            return injectTickEnergy(basicClass);
        }

        return basicClass;
    }

    private byte[] addInterfaces(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        cn.interfaces.add(IGRID_HOST);

        cn.methods.add(makeGetGridNode());
        cn.methods.add(makeGetCableConnectionType());
        cn.methods.add(makeSecurityBreak());

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] injectTickEnergy(byte[] basicClass) {
        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.equals("tick") && method.desc.equals("()V")) {
                InsnList insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, AE_POWER_BRIDGE, "onTick",
                        "(L" + TILE_MACHINE.replace('.', '/') + ";)V", false));
                method.instructions.insert(insns);
                break;
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private MethodNode makeGetGridNode() {
        MethodNode mv = new MethodNode(Opcodes.ACC_PUBLIC, "getGridNode",
                "(L" + AE_PART_LOCATION + ";)L" + IGRID_NODE + ";", null, null);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, TILE_ENTITY);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, PROXY_HOLDER, "getGridNode",
                "(L" + TILE_ENTITY + ";L" + AE_PART_LOCATION + ";)L" + IGRID_NODE + ";", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 2);
        return mv;
    }

    private MethodNode makeGetCableConnectionType() {
        MethodNode mv = new MethodNode(Opcodes.ACC_PUBLIC, "getCableConnectionType",
                "(L" + AE_PART_LOCATION + ";)L" + AE_CABLE_TYPE + ";", null, null);
        mv.visitFieldInsn(Opcodes.GETSTATIC, AE_CABLE_TYPE, "SMART", "L" + AE_CABLE_TYPE + ";");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 2);
        return mv;
    }

    private MethodNode makeSecurityBreak() {
        MethodNode mv = new MethodNode(Opcodes.ACC_PUBLIC, "securityBreak", "()V", null, null);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, TILE_ENTITY);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TILE_ENTITY, "getWorld", "()Lnet/minecraft/world/World;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, TILE_ENTITY);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TILE_ENTITY, "getPos", "()Lnet/minecraft/util/math/BlockPos;", false);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "destroyBlock",
                "(Lnet/minecraft/util/math/BlockPos;Z)Z", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 1);
        return mv;
    }
}
