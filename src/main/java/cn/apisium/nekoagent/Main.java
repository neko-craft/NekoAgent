package cn.apisium.nekoagent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class Main {
    public static void premain(final String agentArgs, final Instrumentation inst) {
        inst.addTransformer(new Transformer());
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
            if (!className.startsWith("net/minecraft/server/") || !className.endsWith("/EntityFallingBlock")) return null;
            try {
                final ClassPool pool = ClassPool.getDefault();
                pool.insertClassPath(new LoaderClassPath(loader));
                final CtClass clazz = pool.get(className.replace('/', '.'));
                final CtMethod method = clazz.getMethod("tick", "()V");
                method.instrument(new ExprEditor() {
                    public void edit(FieldAccess m) throws CannotCompileException {
                        if (!m.isReader() || !m.getFieldName().equals("dead")) return;
                        m.replace("$_ = false;");
                    }
                });
                return clazz.toBytecode();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
