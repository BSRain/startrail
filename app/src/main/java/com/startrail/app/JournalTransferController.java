package com.startrail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns document streams, pending work and restore review for the Activity lifecycle. */
final class JournalTransferController {
    private final Activity activity;
    private final ObservationStore journal;
    private final Runnable pause, onRestored;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService documentExecutor = Executors.newSingleThreadExecutor();
    private boolean documentBusy, destroyed;
    private AlertDialog restoreDialog;
    private Toast activeToast;
    private static final int EXPORT_JOURNAL=3301, IMPORT_JOURNAL=3302;
    JournalTransferController(Activity activity, ObservationStore journal, Runnable pause, Runnable onRestored) {
        this.activity=activity; this.journal=journal; this.pause=pause; this.onRestored=onRestored;
    }
    void destroy() {
        destroyed=true;
        if(restoreDialog!=null)restoreDialog.dismiss();
        if(activeToast!=null)activeToast.cancel();
        documentExecutor.shutdownNow(); handler.removeCallbacksAndMessages(null);
    }
    private String getString(int id,Object...args){return activity.getString(id,args);}
    private void toast(String message){if(activeToast!=null)activeToast.cancel();activeToast=Toast.makeText(activity,message,Toast.LENGTH_LONG);activeToast.show();}
    void chooseBackupDocument(boolean export){
        pause.run();if(documentBusy){toast(getString(R.string.transfer_toast_01));return;}
        Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(export?"application/json":"*/*");
        if(export)intent.putExtra(Intent.EXTRA_TITLE,"startrail-journal-"+java.time.LocalDate.now()+".json");
        try{activity.startActivityForResult(intent,export?EXPORT_JOURNAL:IMPORT_JOURNAL);}catch(android.content.ActivityNotFoundException ex){toast(getString(R.string.transfer_toast_02));}
    }
    void onActivityResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=EXPORT_JOURNAL&&requestCode!=IMPORT_JOURNAL)return;
        if(resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();if(documentBusy){toast(getString(R.string.transfer_toast_03));return;}
        if(requestCode==EXPORT_JOURNAL){
            final List<ObservationStore.Entry> snapshot;
            try{snapshot=journal.all();}catch(IllegalStateException ex){toast(ex.getMessage());return;}
            documentBusy=true;toast(getString(R.string.transfer_toast_04));
            documentExecutor.execute(()->{
                try{
                    byte[] bytes=JournalBackup.encode(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    try(java.io.OutputStream output=activity.getContentResolver().openOutputStream(uri,"wt")){
                        if(output==null)throw new java.io.IOException(getString(R.string.transfer_toast_05));output.write(bytes);output.flush();
                    }
                    finishDocument(()->toast(getString(R.string.transfer_toast_06)+snapshot.size()+getString(R.string.transfer_toast_07)));
                }catch(Exception ex){finishDocument(()->toast(getString(R.string.transfer_toast_08)));}
            });
        }else{
            documentBusy=true;toast(getString(R.string.transfer_toast_09));
            documentExecutor.execute(()->{
                try(java.io.InputStream input=activity.getContentResolver().openInputStream(uri)){
                    List<ObservationStore.Entry> incoming=JournalBackup.decode(BackupDocuments.read(input));
                    finishDocument(()->previewRestore(incoming));
                }catch(Exception ex){finishDocument(()->toast(getString(R.string.transfer_toast_10)));}
            });
        }
    }
    private void finishDocument(Runnable result){handler.post(()->{documentBusy=false;if(!destroyed&&!activity.isFinishing()&&!activity.isDestroyed())result.run();});}
    private void previewRestore(List<ObservationStore.Entry> incoming){
        final int additions;
        try{java.util.Set<String> ids=new java.util.HashSet<>();for(ObservationStore.Entry e:journal.all())ids.add(e.id);int count=0;for(ObservationStore.Entry e:incoming)if(!ids.contains(e.id))count++;additions=count;}
        catch(IllegalStateException ex){toast(ex.getMessage());return;}
        restoreDialog=new DialogStyle.Builder(activity).setTitle(getString(R.string.transfer_preview_restore_01))
            .setMessage(getString(R.string.transfer_preview_restore_02)+incoming.size()+getString(R.string.transfer_preview_restore_03)+additions+getString(R.string.transfer_preview_restore_04))
            .setNegativeButton(getString(R.string.cancel),null).setPositiveButton(getString(R.string.transfer_preview_restore_05),(d,w)->{
                try{int added=journal.mergeImported(incoming);toast(getString(R.string.transfer_preview_restore_06)+added+getString(R.string.transfer_toast_07));onRestored.run();}
                catch(IllegalStateException|IllegalArgumentException ex){toast(getString(R.string.transfer_preview_restore_07)+ex.getMessage());}
            }).show();
    }
}
