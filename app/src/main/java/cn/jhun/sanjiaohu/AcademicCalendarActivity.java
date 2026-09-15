package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;

/** Bundled original calendar, readable offline without browser or storage permission. */
public final class AcademicCalendarActivity extends Activity {
    ThemePalette theme;
    CalendarImage image;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        AppTheme.applySystemBars(this,theme);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(10),dp(18),dp(10));
        header.addView(action("‹","返回首页",()->finish()),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(dp(14),0,0,0);
        TextView title=text("校历",22,theme.text);title.setTypeface(Typeface.create("sans-serif-medium",0));titles.addView(title);titles.addView(text("2026—2027 学年",12,theme.muted));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        try(InputStream stream=getAssets().open("calendar-2026-2027.jpg")){
            Bitmap bitmap=BitmapFactory.decodeStream(stream);if(bitmap==null)throw new java.io.IOException("Calendar decode failed");
            image=new CalendarImage(bitmap);
            if(saved!=null){image.viewport.quarterTurns=Math.floorMod(saved.getInt("rotation",3),4);image.viewport.zoom=Math.max(1,Math.min(12,saved.getFloat("zoom",1)));}
            LinearLayout.LayoutParams picture=new LinearLayout.LayoutParams(-1,0,1);picture.setMargins(dp(12),dp(4),dp(12),dp(4));root.addView(image,picture);
            LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setPadding(dp(14),dp(8),dp(14),dp(4));
            addControl(controls,"−","缩小校历",()->image.zoom(.7f));
            addControl(controls,"＋","放大校历",()->image.zoom(1.5f));
            addControl(controls,"适应","显示完整校历",()->{image.viewport.fit();image.invalidate();});
            addControl(controls,"↻","顺时针旋转校历",()->{image.viewport.rotate();image.invalidate();});root.addView(controls);
            TextView hint=text("双指缩放 · 拖动查看 · 双击放大",12,theme.muted);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(6),0,dp(14));root.addView(hint);
        }catch(java.io.IOException e){
            TextView error=text("校历图片暂时无法打开，请重新进入。",15,theme.text);error.setGravity(Gravity.CENTER);root.addView(error,new LinearLayout.LayoutParams(-1,0,1));
        }
        setContentView(root);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(image!=null){out.putInt("rotation",image.viewport.quarterTurns);out.putFloat("zoom",image.viewport.zoom);}}
    void addControl(LinearLayout parent,String label,String description,Runnable run){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(4),0,dp(4),0);parent.addView(action(label,description,run),lp);}
    TextView action(String label,String description,Runnable run){
        TextView button=text(label,label.equals("适应")?14:24,theme.deepAccent);button.setGravity(Gravity.CENTER);button.setContentDescription(description);button.setFocusable(true);
        GradientDrawable fill=new GradientDrawable();fill.setColor(theme.entrySurface);fill.setCornerRadius(dp(15));
        GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),fill,mask));button.setOnClickListener(v->run.run());return button;
    }
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}

    final class CalendarImage extends View {
        final Bitmap bitmap;
        final CalendarViewport viewport;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final ScaleGestureDetector pinch;
        final GestureDetector gestures;
        CalendarImage(Bitmap bitmap){
            super(AcademicCalendarActivity.this);this.bitmap=bitmap;viewport=new CalendarViewport(bitmap.getWidth(),bitmap.getHeight());
            setContentDescription("江汉大学 2026 至 2027 学年校历，可使用下方按钮缩放和旋转");
            pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){viewport.zoomBy(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());invalidate();return true;}
            });
            gestures=new GestureDetector(getContext(),new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy){if(!pinch.isInProgress()&&current.getPointerCount()==1){viewport.pan(-dx,-dy);invalidate();}return true;}
                @Override public boolean onDoubleTap(MotionEvent e){if(viewport.zoom>1.1f)viewport.fit();else viewport.zoomBy(3,e.getX(),e.getY());invalidate();return true;}
                @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
            });
        }
        void zoom(float factor){viewport.zoomBy(factor,getWidth()/2f,getHeight()/2f);invalidate();}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){viewport.resize(w,h);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f+viewport.panX,getHeight()/2f+viewport.panY);
            float scale=viewport.scale();canvas.scale(scale,scale);canvas.rotate(viewport.quarterTurns*90);canvas.drawBitmap(bitmap,-bitmap.getWidth()/2f,-bitmap.getHeight()/2f,paint);canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);gestures.onTouchEvent(e);return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
