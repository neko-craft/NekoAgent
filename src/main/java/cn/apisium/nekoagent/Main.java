package cn.apisium.nekoagent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class Main {
    private static boolean allowSandDuplication, allowEndPlatform;

    private final static ClassPool pool = ClassPool.getDefault();
    public static void premain(final String agentArgs, final Instrumentation inst) {
        if (agentArgs != null) {
            if (agentArgs.contains("allowDuplicateSand")) allowSandDuplication = true;
            if (agentArgs.contains("allowEndPlatform")) allowEndPlatform = true;
        }
        inst.addTransformer(new Transformer());
    }

    private static String classPrefix;
    private static String getNMSClassName(final String name) {
        return classPrefix + name;
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
                        if (allowSandDuplication) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("tick", "()V").instrument(new ExprEditor() {
                            public void edit(FieldAccess m) throws CannotCompileException {
                                if (!m.isReader() || !m.getFieldName().equals("dead")) return;
                                m.replace("$_ = false;");
                            }
                        });
                        System.out.println("[NekoAgent] Class EntityFallingBlock modified!");
                        break;
                    }
                    case "EntityPlayer":
                        if (allowEndPlatform) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("a", String.format("(L%s;L%s;)V", getNMSClassName("WorldServer"), getNMSClassName("BlockPosition"))).setBody("{}");
                        System.out.println("[NekoAgent] Class EntityPlayer modified!");
                        break;
                    case "WorldServer":
                        if (allowEndPlatform) return null;
                        pool.insertClassPath(new LoaderClassPath(loader));
                        clazz = pool.get(className.replace('/', '.'));
                        clazz.getMethod("a", String.format("(L%s;L%s;)V", getNMSClassName("WorldServer"), getNMSClassName("Entity"))).setBody("{}");
                        System.out.println("[NekoAgent] Class WorldServer modified!");
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
