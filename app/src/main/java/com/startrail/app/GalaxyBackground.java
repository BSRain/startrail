package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.GalacticCoordinates;
import com.startrail.app.astro.SkyCamera;
import java.time.Instant;

/** Original procedural Milky Way artwork aligned with the physical Galactic plane. */
public final class GalaxyBackground {
    private float opacity = 1f;

    public void setOpacity(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("opacity");
        opacity = Math.max(0f, Math.min(1f, value));
    }

    private static final int CLOUDS=48;
    private final double[][] clouds=new double[CLOUDS][3], dust=new double[CLOUDS][3];
    private final float[] strength=new float[CLOUDS], spread=new float[CLOUDS];
    private final Bitmap cloudTexture=texture(false), dustTexture=texture(true);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();
    private final PorterDuffColorFilter lavender=new PorterDuffColorFilter(Color.rgb(125,116,190),PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter nightDust=new PorterDuffColorFilter(Color.rgb(10,9,28),PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter redCloud=new PorterDuffColorFilter(Color.rgb(112,28,32),PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter redDust=new PorterDuffColorFilter(Color.rgb(25,3,5),PorterDuff.Mode.SRC_IN);
    private boolean ready;

    public GalaxyBackground() {}

    /** Precomputes the modest sky-node set whenever observer state changes. */
    public void update(Instant time,double latitude,double longitude){
        if(time==null)throw new IllegalArgumentException("time");
        for(int i=0;i<CLOUDS;i++){
            double l=i*360.0/CLOUDS;
            double wisp=2.1*Math.sin(Math.toRadians(l*3+17))+0.8*Math.sin(Math.toRadians(l*7-11));
            clouds[i]=horizontal(l,wisp,time,latitude,longitude);
            dust[i]=horizontal(l+1.7,0.8*Math.sin(Math.toRadians(l*5+42)),time,latitude,longitude);
            double distance=Math.min(l,360-l);
            float bulge=(float)Math.exp(-distance*distance/1500.0);
            strength[i]=(float)(0.58+0.22*bulge+0.10*Math.sin(Math.toRadians(l*4+25)));
            spread[i]=(float)(0.84+0.32*bulge+0.09*Math.sin(Math.toRadians(l*2-8)));
        }
        ready=true;
    }

    public void draw(Canvas canvas,SkyCamera.Basis frame,double fov,int width,int height,boolean redMode,boolean day){
        if(opacity==0f||!ready||canvas==null||frame==null||width<=0||height<=0)return;
        double focal=height*.5/Math.tan(Math.toRadians(fov)*.5);
        float fade=(day?.25f:1f)*opacity;
        paint.setColorFilter(redMode?redCloud:lavender);
        for(int i=0;i<CLOUDS;i++){
            SkyCamera.Point p=SkyCamera.project(clouds[i],frame,fov,width,height);
            if(!p.inFront)continue;
            float radius=(float)(focal*Math.tan(Math.toRadians(16*spread[i]))/p.depth);
            radius=Math.min(radius,Math.max(width,height)*.72f);
            if(!intersects(p,radius,width,height))continue;
            paint.setAlpha(clamp((int)(80*strength[i]*fade),0,86));
            place(p,radius); canvas.drawBitmap(cloudTexture,null,target,paint);
        }
        paint.setColorFilter(redMode?redDust:nightDust);
        for(int i=0;i<CLOUDS;i++){
            SkyCamera.Point p=SkyCamera.project(dust[i],frame,fov,width,height);
            if(!p.inFront)continue;
            float radius=(float)(focal*Math.tan(Math.toRadians(5.4+1.2*Math.sin(Math.toRadians(i*47.0))))/p.depth);
            radius=Math.min(radius,Math.max(width,height)*.72f);
            if(!intersects(p,radius,width,height))continue;
            paint.setAlpha((int)(42*fade)); place(p,radius); canvas.drawBitmap(dustTexture,null,target,paint);
        }
        paint.setColorFilter(null);
    }

    private static double[] horizontal(double l,double b,Instant time,double lat,double lon){
        GalacticCoordinates.Equatorial eq=GalacticCoordinates.galacticToEquatorial(l,b);
        Astronomy.HorizontalPosition hp=Astronomy.horizontal(eq.raHours,eq.decDegrees,time,lat,lon);
        return SkyCamera.horizontalVector(hp.altitudeDegrees,hp.azimuthDegrees);
    }
    private void place(SkyCamera.Point p,float r){target.set((float)p.x-r,(float)p.y-r,(float)p.x+r,(float)p.y+r);}
    private static boolean intersects(SkyCamera.Point p,float r,int w,int h){return p.x+r>=0&&p.x-r<=w&&p.y+r>=0&&p.y-r<=h;}
    private static Bitmap texture(boolean dark){
        int s=96; Bitmap b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(s/2f,s/2f,s/2f,new int[]{Color.argb(dark?205:190,255,255,255),Color.argb(dark?95:72,255,255,255),Color.TRANSPARENT},new float[]{0,dark?.28f:.42f,1},Shader.TileMode.CLAMP));
        c.drawCircle(s/2f,s/2f,s/2f,p); return b;
    }
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
}
