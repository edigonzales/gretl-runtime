package ch.so.agi.gretl.it;
public final class RuntimeCanaryMain {
 public static void main(String[] args)throws Throwable {
  var type=Class.forName("ch.so.agi.gretl.it."+args[0]);var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
  var method=type.getDeclaredMethod("actualGuiGeneratedSourceDownloadsAndExtractsALocalArchiveWithoutRewritingItsBytes");method.setAccessible(true);
  try{method.invoke(constructor.newInstance());}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
 }
}
