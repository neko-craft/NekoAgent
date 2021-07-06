package cn.apisium.nekoagent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public final class Main {
    private static boolean enableSandDuplication, disableObsidianSpikesReset, enableStoneCutterDamage, enableShulkerSpawningInEndCities;
    private static String serverName;
    private final static ClassPool pool = ClassPool.getDefault();

    public static void premain(final String agentArgs, final Instrumentation inst) {
        if (agentArgs != null) {
            if (agentArgs.contains("enableSandDuplication")) enableSandDuplication = true;
            if (agentArgs.contains("disableObsidianSpikesReset")) disableObsidianSpikesReset = true;
            if (agentArgs.contains("enableStoneCutterDamage")) enableStoneCutterDamage = true;
            if (agentArgs.contains("enableShulkerSpawningInEndCities")) enableShulkerSpawningInEndCities = true;
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
        for (String name : args) if (name.contains(".")) sb.append('L').append(name.replace(".", "/")).append(';');
        else sb.append(name);
        return sb.append(')').append(returnVal == null ? 'V' : returnVal.contains(".")
                ? "L" + returnVal.replace(".", "/") + ";" : returnVal).toString();
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
                    case "net.minecraft.world.entity.Entity": {
                        if (!enableSandDuplication) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.addField(CtField.make("private final boolean isFallingBlock = getClass() ==" +
                                "net.minecraft.world.entity.item.EntityFallingBlock.class;", clazz));
                        var editor = new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                var it = switch (m.getMethodName()) {
                                    case "isRemoved" -> "!isFallingBlock && $0.isRemoved()";
                                    case "isAlive" -> "isFallingBlock || $0.isAlive()";
                                    default -> null;
                                };
                                if (it != null) m.replace("$_ = " + it + ";");
                            }

                            @Override
                            public void edit(FieldAccess f) throws CannotCompileException {
                                if (f.getFieldName().equals("valid")) f.replace("$_ = isFallingBlock || $0.valid;");
                            }
                        };
                        clazz.getDeclaredMethod("tickEndPortal").instrument(editor);
                        clazz.getMethod("teleportTo", buildDesc("net.minecraft.world.entity.Entity",
                                "net.minecraft.server.level.WorldServer", "net.minecraft.core.BlockPosition")).instrument(editor);
                        clazz.getDeclaredMethod("callPortalEvent").instrument(editor);
                        break;
                    }
                    case "net.minecraft.world.entity.item.EntityFallingBlock": {
                        if (!enableSandDuplication) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        final int[] flag = {0};
                        clazz.addMethod(CtNewMethod.make("public boolean canPortal() { return true; }", clazz));
                        clazz.getDeclaredMethod("tick").instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (!m.getMethodName().equals("isRemoved")) return;
                                m.replace("$_ = false;");
                                flag[0]++;
                            }
                        });
                        if (flag[0] != 2) throw new RuntimeException("Cannot find isRemoved call!");
                        break;
                    }
                    case "net.minecraft.world.level.levelgen.feature.WorldGenEnder":
                        if (!disableObsidianSpikesReset) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getDeclaredMethod("generate").setBody("{ return true; }");
                        clazz.getMethod("a", buildDesc("java.util.List", "net.minecraft.world.level.GeneratorAccessSeed"))
                            .setBody("{ return java.util.Collections.emptyList(); }");
                        break;
                    case "net.minecraft.server.MinecraftServer":
                        if (serverName == null) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getDeclaredMethod("getServerModName").setBody("{ return \"" + serverName
                                .replace("\"", "\\\"") + "\"; }");
                        break;
                    case "net.minecraft.world.level.block.BlockStonecutter": {
                        if (!enableStoneCutterDamage) return null;
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
                    case "net.minecraft.world.level.chunk.ChunkGenerator":
                        if (!enableShulkerSpawningInEndCities) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.addField(CtField.make("""
                        private static final net.minecraft.util.random.WeightedRandomList shulkers = net.minecraft.util.random.WeightedRandomList
                            .a((net.minecraft.util.random.WeightedEntry[]) new net.minecraft.world.level.biome.BiomeSettingsMobs.c[] {
                                new net.minecraft.world.level.biome.BiomeSettingsMobs.c(
                                    (net.minecraft.world.entity.EntityTypes) net.minecraft.world.entity.EntityTypes
                                    .a("shulker").get(), 10, 1, 4) });
                        """, clazz));
                        clazz.getDeclaredMethod("getMobsFor").insertBefore("""
                                { if ($3 == net.minecraft.world.entity.EnumCreatureType.a && $2.a($4, true,
                                    net.minecraft.world.level.levelgen.feature.StructureGenerator.o).e()) return $0.shulkers; }""");
                        break;
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
