package com.startrail.app;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.os.SystemClock;
import android.util.AttributeSet;
import com.startrail.app.astro.*;
import java.time.Instant;
import java.util.*;

/** Perspective camera inside a true ENU celestial sphere. Stars are projected in 3D, not rotated on a flat chart. */
public final class SkyMapView extends View {
    public interface Listener { void onStarSelected(Star star); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GalaxyBackground galaxy=new GalaxyBackground();
    private final ConstellationArtwork artwork=new ConstellationArtwork();
    private boolean showDeepSky=true;
    private boolean showGalaxy=true,showArtwork=true,showGrid=false;
    private final List<ConstellationLine> lines;
    private final Map<String,double[]> vectors=new HashMap<>();
    private final Map<String,PointF> hits=new LinkedHashMap<>();
    private final List<RectF> labels=new ArrayList<>();
    private final List<RectF> labelPool=new ArrayList<>();
    private final Map<String,PointF> hitPool=new HashMap<>();
    private final Map<String,DeepSkyCatalog.Entry> deepEntries=new HashMap<>();
    private final Map<String,String> displayNames=new HashMap<>();
    private final List<Star> labelCandidates=new ArrayList<>();
    private final List<double[]> equatorialBackdrop=new ArrayList<>();
    private final Path groundPath=new Path(), linePath=new Path();
    private final float[] groundX=new float[8],groundY=new float[8];
    private LinearGradient skyGradient,hudGradient;
    private int gradientHeight=-1,gradientMode=-1;

    private final List<double[]> backdrop=new ArrayList<>();
    private final ScaleGestureDetector scaleDetector;
    private List<BackgroundStars.Entry> backgroundStars=Collections.emptyList();
    private List<Star> stars;
    private SkyScene scene;
    private Listener listener;
    private Runnable manualListener;
    private Star selected;
    private Star guidanceTarget;
    private double azimuth=180,elevation=35,fov=75;
    private SkyCamera.Basis frame=SkyCamera.manualBasis(180,35,0);
    private boolean tracking,redMode,showLines=true,showLabels=true,dragging,scaled;
    private boolean directionReliable;
    private long alignedSince=-1,lastAlignmentHaptic=-1;
    private boolean alignmentHapticLatched;
    private static final long ALIGNMENT_STABLE_MS=500,ALIGNMENT_HAPTIC_COOLDOWN_MS=2000;
    private float lastX,lastY,downX,downY;
    private final float density;
    private String sensorStatus="手动浏览";
    private int onScreenCount;
    private final List<Star> labelOwners=new ArrayList<>();
    private boolean playbackMotion;
    private double sceneLatitude,motionOffsetSeconds;
    private long motionStarted;
    private static final long MOTION_DURATION_MS=120;

    void setPlaybackMotion(boolean enabled){
        playbackMotion=enabled;
        if(!enabled)motionOffsetSeconds=0;
        invalidate();
    }
    double motionOffsetAt(long uptime){
        double fraction=Math.max(0,Math.min(1,(uptime-motionStarted)/(double)MOTION_DURATION_MS));
        return playbackMotion?motionOffsetSeconds*(1-fraction):0;
    }
    long displayedTimeMillis(){
        return scene==null?0:scene.time.toEpochMilli()+Math.round(motionOffsetAt(SystemClock.uptimeMillis())*1000);
    }


    public SkyMapView(Context context){this(context,null,0);}
    public SkyMapView(Context context,AttributeSet attrs){this(context,attrs,0);}
    public SkyMapView(Context context,AttributeSet attrs,int defStyleAttr){
        this(context,attrs,defStyleAttr,Collections.emptyList(),Collections.emptyList());
    }
    public SkyMapView(Context context,List<Star> stars,List<ConstellationLine> lines){
        this(context,null,0,stars,lines);
    }
    private SkyMapView(Context context,AttributeSet attrs,int defStyleAttr,List<Star> stars,List<ConstellationLine> lines){
        super(context,attrs,defStyleAttr);this.stars=stars;this.lines=lines;density=getResources().getDisplayMetrics().density;
        setFocusable(true);setClickable(true);
        setContentDescription("3D天空。转动手机跟随朝向，拖动切换为手动浏览，双指缩放。点击天体查看详情。");
        scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                scaled=true;requestManual();fov=Math.max(25,Math.min(105,fov/detector.getScaleFactor()));invalidate();return true;
            }
        });
    }
    private float dp(float n){return density*n;}
    public void setListener(Listener listener){this.listener=listener;}
    public void setOnManualModeRequested(Runnable callback){manualListener=callback;}
    public void setSelected(Star star){selected=star;invalidate();}
    /** Sets the independently guided object; null hides target guidance. */
    public void setGuidanceTarget(Star star){if(!Objects.equals(guidanceTarget==null?null:guidanceTarget.id,star==null?null:star.id))resetAlignmentFeedback();guidanceTarget=star;invalidate();}
    public Star getGuidanceTarget(){return guidanceTarget;}
    public void setLayers(boolean lines,boolean labels){showLines=lines;showLabels=labels;invalidate();}
    public void setDecorations(boolean galaxy,boolean art,boolean grid){showGalaxy=galaxy;showArtwork=art;showGrid=grid;invalidate();}
    public void setRedMode(boolean enabled){redMode=enabled;invalidate();}
    public void setDeepSkyVisible(boolean enabled){showDeepSky=enabled;invalidate();}
    public float getChartRotation(){return (float)azimuth;}
    public void setChartRotation(float value){azimuth=value;manualFrame();}
    public double getViewingAltitude(){return elevation;}
    public double getFieldOfView(){return fov;}
    public boolean isTracking(){return tracking;}
    public int getVisibleScreenCount(){return onScreenCount;}
    public void setManualView(double az,double alt,double fieldOfView){azimuth=az;elevation=alt;fov=fieldOfView;manualFrame();}
    public void resetOrientation(){requestManual();azimuth=0;elevation=25;manualFrame();}
    public void setTracking(boolean enabled){if(tracking!=enabled)resetAlignmentFeedback();tracking=enabled;if(!enabled){sensorStatus="手动浏览";manualFrame();}invalidate();}
    /** Controls whether tracked orientation is trustworthy enough to declare alignment. */
    public void setDirectionReliable(boolean reliable){
        if(directionReliable!=reliable)resetAlignmentFeedback();
        directionReliable=reliable;
        invalidate();
    }
    public void setSensorStatus(String status){sensorStatus=status;invalidate();}
    public void setSensorOrientation(double[] right,double[] up,double[] forward,String status){
        sensorStatus=status;
        if(tracking && right!=null && up!=null && forward!=null){
            frame=new SkyCamera.Basis(right,up,forward);
            azimuth=Astronomy.normalizeDegrees(Math.toDegrees(Math.atan2(frame.forward[0],frame.forward[1])));
            elevation=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,frame.forward[2]))));
            updateAlignmentFeedback(SystemClock.uptimeMillis());
        }
        postInvalidateOnAnimation();
    }
    public void focus(Star target){
        selected=target;requestManual();
        Astronomy.HorizontalPosition h=scene==null?null:scene.positions.get(target.id);
        if(h!=null){azimuth=h.azimuth;elevation=h.altitude;manualFrame();}
    }
    private void requestManual(){if(tracking){tracking=false;resetAlignmentFeedback();if(manualListener!=null)manualListener.run();}sensorStatus="手动浏览";}
    private void manualFrame(){if(!tracking)frame=SkyCamera.manualBasis(azimuth,elevation,0);invalidate();}
    private void resetAlignmentFeedback(){alignedSince=-1;alignmentHapticLatched=false;}
    private void updateAlignmentFeedback(long now){
        if(!tracking||!directionReliable||guidanceTarget==null){resetAlignmentFeedback();return;}
        double[] target=vectors.get(guidanceTarget.id);
        if(target==null){resetAlignmentFeedback();return;}
        Guidance.Result result=Guidance.calculate(target,frame,2.0);
        if(result.belowHorizon||!result.aligned){resetAlignmentFeedback();return;}
        if(alignedSince<0)alignedSince=now;
        if(!alignmentHapticLatched&&now-alignedSince>=ALIGNMENT_STABLE_MS
                &&(lastAlignmentHaptic<0||now-lastAlignmentHaptic>=ALIGNMENT_HAPTIC_COOLDOWN_MS)){
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            lastAlignmentHaptic=now;alignmentHapticLatched=true;
        }
    }
    public void setBackgroundStars(List<BackgroundStars.Entry> stars){
        backgroundStars=BackgroundStars.forRendering(stars);
        equatorialBackdrop.clear();backdrop.clear();
        for(BackgroundStars.Entry star:backgroundStars){
            equatorialBackdrop.add(EquatorialTransform.fromRaDec(star.raHours,star.decDegrees));
            backdrop.add(new double[]{0,0,0,star.magnitude});
        }
    }
    public void update(Instant time,double latitude,double longitude){setScene(SkyScene.create(time,latitude,longitude),latitude,longitude);}
    void setScene(SkyScene newScene,double latitude,double longitude){
        long uptime=SystemClock.uptimeMillis();
        double offset=scene==null?0:(scene.time.toEpochMilli()-newScene.time.toEpochMilli())/1000.0+motionOffsetAt(uptime);
        motionOffsetSeconds=playbackMotion&&Math.abs(offset)<1800?offset:0;
        motionStarted=uptime;sceneLatitude=latitude;
        scene=newScene;stars=scene.objects;vectors.clear();
        galaxy.update(scene.time,latitude,longitude);artwork.update(scene.time,latitude,longitude);
        for(Star star:stars){Astronomy.HorizontalPosition p=scene.positions.get(star.id);vectors.put(star.id,SkyCamera.horizontalVector(p.altitude,p.azimuth));}
        EquatorialTransform transform=new EquatorialTransform(scene.time,latitude,longitude);
        for(int i=0;i<backdrop.size();i++)transform.transform(equatorialBackdrop.get(i),backdrop.get(i));
        deepEntries.clear();displayNames.clear();labelCandidates.clear();
        for(Star star:stars){
            DeepSkyCatalog.Entry deep=DeepSkyCatalog.find(star.id);
            if(deep!=null)deepEntries.put(star.id,deep);
            if(!hitPool.containsKey(star.id))hitPool.put(star.id,new PointF());
            String name=star.chineseName.isEmpty()?star.name:star.chineseName;
            if(deep!=null)name=(star.id.equals("omega_cen")?"NGC 5139":star.id.toUpperCase(Locale.ROOT))+" "+name;
            displayNames.put(star.id,name);
            if(star.magnitude<2.8||scene.solar.containsKey(star.id)||deep!=null)labelCandidates.add(star);
        }
        labelCandidates.sort(Comparator.comparingDouble(star->scene.solar.containsKey(star.id)?-20:star.magnitude));
        while(labelPool.size()<stars.size()+2)labelPool.add(new RectF());
        invalidate();
    }
    private SkyCamera.Point project(double[] v){return SkyCamera.project(v,frame,fov,getWidth(),getHeight());}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private void color(int normal,int red){paint.setColor(redMode?red:normal);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(getWidth()==0||getHeight()==0)return;
        float w=getWidth(),h=getHeight();
        SolarSystem.BodyPosition sun=scene==null?null:scene.solar.get("sun");
        boolean day=sun!=null&&sun.altitude> -6;
        int mode=redMode?2:day?1:0;
        if(gradientHeight!=getHeight()||gradientMode!=mode){
            gradientHeight=getHeight();gradientMode=mode;
            skyGradient=new LinearGradient(0,0,0,h,redMode?0xff13060b:day?0xff0b203e:0xff030814,
                redMode?0xff271019:day?0xff294e6b:0xff11263a,Shader.TileMode.CLAMP);
        }
        paint.setStyle(Paint.Style.FILL);paint.setShader(skyGradient);
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        SkyCamera.Basis physicalFrame=frame;
        double motionOffset=motionOffsetAt(SystemClock.uptimeMillis());
        SkyCamera.Basis skyFrame=motionOffset==0?frame:SkyMotion.forSky(frame,sceneLatitude,motionOffset);
        frame=skyFrame;
        if(showGalaxy)galaxy.draw(canvas,frame,fov,getWidth(),getHeight(),redMode,day);
        if(showArtwork)artwork.draw(canvas,frame,fov,getWidth(),getHeight(),redMode,day);
        frame=physicalFrame;
        if(showGrid)drawGrid(canvas);
        drawGround(canvas);
        frame=skyFrame;
        double focal=h/2/Math.tan(Math.toRadians(fov/2));
        for(double[] v:backdrop){
            double depth=dot(v,frame.forward);if(depth<=0.02)continue;
            float x=(float)(w/2+focal*dot(v,frame.right)/depth),y=(float)(h/2-focal*dot(v,frame.up)/depth);
            if(x<0||x>w||y<0||y>h)continue;
            color(day?0xff8097ae:0xffbac9de,0xffa95468);
            canvas.drawCircle(x,y,dp((float)Math.max(.45,1.45-v[3]*.13)),paint);
        }
        hits.clear();labels.clear();labelOwners.clear();onScreenCount=0;
        if(showLines){
            paint.setStrokeWidth(dp(.9f));color(0x886495b5,0x997e354d);
            for(ConstellationLine line:lines){double[] a=vectors.get(line.fromId),b=vectors.get(line.toId);if(a==null||b==null)continue;
                SkyCamera.Point p=project(a),q=project(b);if(p.depth>.05&&q.depth>.05)canvas.drawLine((float)p.x,(float)p.y,(float)q.x,(float)q.y,paint);
            }
        }
        for(Star star:stars){
            double[] v=vectors.get(star.id);if(v==null)continue;
            DeepSkyCatalog.Entry deep=deepEntries.get(star.id);
            if(deep!=null&&!showDeepSky&&(selected==null||!selected.id.equals(star.id))&&(guidanceTarget==null||!guidanceTarget.id.equals(star.id)))continue;
            SkyCamera.Point p=project(v);if(!p.onScreen)continue;
            float x=(float)p.x,y=(float)p.y;PointF hit=hitPool.get(star.id);hit.set(x,y);hits.put(star.id,hit);onScreenCount++;
            SolarSystem.BodyPosition body=scene==null?null:scene.solar.get(star.id);
            if(deep!=null)DeepSkyRenderer.draw(canvas,deep.kind,x,y,density,redMode);
            else if(body!=null)drawBody(canvas,body,x,y);
            else{
                float radius=dp((float)Math.max(1.1,2.3-star.magnitude*.36));
                color(0x2548bde1,0x40702840);canvas.drawCircle(x,y,radius*3,paint);
                color(0xffe1edff,0xffec8fa1);canvas.drawCircle(x,y,radius,paint);
            }
        }
        if(selected!=null&&hits.containsKey(selected.id)){
            PointF p=hits.get(selected.id);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.2f));color(0xffa7f5dc,0xffffa7b5);
            canvas.drawCircle(p.x,p.y,dp(22),paint);paint.setStyle(Paint.Style.FILL);label(canvas,selected,p,true);
        }
        if(guidanceTarget!=null&&guidanceTarget!=selected&&hits.containsKey(guidanceTarget.id))
            label(canvas,guidanceTarget,hits.get(guidanceTarget.id),true);
        if(showLabels){
            for(Star star:labelCandidates){
                if((selected==null||!star.id.equals(selected.id))&&(guidanceTarget==null||!star.id.equals(guidanceTarget.id))&&hits.containsKey(star.id))label(canvas,star,hits.get(star.id),false);
            }
        }
        frame=physicalFrame;
        drawGuidance(canvas);
        drawHud(canvas,day);
        if(playbackMotion&&motionOffset!=0)postInvalidateOnAnimation();
    }
    private void drawGrid(Canvas canvas){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.7f));color(0x333f7193,0x405d2638);
        for(int alt=-60;alt<=60;alt+=30)polyline(canvas,alt,true);
        for(int az=0;az<360;az+=30)polyline(canvas,az,false);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(13));
        String[] names={"北 N","东北","东 E","东南","南 S","西南","西 W","西北"};
        for(int i=0;i<8;i++){SkyCamera.Point p=project(SkyCamera.horizontalVector(3,i*45));if(p.onScreen){color(0xffa3d1c8,0xffd47789);canvas.drawText(names[i],(float)p.x,(float)p.y,paint);}}
    }
    private void polyline(Canvas canvas,int value,boolean latitude){
        Path path=linePath;path.rewind();boolean started=false;
        int end=latitude?360:90;
        for(int a=latitude?0:-90;a<=end;a+=2){SkyCamera.Point p=project(SkyCamera.horizontalVector(latitude?value:a,latitude?a:value));
            if(p.depth>.05){if(!started)path.moveTo((float)p.x,(float)p.y);else path.lineTo((float)p.x,(float)p.y);started=true;}else started=false;
        }canvas.drawPath(path,paint);
    }
    private double horizonValue(float x,float y,double focal){
        return frame.forward[2]+frame.right[2]*(x-getWidth()/2.0)/focal-frame.up[2]*(y-getHeight()/2.0)/focal;
    }
    private void drawGround(Canvas canvas){
        double focal=getHeight()/2.0/Math.tan(Math.toRadians(fov/2));
        float previousX=0,previousY=getHeight();
        double previousValue=horizonValue(previousX,previousY,focal);
        int count=0;
        for(int corner=0;corner<4;corner++){
            float x=corner==1||corner==2?getWidth():0,y=corner>=2?getHeight():0;
            double value=horizonValue(x,y,focal);
            if((value<=0)!=(previousValue<=0)){
                double t=previousValue/(previousValue-value);
                groundX[count]=(float)(previousX+t*(x-previousX));groundY[count++]=(float)(previousY+t*(y-previousY));
            }
            if(value<=0){groundX[count]=x;groundY[count++]=y;}
            previousX=x;previousY=y;previousValue=value;
        }
        if(count>0){
            groundPath.rewind();groundPath.moveTo(groundX[0],groundY[0]);
            for(int i=1;i<count;i++)groundPath.lineTo(groundX[i],groundY[i]);
            groundPath.close();color(0x450c171b,0x45160a0e);canvas.drawPath(groundPath,paint);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.3f));color(0xff50796f,0xff853749);polyline(canvas,0,true);paint.setStyle(Paint.Style.FILL);
    }
    private void drawBody(Canvas canvas,SolarSystem.BodyPosition body,float x,float y){
        float radius=dp(body.id.equals("sun")?13:body.id.equals("moon")?13:body.id.equals("jupiter")?7:5);
        int tint=body.id.equals("sun")?0xffffd378:body.id.equals("mars")?0xffdf9678:body.id.equals("venus")?0xffffe6b4:body.id.equals("jupiter")?0xffd7b593:body.id.equals("saturn")?0xffdcc99b:body.id.equals("uranus")?0xff8ccdd2:body.id.equals("neptune")?0xff6b9ce4:0xffccd4dc;
        if(redMode)tint=0xffd76d83;
        if(body.id.equals("sun")){
            paint.setShader(new RadialGradient(x,y,radius*3,new int[]{0x70ffcf74,0x00ffcf74},null,Shader.TileMode.CLAMP));canvas.drawCircle(x,y,radius*3,paint);paint.setShader(null);
        }
        if(body.id.equals("saturn")){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(tint);canvas.save();canvas.rotate(-25,x,y);canvas.drawOval(x-radius*1.9f,y-radius*.55f,x+radius*1.9f,y+radius*.55f,paint);canvas.restore();paint.setStyle(Paint.Style.FILL);}
        paint.setShader(new RadialGradient(x-radius*.35f,y-radius*.35f,radius*1.8f,new int[]{tint,redMode?0xff532232:0xff344555},null,Shader.TileMode.CLAMP));canvas.drawCircle(x,y,radius,paint);paint.setShader(null);
        if(body.id.equals("moon")){
            color(0xff263445,0xff381725);canvas.drawCircle(x,y,radius,paint);
            double[] sun=vectors.get("sun"),moon=vectors.get("moon");double rotation=0;
            if(sun!=null&&moon!=null){double along=dot(sun,moon);double[] tangent={sun[0]-along*moon[0],sun[1]-along*moon[1],sun[2]-along*moon[2]};rotation=Math.toDegrees(Math.atan2(-dot(tangent,frame.up),dot(tangent,frame.right)));}
            canvas.save();canvas.rotate((float)rotation,x,y);
            Path phase=new Path();phase.moveTo(x,y-radius);
            for(int i=-90;i<=90;i+=5){double a=Math.toRadians(i);phase.lineTo(x+(float)Math.cos(a)*radius,y+(float)Math.sin(a)*radius);}
            for(int i=90;i>=-90;i-=5){double a=Math.toRadians(i);phase.lineTo(x+(float)((1-2*body.illumination)*Math.cos(a))*radius,y+(float)Math.sin(a)*radius);}
            phase.close();color(0xffe6e9da,0xffe5a0ad);canvas.drawPath(phase,paint);canvas.restore();
        }
    }
    private boolean label(Canvas canvas,Star star,PointF p,boolean active){
        boolean solar=scene.solar.containsKey(star.id);String name=displayNames.get(star.id);
        if((active||solar)&&scene.positions.get(star.id).altitude<0)name+=" · 地平线下";
        paint.setTextSize(dp(active||solar?12:10));paint.setTextAlign(Paint.Align.LEFT);
        name=fitText(name,Math.max(dp(20),getWidth()-dp(20)));
        float width=paint.measureText(name),gap=dp(solar?18:8);
        RectF rect=labelPool.get(labels.size());
        for(int placement=0;placement<4;placement++){
            float x=placement%2==0?p.x+gap:p.x-width-gap;
            float y=p.y+(placement<2?-dp(6):dp(20));
            x=Math.max(dp(7),Math.min(getWidth()-dp(7)-width,x));
            y=Math.max(dp(82),Math.min(getHeight()-dp(62),y));
            rect.set(x-dp(3),y-dp(13),x+width+dp(3),y+dp(4));
            boolean collision=false;
            for(RectF other:labels)if(RectF.intersects(rect,other)){collision=true;break;}
            if(collision)continue;
            labels.add(rect);labelOwners.add(star);
            color(active?0xffa3f2d6:solar?0xfff7ddaa:0xffc1ccdd,0xffe291a3);
            canvas.drawText(name,x,y,paint);return true;
        }
        return false;
    }
    private void drawGuidance(Canvas canvas){
        if(guidanceTarget==null)return;
        double[] target=vectors.get(guidanceTarget.id);if(target==null)return;
        Guidance.Result result=Guidance.calculate(target,frame,2.0);
        String name=guidanceTarget.chineseName.isEmpty()?guidanceTarget.name:guidanceTarget.chineseName;
        float w=getWidth(),h=getHeight(),cx=w/2,cy=h/2;
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);
        if(result.belowHorizon){
            paint.setTextSize(dp(12));color(0xffd6c69a,0xffe39aad);
            canvas.drawText("目标在地平线下 · 当前无法对准",cx,h-dp(48),paint);
            canvas.drawText(fitText("「"+name+"」",w-dp(28)),cx,h-dp(32),paint);
            paint.setTypeface(Typeface.DEFAULT);return;
        }
        if(tracking&&!directionReliable){
            paint.setTextSize(dp(12));color(0xffd6c69a,0xffe39aad);
            canvas.drawText("方向信号不稳定 · 暂停对准判断",cx,h-dp(40),paint);
            paint.setTypeface(Typeface.DEFAULT);return;
        }
        if(result.aligned){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));color(0xff7ff0c9,0xffff91aa);
            canvas.drawCircle(cx,cy,dp(17),paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(12));
            canvas.drawText("已对准「"+name+"」",cx,cy+dp(38),paint);paint.setTypeface(Typeface.DEFAULT);return;
        }
        SkyCamera.Point projected=project(target);
        if(projected.onScreen){
            paint.setTextSize(dp(11));color(0xff8ff0d0,0xffff92a9);
            String cue=Guidance.directionCue(result.screenX,result.screenY,!tracking);
            canvas.drawText(fitText(cue+" · 「"+name+"」相距 "+Math.round(result.angularSeparation)+"°",w-dp(28)),cx,h-dp(40),paint);
            paint.setTypeface(Typeface.DEFAULT);return;
        }
        float left=dp(30),right=w-dp(30),top=dp(82),bottom=h-dp(64);
        double sx=result.screenX,sy=result.screenY;
        double scale=Math.min(sx==0?Double.POSITIVE_INFINITY:(sx>0?(right-cx)/sx:(left-cx)/sx),
                sy==0?Double.POSITIVE_INFINITY:(sy>0?(bottom-cy)/sy:(top-cy)/sy));
        float x=(float)(cx+sx*scale),y=(float)(cy+sy*scale);
        float angle=(float)Math.toDegrees(Math.atan2(sy,sx));
        canvas.save();canvas.translate(x,y);canvas.rotate(angle);
        Path arrow=new Path();arrow.moveTo(dp(15),0);arrow.lineTo(-dp(9),-dp(9));arrow.lineTo(-dp(4),0);arrow.lineTo(-dp(9),dp(9));arrow.close();
        color(0xff8ff0d0,0xffff92a9);canvas.drawPath(arrow,paint);canvas.restore();
        paint.setTextSize(dp(11));
        String cue=Guidance.directionCue(result.screenX,result.screenY,!tracking)+" · 「"+name+"」相距 "+Math.round(result.angularSeparation)+"°";
        canvas.drawText(fitText(cue,w-dp(28)),cx,h-dp(40),paint);
        paint.setTypeface(Typeface.DEFAULT);
    }
    private String fitText(String text,float maxWidth){
        if(paint.measureText(text)<=maxWidth)return text;
        String suffix="…";int end=text.length();
        while(end>0&&paint.measureText(text.substring(0,end)+suffix)>maxWidth)end--;
        return text.substring(0,end)+suffix;
    }
    private void drawHud(Canvas canvas,boolean day){
        float w=getWidth(),h=getHeight();if(hudGradient==null)hudGradient=new LinearGradient(0,0,0,dp(66),0xa0080e1a,0x00080e1a,Shader.TileMode.CLAMP);paint.setShader(hudGradient);canvas.drawRect(0,0,w,dp(66),paint);paint.setShader(null);
        paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(dp(11));color(0xffb5ecd9,0xffe29aae);
        canvas.drawText(tracking?"● 手机朝向":"◎ 手动浏览",dp(15),dp(25),paint);
        paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(String.format(Locale.CHINA,"方位 %03.0f°  仰角 %+.0f°",azimuth,elevation),w-dp(15),dp(25),paint);
        paint.setTextAlign(Paint.Align.LEFT);paint.setTextSize(dp(10));color(0xff9daec0,0xffb36b7f);
        String bottom=elevation<0?"地平线下 · 仅供浏览，实际不可见":day?"白天星图模拟 · 实际恒星难以看见":"日月与行星图标已放大 · 双指缩放";
        canvas.drawText(bottom,dp(14),h-dp(15),paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(String.format(Locale.CHINA,"%.0f°",fov),w-dp(14),h-dp(15),paint);
        color(0x55789eab,0x886e3446);paint.setStrokeWidth(dp(1));float cx=w/2,cy=h/2;
        canvas.drawLine(cx-dp(7),cy,cx-dp(3),cy,paint);canvas.drawLine(cx+dp(3),cy,cx+dp(7),cy,paint);canvas.drawLine(cx,cy-dp(7),cx,cy-dp(3),paint);canvas.drawLine(cx,cy+dp(3),cx,cy+dp(7),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        scaleDetector.onTouchEvent(event);float x=event.getX(),y=event.getY();
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=x;downY=lastY=y;dragging=false;scaled=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()>1||scaleDetector.isInProgress()){lastX=x;lastY=y;return true;}
                if(Math.hypot(x-downX,y-downY)>dp(8))dragging=true;
                if(dragging){requestManual();azimuth=Astronomy.normalizeDegrees(azimuth-(x-lastX)*fov/Math.max(1,getHeight()));elevation=Math.max(-89.8,Math.min(89.8,elevation+(y-lastY)*fov/Math.max(1,getHeight())));manualFrame();}
                lastX=x;lastY=y;return true;
            case MotionEvent.ACTION_POINTER_UP:
                int remaining=event.getActionIndex()==0?1:0;
                lastX=downX=event.getX(remaining);lastY=downY=event.getY(remaining);
                scaled=true;dragging=false;return true;
            case MotionEvent.ACTION_UP:
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                if(!dragging&&!scaled){performClick();Star closest=null;for(int i=0;i<labels.size();i++)if(labels.get(i).contains(x,y)){closest=labelOwners.get(i);break;}double distance=closest==null?dp(26):0;for(Star s:stars){PointF p=hits.get(s.id);if(p==null)continue;double d=Math.hypot(p.x-x,p.y-y);if(d<distance){distance=d;closest=s;}}if(closest!=null){setSelected(closest);if(listener!=null)listener.onStarSelected(closest);}}return true;
            case MotionEvent.ACTION_CANCEL:if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
