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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
    private static boolean enableSandDuplication, disableObsidianSpikesReset, enableStoneCutterDamage,
            enableShulkerSpawningInEndCities, enableSetMSPTCommand, enableLeashableViallagers,
            enableFakePermissionLevel4;
    private static String serverName;
    private static int maxShulkersCount = 4, minShulkersCount = 1;
    private final static ClassPool pool = ClassPool.getDefault();

    public static void premain(final String agentArgs, final Instrumentation inst) {
        if (agentArgs != null) {
            if (agentArgs.contains("enableSandDuplication")) enableSandDuplication = true;
            if (agentArgs.contains("disableObsidianSpikesReset")) disableObsidianSpikesReset = true;
            if (agentArgs.contains("enableStoneCutterDamage")) enableStoneCutterDamage = true;
            if (agentArgs.contains("enableSetMSPTCommand")) enableSetMSPTCommand = true;
            if (agentArgs.contains("enableShulkerSpawningInEndCities")) enableShulkerSpawningInEndCities = true;
            if (agentArgs.contains("enableLeashableViallagers")) enableLeashableViallagers = true;
            if (agentArgs.contains("enableFakeLevel4Permission")) enableFakePermissionLevel4 = true;
            Matcher matcher = Pattern.compile("maxShulkersCount=(\\d+)").matcher(agentArgs);
            if (matcher.find()) maxShulkersCount = Integer.parseInt(matcher.group(1));
            matcher = Pattern.compile("minShulkersCount=(\\d+)").matcher(agentArgs);
            if (matcher.find()) minShulkersCount = Integer.parseInt(matcher.group(1));
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
        public byte[] transform(final ClassLoader loader, String className, final Class<?> classBeingRedefined,
                                final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
            if (obcPrefix == null && className.startsWith("org/bukkit/craftbukkit/")) {
                String[] arr = className.split("/");
                if (arr.length < 4 || !arr[3].startsWith("v")) return null;
                obcPrefix = "org.bukkit.craftbukkit." + arr[3] + ".";
            }
            if (enableSetMSPTCommand) switch (className) {
                case "org/spigotmc/TicksPerSecondCommand": try {
                    pool.insertClassPath(new LoaderClassPath(loader));
                    CtClass clazz = pool.get(className.replace('/', '.'));
                    clazz.getDeclaredMethod("format").setBody("""
                    { return ($1 > 21.3D ? org.bukkit.ChatColor.AQUA : $1 > 18.0D ? org.bukkit.ChatColor.GREEN : $1 > 16.0D ?
                            org.bukkit.ChatColor.YELLOW : org.bukkit.ChatColor.RED).toString() + ($1 > 21.0D ? "*" : "") +
                            ((double)Math.round($1 * 100.0D) / 100.0D); }""");
                    System.out.println("[NekoAgent] Class " + className + " has been modified!");
                    return clazz.toBytecode();
                } catch (Throwable e) { throw new RuntimeException(e); }
                case "com/destroystokyo/paper/MSPTCommand": try {
                    pool.insertClassPath(new LoaderClassPath(loader));
                    CtClass clazz = pool.get(className.replace('/', '.'));
                    clazz.getDeclaredMethod("execute").insertBefore("""
                            { if (!$0.testPermission($1)) return true; if ($3.length == 1) { try {
                                long v = Long.parseLong(args[0]); if (v < 0 || v > 10000) return false;
                                net.minecraft.server.MinecraftServer.shouldWaitTickTime = v;
                                $1.sendMessage("[NekoAgent] Set mspt to " + args[0]);
                                return true; } catch (Throwable e) { return false; } } else $1.sendMessage(
                                "[NekoAgent] Current mspt value: " + net.minecraft.server.MinecraftServer.shouldWaitTickTime); }""");
                    System.out.println("[NekoAgent] Class " + className + " has been modified!");
                    return clazz.toBytecode();
                } catch (Throwable e) { throw new RuntimeException(e); }
            }
            if (!className.startsWith("net/minecraft/")) return null;
            className = className.replace('/', '.');
            final CtClass clazz;
            switch (className) {
                case "net.minecraft.network.protocol.game.PacketPlayOutEntityStatus":
                    if (!enableFakePermissionLevel4) return null;
                    try {
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getConstructor(buildDesc(null, "net.minecraft.world.entity.Entity", "B"))
                                .insertBefore("{ if ($2 > 23 && $2 < 28) $2 = 28; }");
                        break;
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.entity.Entity":
                    if (!enableSandDuplication) return null;
                    try {
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
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.entity.item.EntityFallingBlock":
                    if (!enableSandDuplication) return null;
                    try {
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
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.level.levelgen.feature.WorldGenEnder":
                    if (!disableObsidianSpikesReset) return null;
                    try {
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getDeclaredMethod("generate").setBody("{ return true; }");
                        clazz.getMethod("a", buildDesc("java.util.List", "net.minecraft.world.level.GeneratorAccessSeed"))
                                .setBody("{ return java.util.Collections.emptyList(); }");
                        break;
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.server.MinecraftServer":
                    if (serverName == null && !enableSetMSPTCommand) return null;
                    try {
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        if (serverName != null) clazz.getDeclaredMethod("getServerModName")
                                .setBody("{ return \"" + serverName.replace("\"", "\\\"") + "\"; }");
                        if (enableSetMSPTCommand) {
                            clazz.addField(CtField.make("public static long shouldWaitTickTime = 50L;", clazz));
                            clazz.getDeclaredMethod("bh").insertBefore("{ $0.ao += $0.shouldWaitTickTime - 50L; }");
                            clazz.getDeclaredMethod("sleepForTick")
                                    .insertBefore("{ $0.ap = Math.max(net.minecraft.SystemUtils.getMonotonicMillis() +" +
                                            "$0.shouldWaitTickTime, $0.ao); }");
                        }
                        break;
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.level.block.BlockStonecutter":
                    if (!enableStoneCutterDamage) return null;
                    try {
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
                    } catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.entity.npc.EntityVillagerAbstract":
                    if (!enableLeashableViallagers) return null;
                    try {
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.getMethod("a", buildDesc("Z", "net.minecraft.world.entity.player.EntityHuman"))
                            .setBody("{ return true; }");
                        break;
                    }  catch (Throwable e) { throw new RuntimeException(e); }
                case "net.minecraft.world.level.chunk.ChunkGenerator":
                    if (!enableShulkerSpawningInEndCities) return null;
                    try {
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className);
                        clazz.addField(CtField.make(String.format("""
                                private static final net.minecraft.util.random.WeightedRandomList shulkers = net.minecraft.util.random.WeightedRandomList
                                    .a((net.minecraft.util.random.WeightedEntry[]) new net.minecraft.world.level.biome.BiomeSettingsMobs.c[] {
                                        new net.minecraft.world.level.biome.BiomeSettingsMobs.c(
                                            (net.minecraft.world.entity.EntityTypes) net.minecraft.world.entity.EntityTypes
                                            .a("shulker").get(), 10, %d, %d) });
                                """, minShulkersCount, maxShulkersCount), clazz));
                        clazz.getDeclaredMethod("getMobsFor").insertBefore("""
                                { if ($3 == net.minecraft.world.entity.EnumCreatureType.a && $2.a($4, true,
                                    net.minecraft.world.level.levelgen.feature.StructureGenerator.o).e()) return $0.shulkers; }""");
                        break;
                    }  catch (Throwable e) { throw new RuntimeException(e); }
                default: return null;
            }
            System.out.println("[NekoAgent] Class " + className + " has been modified!");
            try { return clazz.toBytecode(); } catch (Throwable e) { throw new RuntimeException(e); }
        }
    }
}
