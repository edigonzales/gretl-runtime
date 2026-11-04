package ch.so.agi.gretl.tooling.syntax;

import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.*;

/** Source regions whose execution depends on control flow; no condition is evaluated. */
public final class ExecutionRegions {
    private ExecutionRegions() { }
    public static List<TextSpan> normalize(List<TextSpan> regions){
        var ordered=regions.stream().filter(s->s.length()>0).sorted(Comparator.comparingInt(TextSpan::startUtf16)).toList();var result=new ArrayList<TextSpan>();
        for(var span:ordered){if(!result.isEmpty()&&span.startUtf16()<=result.getLast().endUtf16()){var previous=result.removeLast();result.add(new TextSpan(previous.startUtf16(),Math.max(previous.endUtf16(),span.endUtf16())));}else result.add(span);}return List.copyOf(result);
    }
    /** Input is normalized; logarithmic lookup also bounds large flat control-flow bodies. */
    public static boolean contains(List<TextSpan> regions,TextSpan target){
        int low=0,high=regions.size()-1,index=-1;while(low<=high){int middle=(low+high)>>>1;if(regions.get(middle).startUtf16()<=target.startUtf16()){index=middle;low=middle+1;}else high=middle-1;}return index>=0&&regions.get(index).contains(target);
    }
}
