package com.startrail.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.util.AttributeSet;
import com.startrail.app.astro.ObservingEvents;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Offline altitude chart with a selectable sample; local horizon always remains visible. */
final class AltitudeChartView extends View {
    interface Listener { void selected(int index); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final ObservingEvents.Result result;
    private final boolean red;
    private final float density;
    private Listener listener;
    private int selected;
    public AltitudeChartView(Context context){this(context,null,0);}
    public AltitudeChartView(Context context,AttributeSet attrs){this(context,attrs,0);}
    public AltitudeChartView(Context context,AttributeSet attrs,int defStyleAttr){
        this(context,attrs,defStyleAttr,null,false);
    }
    AltitudeChartView(Context context,ObservingEvents.Result result,boolean red){
        this(context,null,0,result,red);
    }
    private AltitudeChartView(Context context,AttributeSet attrs,int defStyleAttr,ObservingEvents.Result result,boolean red){
        super(context,attrs,defStyleAttr);this.result=result;this.red=red;density=getResources().getDisplayMetrics().density;
        setClickable(true);setFocusable(true);setContentDescription("天体高度曲线，横轴时间，纵轴高度。也可使用下方滑块选择时刻。");
    }
    void setListener(Listener listener){this.listener=listener;}
    void select(int index){if(result==null||result.samples.isEmpty())return;selected=Math.max(0,Math.min(result.samples.size()-1,index));invalidate();}
    private float dp(float n){return density*n;}
    private float x(int index){return dp(34)+(getWidth()-dp(44))*(float)((result.samples.get(index).time.toEpochMilli()-result.start.toEpochMilli())/(double)Math.max(1,result.end.toEpochMilli()-result.start.toEpochMilli()));}
    private float y(double altitude){return dp(14)+(getHeight()-dp(48))*(float)((90-altitude)/180);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(result==null||result.samples.isEmpty())return;
        canvas.drawColor(AppPalette.bg(red));
        for(int i=0;i<result.samples.size()-1;i++){
            double sun=result.samples.get(i).sunAltitude;
            paint.setColor(red?(sun<=-18?0xff190b12:0xff3c1821):(sun<=-18?0xff0a1425:sun<=-6?0xff202c44:0xff32465d));
            canvas.drawRect(x(i),dp(14),x(i+1)+1,getHeight()-dp(34),paint);
        }
        paint.setTextSize(dp(10));paint.setTextAlign(Paint.Align.RIGHT);
        for(int altitude=-60;altitude<=60;altitude+=30){
            paint.setColor(altitude==0?AppPalette.accent(red):AppPalette.line(red));paint.setStrokeWidth(dp(altitude==0?1.3f:.7f));
            canvas.drawLine(dp(34),y(altitude),getWidth()-dp(10),y(altitude),paint);
            paint.setColor(AppPalette.muted(red));canvas.drawText(altitude+"°",dp(30),y(altitude)+dp(3),paint);
        }
        path.rewind();
        for(int i=0;i<result.samples.size();i++){float x=x(i),y=y(result.samples.get(i).altitude);if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(AppPalette.accent(red));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm",Locale.CHINA).withZone(ZoneId.systemDefault());
        for(int tick=0;tick<(result.samples.size()==1?1:5);tick++){int i=(result.samples.size()-1)*tick/4;paint.setTextAlign(tick==0?Paint.Align.LEFT:tick==4?Paint.Align.RIGHT:Paint.Align.CENTER);paint.setColor(AppPalette.muted(red));canvas.drawText(format.format(result.samples.get(i).time),x(i),getHeight()-dp(12),paint);}
        paint.setColor(red?0xffef9dab:0xffffd17d);canvas.drawLine(x(selected),dp(14),x(selected),getHeight()-dp(34),paint);canvas.drawCircle(x(selected),y(result.samples.get(selected).altitude),dp(4),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(result==null||result.samples.isEmpty())return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            int index=0;float nearest=Float.MAX_VALUE;for(int i=0;i<result.samples.size();i++){float distance=Math.abs(event.getX()-x(i));if(distance<nearest){nearest=distance;index=i;}}select(index);if(listener!=null)listener.selected(selected);return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
