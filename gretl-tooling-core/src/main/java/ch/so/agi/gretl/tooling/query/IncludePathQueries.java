package ch.so.agi.gretl.tooling.query;

import ch.so.agi.gretl.tooling.syntax.IncludeCompletionSite;
import ch.so.agi.gretl.tooling.syntax.SyntaxModel.QuoteKind;
import ch.so.agi.gretl.tooling.source.TextSpan;
import ch.so.agi.gretl.tooling.workspace.SourceUris;
import ch.so.agi.gretl.tooling.edit.SourceValueWriter;
import java.util.*;
import static ch.so.agi.gretl.tooling.query.QueryResults.*;

/** Queries only frozen, bounded host listings. A lexical hint never upgrades syntax coverage. */
final class IncludePathQueries {
    record Result(boolean applicable,List<CompletionProposal> items,boolean incomplete) { }
    Result complete(QueryContext context){
        var document=context.document().orElse(null);if(document==null)return new Result(false,List.of(),false);
        for(var site:IncludeCompletionSite.in(document)){
            var content=site.literal().content();int offset=context.offsetUtf16();
            if(offset<content.startUtf16()||offset>content.endUtf16())continue;
            String prefix=site.prefix(offset);int slash=prefix.lastIndexOf('/');String parent=slash<0?"":prefix.substring(0,slash+1),typed=prefix.substring(slash+1);
            try{
                var directory=SourceUris.directoryIdentity(SourceUris.child(SourceUris.parent(context.documentId()),parent));
                var listing=context.snapshot().analysis().capture().project().sources().pathListings().get(directory);
                if(listing==null)return new Result(true,List.of(),true);
                var proposals=new ArrayList<CompletionProposal>();
                for(var entry:listing.entries()){
                    String path=entry.documentId().getPath();String label=path.substring(path.lastIndexOf('/')+1)+(entry.directory()?"/":"");
                    if(!label.startsWith(typed))continue;
                    String encoded=SourceValueWriter.string(label,site.literal().quotes());int width=switch(site.literal().quotes()){case TRIPLE_SINGLE,TRIPLE_DOUBLE->3;case DOLLAR_SLASHY->2;default->1;};
                    encoded=encoded.substring(width,encoded.length()-width);
                    int start=parent.isEmpty()?content.startUtf16():site.literal().decodedToSource().get(parent.length());
                    if(!site.closed())encoded+=site.literal().quotes()==QuoteKind.SINGLE?"'":"\"";
                    proposals.add(new CompletionProposal(label,entry.directory()?"FOLDER":"FILE","Captured include path",new TextSpan(start,content.endUtf16()),encoded));
                }
                return new Result(true,List.copyOf(proposals),!listing.complete());
            }catch(IllegalArgumentException invalid){return new Result(true,List.of(),true);}
        }
        return new Result(false,List.of(),false);
    }
}
