package com.startrail.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Exercises actual document streams, worker callbacks, review/cancel and durable merge. */
@SuppressWarnings("deprecation")
public final class BackupFlowTest extends ActivityInstrumentationTestCase2<MainActivity>{
    private SharedPreferences prefs;private String previous;private File file;
    public BackupFlowTest(){super(MainActivity.class);}
    private Object get(MainActivity a,String name)throws Exception{Field owner=MainActivity.class.getDeclaredField("journalTransfer");owner.setAccessible(true);Object controller=owner.get(a);Field f=JournalTransferController.class.getDeclaredField(name);f.setAccessible(true);return f.get(controller);}
    @Override protected void setUp()throws Exception{super.setUp();prefs=getInstrumentation().getTargetContext().getSharedPreferences("journal",0);previous=prefs.getString("entries",null);prefs.edit().remove("entries").commit();file=new File(getInstrumentation().getTargetContext().getCacheDir(),"backup-flow-test.json");}
    @Override protected void tearDown()throws Exception{SharedPreferences saved=prefs;String raw=previous;File temporary=file;try{super.tearDown();}finally{if(raw==null)saved.edit().remove("entries").commit();else saved.edit().putString("entries",raw).commit();temporary.delete();}}
    private void waitForDocument(MainActivity a)throws Throwable{
        long deadline=android.os.SystemClock.uptimeMillis()+8000;final boolean[] busy={true};
        while(busy[0]&&android.os.SystemClock.uptimeMillis()<deadline){getInstrumentation().waitForIdleSync();runTestOnUiThread(()->{try{busy[0]=(Boolean)get(a,"documentBusy");}catch(Exception e){throw new RuntimeException(e);}});if(busy[0])android.os.SystemClock.sleep(50);}
        assertFalse("document worker must finish",busy[0]);
    }
    public void testExportReviewCancelThenMergeAndRepeat()throws Throwable{
        MainActivity a=getActivity();ObservationStore store=new ObservationStore(a);
        ObservationStore.Entry original=new ObservationStore.Entry("vega","织女星","北京",-86400000L,39.9,116.4,"旧笔记 ✨");
        ObservationStore.Entry second=new ObservationStore.Entry("moon","月球","上海",1770000000000L,31.2,121.4,"另一条记录");
        store.save(original);store.save(second);
        runTestOnUiThread(()->a.onActivityResult(3301,-1,new Intent().setData(Uri.fromFile(file))));waitForDocument(a);
        List<ObservationStore.Entry> decoded=JournalBackup.decode(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));assertEquals(2,decoded.size());
        store.delete(second.id);original.note="本机编辑后应保留";store.save(original);
        runTestOnUiThread(()->a.onActivityResult(3302,-1,new Intent().setData(Uri.fromFile(file))));waitForDocument(a);
        runTestOnUiThread(()->{try{((AlertDialog)get(a,"restoreDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();assertEquals(1,store.all().size());
        runTestOnUiThread(()->a.onActivityResult(3302,-1,new Intent().setData(Uri.fromFile(file))));waitForDocument(a);
        runTestOnUiThread(()->{try{((AlertDialog)get(a,"restoreDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
        assertEquals(2,store.all().size());for(ObservationStore.Entry e:store.all())if(e.id.equals(original.id))assertEquals("本机编辑后应保留",e.note);
        assertEquals(0,store.mergeImported(decoded));
    }
    public void testMalformedDocumentLeavesJournalUntouched()throws Throwable{
        MainActivity a=getActivity();ObservationStore store=new ObservationStore(a);store.save(new ObservationStore.Entry("moon","月球","北京",1770000000000L,39,116,"保留"));
        String before=prefs.getString("entries","");Files.write(file.toPath(),"{broken}".getBytes(StandardCharsets.UTF_8));
        runTestOnUiThread(()->a.onActivityResult(3302,-1,new Intent().setData(Uri.fromFile(file))));waitForDocument(a);assertEquals(before,prefs.getString("entries",""));
    }
    public void testBoundedReadsAndMalformedUtf8()throws Exception{
        try{BackupDocuments.read(new java.io.ByteArrayInputStream(new byte[JournalBackup.MAX_BYTES+1]));fail();}catch(java.io.IOException expected){}
        try{BackupDocuments.read(new java.io.ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}));fail();}catch(java.io.IOException expected){}
        assertEquals("月球",BackupDocuments.read(new java.io.ByteArrayInputStream("月球".getBytes(StandardCharsets.UTF_8))));
    }
    public void testInvalidSecondEntryDoesNotPartiallyMerge(){
        ObservationStore store=new ObservationStore(getInstrumentation().getTargetContext());
        ObservationStore.Entry kept=new ObservationStore.Entry("moon","月球","北京",1770000000000L,39,116,"保留");store.save(kept);
        String before=prefs.getString("entries","");
        ObservationStore.Entry valid=new ObservationStore.Entry("vega","织女星","北京",1770000000000L,39,116,"新记录");
        ObservationStore.Entry invalid=new ObservationStore.Entry("sirius","天狼星","北京",1770000000000L,Double.NaN,116,"无效");
        try{store.mergeImported(java.util.Arrays.asList(valid,invalid));fail("must reject whole import");}catch(IllegalArgumentException expected){}
        assertEquals(before,prefs.getString("entries",""));
    }
}
