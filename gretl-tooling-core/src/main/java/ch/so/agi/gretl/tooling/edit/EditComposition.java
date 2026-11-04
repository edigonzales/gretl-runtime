package ch.so.agi.gretl.tooling.edit;

import ch.so.agi.gretl.tooling.CancellationToken;
import ch.so.agi.gretl.tooling.source.SourceText;
import ch.so.agi.gretl.tooling.source.TextSpan;
import java.util.Arrays;
import java.util.List;

/** Tracks original character provenance through verified local edits, preserving untouched runs verbatim. */
final class EditComposition {
    private final SourceText original;
    private int[] origins;
    private String text;
    EditComposition(SourceText original) {
        this.original=original;text=original.text();origins=new int[text.length()];
        for(int i=0;i<origins.length;i++)origins[i]=i;
    }
    void append(List<SourceReplacement> replacements,String expectedPostimage,CancellationToken cancellation) {
        cancellation.check();
        var builder=new SourceEditBuilder(new SourceText(original.stamp().documentId(),text,null,"COMPOSITION",true));
        for(var replacement:replacements)builder.replace(replacement.span(),replacement.expectedText(),replacement.replacement());
        var edits=builder.build();String nextText=builder.apply();
        if(!nextText.equals(expectedPostimage))throw new IllegalArgumentException("EDIT_COMPOSITION_POSTIMAGE");
        int[] next=new int[nextText.length()];Arrays.fill(next,-1);
        int from=0,to=0;
        for(var edit:edits) {
            cancellation.check();
            int length=edit.span().startUtf16()-from;
            System.arraycopy(origins,from,next,to,length);
            to+=length+edit.replacement().length();from=edit.span().endUtf16();
        }
        System.arraycopy(origins,from,next,to,origins.length-from);
        origins=next;text=nextText;
    }
    List<SourceReplacement> build(CancellationToken cancellation) {
        var builder=new SourceEditBuilder(original);var pending=new StringBuilder();int consumed=0;
        for(int i=0;i<origins.length;i++) {
            if((i&4095)==0)cancellation.check();
            int origin=origins[i];
            if(origin<0)pending.append(text.charAt(i));
            else {
                if(origin<consumed||original.text().charAt(origin)!=text.charAt(i))throw new IllegalArgumentException("EDIT_COMPOSITION_PROVENANCE");
                if(origin>consumed||!pending.isEmpty()) {
                    var span=new TextSpan(consumed,origin);builder.replace(span,original.slice(span),pending.toString());pending.setLength(0);
                }
                consumed=origin+1;
            }
        }
        if(consumed<original.text().length()||!pending.isEmpty()) {
            var span=new TextSpan(consumed,original.text().length());builder.replace(span,original.slice(span),pending.toString());
        }
        return builder.build();
    }
}
