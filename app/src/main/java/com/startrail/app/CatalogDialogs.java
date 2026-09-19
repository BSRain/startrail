package com.startrail.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import com.startrail.app.astro.*;
import java.util.*;
import java.util.function.*;

/** Catalogue/favorites modals use an immutable observing scene captured at opening. */
final class CatalogDialogs {
    private final Activity activity;
    private final DialogUi ui;
    private final SkyScene scene;
    private final FavoritesStore favorites;
    private final boolean live;
    private final String timestamp;
    private final Function<Star,String> names,coordinates;
    private final Consumer<Star> details;
    private final Runnable onChanged;
    CatalogDialogs(Activity activity,DialogUi ui,SkyScene scene,FavoritesStore favorites,boolean live,String timestamp,
            Function<Star,String> names,Function<Star,String> coordinates,Consumer<Star> details,Runnable onChanged){
        this.activity=activity;this.ui=ui;this.scene=scene;this.favorites=favorites;this.live=live;
        this.timestamp=timestamp;this.names=names;this.details=details;this.onChanged=onChanged;
        Map<String,String> capturedCoordinates=new HashMap<>();
        for(Star star:scene.objects)capturedCoordinates.put(star.id,coordinates.apply(star));
        this.coordinates=star->capturedCoordinates.getOrDefault(star.id,"");
    }
    private String getString(int id,Object...args){return activity.getString(id,args);}
    void showDeepSkyCatalog(){
        LinearLayout content=ui.dialogColumn();
        content.addView(ui.text(getString(R.string.catalog_get_string_01),12,ui.muted(),false));
        LinearLayout filters=ui.row();content.addView(filters);TextView count=ui.text("",12,ui.accent(),true);content.addView(count);
        ScrollView scroll=new ScrollView(activity);LinearLayout list=ui.column();scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,ui.dp(350)));
        AlertDialog dialog=new DialogStyle.Builder(activity).setTitle(getString(R.string.main_search_03)).setView(content).setNegativeButton(getString(R.string.close),null).create();
        final int[] selectedCategory={0};String[] labels={getString(R.string.catalog_get_string_02),getString(R.string.catalog_get_string_03),getString(R.string.catalog_get_string_04),getString(R.string.catalog_get_string_05)};
        Runnable fill=()->{
            list.removeAllViews();int total=0;
            for(DeepSkyCatalog.Entry entry:DeepSkyCatalog.entries()){
                int category=entry.kind.equals("galaxy")?1:entry.kind.endsWith("cluster")?2:3;
                if(selectedCategory[0]!=0&&category!=selectedCategory[0])continue;
                Star target=scene.find(entry.star.id);if(target==null)continue;total++;
                LinearLayout card=ui.card();card.addView(ui.text((target.id.equals("omega_cen")?"NGC 5139":target.id.toUpperCase(Locale.ROOT))+" · "+names.apply(target),16,ui.ink(),true));
                card.addView(ui.text(entry.label+" · "+target.constellation+"\n"+coordinates.apply(target)+(scene.positions.get(target.id).altitude<0?getString(R.string.main_show_observing_profile_10):""),11,ui.accent(),false));
                card.addView(ui.text(entry.equipment,12,ui.muted(),false));card.setOnClickListener(v->{dialog.dismiss();details.accept(target);});list.addView(card,ui.spaced());
            }
            count.setText(getString(R.string.ui_catalog_count,labels[selectedCategory[0]],total,(live?getString(R.string.time_live):getString(R.string.time_simulated_instant))));
        };
        for(int i=0;i<labels.length;i++){final int category=i;filters.addView(ui.button(labels[i],()->{selectedCategory[0]=category;fill.run();}),new LinearLayout.LayoutParams(0,ui.dp(44),1));}
        fill.run();dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(ui.round(ui.panel(),20,ui.line()));content.setBackgroundColor(ui.panel());
    }
    void showFavorites(){
        
        LinearLayout content=ui.dialogColumn();TextView summary=ui.text("",12,ui.muted(),false);content.addView(summary);
        ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(ui.panel());content.setBackgroundColor(ui.panel());LinearLayout list=ui.column();scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,ui.dp(Math.min(360,Math.max(100,favorites.ids().size()*82)))));
        AlertDialog dialog=new DialogStyle.Builder(activity).setTitle(getString(R.string.catalog_get_string_07)).setView(content).setNegativeButton(getString(R.string.close),null).create();
        Runnable fill=()->{
            list.removeAllViews();List<String> ids=favorites.ids();summary.setText(getString(R.string.ui_favorites_summary,ids.size(),(live?getString(R.string.catalog_get_string_08):getString(R.string.time_simulated_instant)),timestamp));
            if(ids.isEmpty())list.addView(ui.text(getString(R.string.catalog_get_string_09),14,ui.muted(),false));
            for(String id:ids){Star target=scene.find(id);LinearLayout item=ui.row();
                TextView open=ui.button(target==null?getString(R.string.catalog_get_string_10)+id:names.apply(target)+"\n"+coordinates.apply(target),()->{if(target!=null){dialog.dismiss();details.accept(target);}});
                item.addView(open,new LinearLayout.LayoutParams(0,-2,1));item.addView(ui.button(getString(R.string.remove),()->{favorites.set(id,false);showFavoritesAfterRemoval(dialog); }));list.addView(item);
            }
        };
        fill.run();dialog.setOnDismissListener(d->{if(!activity.isFinishing()&&!activity.isDestroyed())onChanged.run();});dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(ui.round(ui.panel(),20,ui.line()));
    }
    private void showFavoritesAfterRemoval(AlertDialog previous){previous.dismiss();showFavorites();}
}
