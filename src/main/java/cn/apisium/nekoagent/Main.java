package cn.apisium.nekoagent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class Main {
    private static boolean disallowSandDuplication, allowEndPlatform, allowObsidianSpikesReset;

    private final static ClassPool pool = ClassPool.getDefault();
    public static void premain(final String agentArgs, final Instrumentation inst) {
        if (agentArgs != null) {
            if (agentArgs.contains("disallowSandDuplication")) disallowSandDuplication = true;
            if (agentArgs.contains("allowEndPlatform")) allowEndPlatform = true;
            if (agentArgs.contains("allowObsidianSpikesReset")) allowObsidianSpikesReset = true;
        }
        inst.addTransformer(new Transformer());
    }

    private static String classPrefix;

    private static String buildDesc(String returnVal, String ...args) {
        StringBuilder sb = new StringBuilder("(");
        for (String name : args) {
            if (name.contains("/")) sb.append('L').append(name);
            else sb.append('L').append(classPrefix).append(name);
            sb.append(';');
        }
        return sb.append(')').append(returnVal == null ? 'V' : returnVal).toString();
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
            if (!className.startsWith("net/minecraft/server/")) return null;
            String[] arr = className.split("/");
            if (arr.length != 5) return null;
            if (classPrefix == null) classPrefix = arr[0] + "/" + arr[1] + "/" + arr[2] + "/" + arr[3] + "/";
            try {
                final CtClass clazz;
                switch (arr[arr.length - 1]) {
                    case "EntityFallingBlock": {
                        if (disallowSandDuplication) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        final boolean[] flag = {false};
                        clazz.getMethod("tick", "()V").instrument(new ExprEditor() {
                            public void edit(FieldAccess m) throws CannotCompileException {
                                if (!m.isReader() || !m.getFieldName().equals("dead")) return;
                                m.replace("$_ = false;");
                                flag[0] = true;
                            }
                        });
                        if (flag[0]) System.out.println("[NekoAgent] Class EntityFallingBlock modified!");
                        break;
                    }
                    case "EntityPlayer":
                        if (allowEndPlatform) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("a", buildDesc(null, "WorldServer", "BlockPosition")).setBody("{}");
                        System.out.println("[NekoAgent] Class EntityPlayer modified!");
                        break;
                    case "WorldServer":
                        if (allowEndPlatform) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("a", buildDesc(null, "WorldServer", "Entity")).setBody("{}");
                        System.out.println("[NekoAgent] Class WorldServer modified!");
                        break;
                    case "WorldGenEnder":
                        if (allowObsidianSpikesReset) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("a", buildDesc("Z",
                                "GeneratorAccessSeed", "ChunkGenerator", "java/util/Random", "BlockPosition",
                                "WorldGenFeatureEndSpikeConfiguration")).setBody("{ return true; }");
                        clazz.getMethod("a", buildDesc("Ljava/util/List;",
                                "GeneratorAccessSeed")).setBody("{ return java.util.Collections.emptyList(); }");
                        System.out.println("[NekoAgent] Class WorldGenEnder modified!");
                        break;
                    default: return null;
                }

                return clazz.toBytecode();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
