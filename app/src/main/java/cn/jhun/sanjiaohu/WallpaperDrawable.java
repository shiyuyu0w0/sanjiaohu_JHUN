package cn.jhun.sanjiaohu;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Center-crop without stretching; keeps the user's image entirely on-device. */
public final class WallpaperDrawable extends Drawable {
    private final Bitmap bitmap;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int gutter,header,stripColor;
    public WallpaperDrawable(Bitmap bitmap,int gutter,int header,int stripColor){this.bitmap=bitmap;this.gutter=gutter;this.header=header;this.stripColor=stripColor;}
    /** Match the center-crop in draw(), sampling the central region of a course. */
    int sample(RectF area){
        Rect bounds=getBounds();if(bounds.width()<=0||bounds.height()<=0||bitmap.isRecycled())return stripColor;
        float scale=Math.max(bounds.width()/(float)bitmap.getWidth(),bounds.height()/(float)bitmap.getHeight());float left=bounds.centerX()-bitmap.getWidth()*scale/2,top=bounds.centerY()-bitmap.getHeight()*scale/2;int red=0,green=0,blue=0;
        for(int r=0;r<5;r++)for(int c=0;c<5;c++){
            float x=area.left+area.width()*(.15f+c*.175f),y=area.top+area.height()*(.2f+r*.15f);
            int px=Math.max(0,Math.min(bitmap.getWidth()-1,(int)((x-left)/scale))),py=Math.max(0,Math.min(bitmap.getHeight()-1,(int)((y-top)/scale)));
            int color=bitmap.getPixel(px,py);red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);
        }return Color.rgb(red/25,green/25,blue/25);
    }
    @Override public void draw(Canvas canvas){Rect b=getBounds();float scale=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;canvas.save();canvas.clipRect(b);canvas.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),paint);Paint strip=new Paint();strip.setColor(stripColor);canvas.drawRect(b.left,b.top,b.right,b.top+Math.min(header,b.height()/5),strip);canvas.drawRect(b.left,b.top,b.left+Math.min(gutter,b.width()/5),b.bottom,strip);canvas.restore();}
    @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
