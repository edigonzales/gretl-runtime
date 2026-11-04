package ch.so.agi.gretl.cli;

import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;

/** Test-only tripwire: linking compiler types is allowed; constructing/using a compiler is fatal. */
public final class NoSourceCompilerAgent {
    public static void premain(String arguments,Instrumentation instrumentation){
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
                if(!name.equals("org/codehaus/groovy/control/CompilationUnit")&&!name.equals("org/codehaus/groovy/control/SourceUnit"))return null;
                try {
                    var cf=ClassFile.of();byte[] guarded=cf.transformClass(cf.parse(bytes),(builder,element)->{
                        if(element instanceof MethodModel method&&(method.methodName().equalsString("<init>")||method.methodName().equalsString("compile")||method.methodName().equalsString("parse")))
                            builder.withMethodBody(method.methodName(),method.methodType(),method.flags().flagsMask(),code->code
                                .new_(ClassDesc.of("java.lang.AssertionError")).dup().ldc("SOURCE_COMPILER_USED")
                                .invokespecial(ClassDesc.of("java.lang.AssertionError"),"<init>",MethodTypeDesc.of(ConstantDescs.CD_void,ConstantDescs.CD_Object)).athrow());
                        else builder.with(element);
                    });
                    System.err.println("COMPILER_GUARD_TRANSFORMED "+name);return guarded;
                }catch(Throwable failure){failure.printStackTrace();Runtime.getRuntime().halt(91);return null;}
            }
        });
        System.err.println("COMPILER_GUARD_INSTALLED");
    }
    public static void main(String[] args)throws Exception{
        try{Class.forName("org.codehaus.groovy.control.CompilationUnit").getConstructor().newInstance();throw new AssertionError("Tripwire did not fire");}
        catch(java.lang.reflect.InvocationTargetException failure){if(!(failure.getCause() instanceof AssertionError)||!"SOURCE_COMPILER_USED".equals(failure.getCause().getMessage()))throw failure;System.out.println("COMPILER_BLOCKED");}
    }
}
