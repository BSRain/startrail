package com.startrail.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bounded document reads, independent of provider-reported length or MIME type. */
final class BackupDocuments {
    static String read(InputStream input)throws IOException {
        if(input==null)throw new IOException("无法打开备份文件。");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){
            if(Thread.currentThread().isInterrupted())throw new IOException("读取已取消。");
            if(bytes.size()+n>JournalBackup.MAX_BYTES)throw new IOException("备份超过 2 MiB，请选择较小的星迹备份。");
            bytes.write(buffer,0,n);
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
}
