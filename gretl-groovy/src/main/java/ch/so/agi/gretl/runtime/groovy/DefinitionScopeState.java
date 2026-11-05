package ch.so.agi.gretl.runtime.groovy;

import ch.so.agi.gretl.runtime.api.*;
import ch.so.agi.gretl.runtime.runner.JobDefinitionBuilder;
import java.util.*;

/** GRETL-managed state shared within one scope; never a script Binding. */
final class DefinitionScopeState {
    final JobDefinitionBuilder builder;
    final Map<String,TaskRef<?>> taskRefs=new LinkedHashMap<>();
    final Map<String,OperationType<?,?>> taskOperations=new LinkedHashMap<>();
    DefinitionScopeState(JobDefinitionBuilder builder){this.builder=builder;}
}
