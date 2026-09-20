package cn.jhun.sanjiaohu;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Center-crop without stretching; keeps the user's image entirely on-device. */
public final class WallpaperDrawable extends Drawable {
    private final Bitmap bitmap;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int gutter,header,stripColor,scrimColor;private final boolean fit;
    public WallpaperDrawable(Bitmap bitmap,int gutter,int header,int stripColor){this(bitmap,gutter,header,stripColor,0x00000000);}
    public WallpaperDrawable(Bitmap bitmap,int gutter,int header,int stripColor,int scrimColor){this(bitmap,gutter,header,stripColor,scrimColor,false);}
    public WallpaperDrawable(Bitmap bitmap,int gutter,int header,int stripColor,int scrimColor,boolean fit){this.bitmap=bitmap;this.gutter=gutter;this.header=header;this.stripColor=stripColor;this.scrimColor=scrimColor;this.fit=fit;}
    float scale(Rect b){float x=b.width()/(float)bitmap.getWidth(),y=b.height()/(float)bitmap.getHeight();return fit?Math.min(x,y):Math.max(x,y);}
    /** Match the center-crop in draw(), sampling the central region of a course. */
    int sample(RectF area){
        Rect bounds=getBounds();if(bounds.width()<=0||bounds.height()<=0||bitmap.isRecycled())return stripColor;
        float scale=scale(bounds);float left=bounds.centerX()-bitmap.getWidth()*scale/2,top=bounds.centerY()-bitmap.getHeight()*scale/2;int red=0,green=0,blue=0;
        for(int r=0;r<5;r++)for(int c=0;c<5;c++){
            float x=area.left+area.width()*(.15f+c*.175f),y=area.top+area.height()*(.2f+r*.15f);
            int px=Math.max(0,Math.min(bitmap.getWidth()-1,(int)((x-left)/scale))),py=Math.max(0,Math.min(bitmap.getHeight()-1,(int)((y-top)/scale)));
            int color=fit&&(x<left||y<top||x>=left+bitmap.getWidth()*scale||y>=top+bitmap.getHeight()*scale)?stripColor:bitmap.getPixel(px,py);red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);
        }return ThemePalette.overlay(Color.rgb(red/25,green/25,blue/25),scrimColor);
    }
    @Override public void draw(Canvas canvas){Rect b=getBounds();float scale=scale(b);float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.save();canvas.clipRect(b);if(fit)canvas.drawColor(stripColor);canvas.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),paint);if(Color.alpha(scrimColor)>0){Paint shade=new Paint();shade.setColor(scrimColor);canvas.drawRect(b.left,b.top,b.right,b.bottom,shade);}Paint strip=new Paint();strip.setColor(stripColor);canvas.drawRect(b.left,b.top,b.right,b.top+Math.min(header,b.height()/5),strip);canvas.drawRect(b.left,b.top,b.left+Math.min(gutter,b.width()/5),b.bottom,strip);canvas.restore();}
    @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
