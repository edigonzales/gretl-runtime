package ch.so.agi.gretl.lsp;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Enforces framing limits before LSP4J allocates a JSON message. No workspace-dependent parsing. */
final class BoundedRpcInput extends InputStream {
    static final int MAXIMUM_MESSAGE_BYTES=2_097_152;
    private final InputStream input;
    private byte[] header=new byte[0];private int offset,remaining;
    BoundedRpcInput(InputStream input){this.input=input;}
    public int read() throws IOException {
        if(offset<header.length)return header[offset++]&255;
        if(remaining>0){int value=input.read();if(value<0)throw new EOFException("RPC_TRUNCATED_BODY");remaining--;return value;}
        var bytes=new ByteArrayOutputStream();int state=0;
        while(state!=4) {
            int value=input.read();if(value<0){if(bytes.size()==0)return -1;throw new EOFException("RPC_TRUNCATED_HEADER");}
            bytes.write(value);if(bytes.size()>8192)throw new IOException("RPC_HEADER_LIMIT");
            state=switch(state){case 0->value=='\r'?1:0;case 1->value=='\n'?2:value=='\r'?1:0;case 2->value=='\r'?3:0;default->value=='\n'?4:0;};
        }
        int length=-1;
        for(var line:bytes.toString(StandardCharsets.US_ASCII).split("\r\n"))if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
            if(length!=-1)throw new IOException("RPC_DUPLICATE_LENGTH");
            try{length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());}catch(NumberFormatException invalid){throw new IOException("RPC_LENGTH_INVALID");}
        }
        if(length<0||length>MAXIMUM_MESSAGE_BYTES)throw new IOException("RPC_MESSAGE_LIMIT");
        header=bytes.toByteArray();offset=1;remaining=length;return header[0]&255;
    }
    public int read(byte[] bytes,int start,int length) throws IOException {
        if(length==0)return 0;
        if(offset<header.length){int count=Math.min(length,header.length-offset);System.arraycopy(header,offset,bytes,start,count);offset+=count;return count;}
        if(remaining>0){int count=input.read(bytes,start,Math.min(length,remaining));if(count<0)throw new EOFException("RPC_TRUNCATED_BODY");remaining-=count;return count;}
        int first=read();if(first<0)return -1;bytes[start]=(byte)first;return 1;
    }
}
