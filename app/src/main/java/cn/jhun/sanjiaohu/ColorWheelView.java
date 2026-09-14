package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Hue around the circle, saturation from its center. Brightness is edited separately. */
final class ColorWheelView extends View {
    interface Listener {void changed(float hue,float saturation);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float hue,saturation=1,value=1,cx,cy,radius;
    private Shader shader;
    private Listener listener;
    private boolean dragging;
    ColorWheelView(Context context){super(context);density=getResources().getDisplayMetrics().density;setFocusable(true);setClickable(true);}
    void setListener(Listener callback){listener=callback;}
    void setColor(float h,float s,float v){hue=h;saturation=s;value=v;setContentDescription("调色盘，色相 "+Math.round(h)+" 度，饱和度 "+Math.round(s*100)+"%，也可通过颜色代码精确输入");invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        cx=w/2f;cy=h/2f;radius=Math.max(1,Math.min(w,h)/2f-12*density);
        Shader sweep=new SweepGradient(cx,cy,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null);
        Shader radial=new RadialGradient(cx,cy,radius,new int[]{Color.WHITE,0x00ffffff},null,Shader.TileMode.CLAMP);
        shader=new ComposeShader(sweep,radial,PorterDuff.Mode.SRC_OVER);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);paint.setStyle(Paint.Style.FILL);paint.setShader(shader);canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
        double angle=Math.toRadians(hue);float x=cx+(float)Math.cos(angle)*radius*saturation,y=cy+(float)Math.sin(angle)*radius*saturation;
        paint.setColor(0x55000000);canvas.drawCircle(x,y,9*density,paint);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,7.5f*density,paint);paint.setColor(Color.HSVToColor(new float[]{hue,saturation,value}));canvas.drawCircle(x,y,5*density,paint);
    }
    private void pick(float x,float y){
        float dx=x-cx,dy=y-cy;hue=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);saturation=Math.min(1,(float)Math.hypot(dx,dy)/radius);
        if(listener!=null)listener.changed(hue,saturation);invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:if(Math.hypot(event.getX()-cx,event.getY()-cy)>radius+10*density)return false;dragging=true;getParent().requestDisallowInterceptTouchEvent(true);pick(event.getX(),event.getY());return true;
            case MotionEvent.ACTION_MOVE:if(!dragging)return false;pick(event.getX(),event.getY());return true;
            case MotionEvent.ACTION_UP:if(!dragging)return false;pick(event.getX(),event.getY());dragging=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
            case MotionEvent.ACTION_CANCEL:dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_DPAD_LEFT)hue=(hue+355)%360;else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)hue=(hue+5)%360;
        else if(key==KeyEvent.KEYCODE_DPAD_UP)saturation=Math.min(1,saturation+.05f);else if(key==KeyEvent.KEYCODE_DPAD_DOWN)saturation=Math.max(0,saturation-.05f);else return super.onKeyDown(key,event);
        if(listener!=null)listener.changed(hue,saturation);invalidate();return true;
    }
}
