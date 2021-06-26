package cn.apisium.nekoagent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public final class Main {
    private static boolean disallowSandDuplication, allowObsidianSpikesReset, stoneCutterNoDamage;
    private static String serverName;
    private final static ClassPool pool = ClassPool.getDefault();

    public static void premain(final String agentArgs, final Instrumentation inst) {
        if (agentArgs != null) {
            if (agentArgs.contains("disallowSandDuplication")) disallowSandDuplication = true;
            if (agentArgs.contains("allowObsidianSpikesReset")) allowObsidianSpikesReset = true;
            if (agentArgs.contains("stoneCutterNoDamage")) stoneCutterNoDamage = true;
        }
        final File file = new File("server_name.txt");
        if (file.exists()) try {
            serverName = Files.readString(file.toPath());
        } catch (IOException e) { e.printStackTrace(); }
        inst.addTransformer(new Transformer());
    }

    private static String obcPrefix;

    private static String buildDesc(@SuppressWarnings("SameParameterValue") String returnVal, String ...args) {
        StringBuilder sb = new StringBuilder("(");
        for (String name : args) sb.append('L').append(name.replace(".", "/")).append(';');
        return sb.append(')').append(returnVal == null ? 'V' : returnVal).toString();
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(final ClassLoader loader, String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
            if (obcPrefix == null && className.startsWith("org/bukkit/craftbukkit/")) {
                String[] arr = className.split("/");
                if (arr.length < 4 || !arr[3].startsWith("v")) return null;
                obcPrefix = "org.bukkit.craftbukkit." + arr[3] + ".";
            }
            if (!className.startsWith("net/minecraft/")) return null;
            className = className.replace('/', '.');
            try {
                final CtClass clazz;
                switch (className) {
                    case "net.minecraft.world.entity.item.EntityFallingBlock": {
                        if (disallowSandDuplication) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        final boolean[] flag = {false};
                        clazz.getMethod("tick", "()V").instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (!m.getMethodName().equals("isRemoved")) return;
                                m.replace("$_ = false;");
                                flag[0] = true;
                            }
                        });
                        if (!flag[0]) throw new RuntimeException("Cannot find isRemoved call!");
                        break;
                    }
                    case "net.minecraft.world.level.levelgen.feature.WorldGenEnder":
                        if (allowObsidianSpikesReset) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getMethod("generate", buildDesc("Z",
                                "net.minecraft.world.level.levelgen.feature.FeaturePlaceContext")).setBody("{ return true; }");
                        break;
                    case "net.minecraft.server.MinecraftServer":
                        if (serverName == null) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getMethod("getServerModName", "()Ljava/lang/String;").setBody("{ return \"" + serverName
                                .replace("\"", "\\\"") + "\"; }");
                        break;
                    case "net.minecraft.world.level.block.BlockStonecutter": {
                        if (stoneCutterNoDamage) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.addMethod(CtNewMethod.make("""
                            public void stepOn(net.minecraft.world.level.World world, net.minecraft.core.BlockPosition p,
                                net.minecraft.world.level.block.state.IBlockData state, net.minecraft.world.entity.Entity entity) {
                                if (!(entity instanceof net.minecraft.world.entity.EntityLiving) ||
                                    net.minecraft.world.item.enchantment.EnchantmentManager.i((net.minecraft.world.entity.EntityLiving) entity)) return;
                                #event.CraftEventFactory.blockDamage = world.getWorld().getBlockAt(p.getX(), p.getY(), p.getZ());
                                entity.damageEntity(net.minecraft.world.damagesource.DamageSource.o, 4.0F);
                                #event.CraftEventFactory.blockDamage = null;
                            }}""".replace("#", obcPrefix), clazz));
                        break;
                    }
                    default: return null;
                }
                System.out.println("[NekoAgent] Class " + className + " has been modified!");
                return clazz.toBytecode();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
