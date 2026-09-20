package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Local crop editor. No changes reach WallpaperStore until Apply is pressed. */
public final class WallpaperCropActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler handler=new Handler(Looper.getMainLooper());
    ThemePalette theme;CropView crop;Bitmap bitmap;TextView status,apply;LinearLayout tools;boolean saving,disposed;float timetableRatio;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));AppTheme.applySystemBars(this,theme);
        timetableRatio=getIntent().getFloatExtra("aspect",.65f);if(!Float.isFinite(timetableRatio)||timetableRatio<=0)timetableRatio=.65f;
        LinearLayout root=column();root.setBackgroundColor(theme.surface);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(12),dp(16),dp(12));header.addView(button("取消",()->onBackPressed()),new LinearLayout.LayoutParams(dp(60),dp(44)));TextView title=text("裁切壁纸",20,theme.text);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));apply=button("应用",()->save());apply.setEnabled(false);header.addView(apply,new LinearLayout.LayoutParams(dp(60),dp(44)));root.addView(header);
        FrameLayout preview=new FrameLayout(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));status=text("正在读取图片…",14,theme.muted);status.setGravity(Gravity.CENTER);preview.addView(status,new FrameLayout.LayoutParams(-1,-1));
        TextView hint=text("拖动图片定位 · 双指缩放\n拖动四角调整范围，自由比例会完整显示",13,theme.muted);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(12),dp(12),dp(12),dp(8));root.addView(hint);
        tools=column();tools.setPadding(dp(12),0,dp(12),dp(16));LinearLayout modes=new LinearLayout(this);add(modes,"课表比例",()->mode(timetableRatio));add(modes,"自由裁切",()->mode(0));add(modes,"完整原图",()->mode(bitmap.getWidth()/(float)bitmap.getHeight()));tools.addView(modes);
        LinearLayout zoom=new LinearLayout(this);add(zoom,"缩小",()->crop.zoom(.8f));add(zoom,"重置",()->{crop.geometry.reset();crop.invalidate();});add(zoom,"放大",()->crop.zoom(1.25f));tools.addView(zoom);enableTools(false);root.addView(tools);setContentView(root);
        if(getIntent().getData()==null){status.setText("未选择图片，请返回重试");return;}
        worker.execute(()->{
            try{Bitmap decoded=WallpaperStore.decode(this,getIntent().getData());handler.post(()->{
                if(disposed){decoded.recycle();return;}bitmap=decoded;crop=new CropView();if(saved!=null){crop.geometry.ratio=saved.getFloat("ratio",timetableRatio);crop.restored=saved.getFloatArray("crop");}preview.addView(crop,new FrameLayout.LayoutParams(-1,-1));status.setVisibility(View.GONE);apply.setEnabled(true);enableTools(true);
            });}catch(Exception|OutOfMemoryError e){handler.post(()->{if(!disposed)status.setText("图片无法读取，请返回选择另一张图片");});}
        });
    }
    void mode(float ratio){if(crop==null||saving)return;crop.geometry.setRatio(ratio);crop.invalidate();Toast.makeText(this,ratio==0?"自由裁切：拖动四角调整宽高":"已调整裁切比例",Toast.LENGTH_SHORT).show();}
    void save(){
        if(saving||crop==null||crop.geometry.scale<=0)return;saving=true;apply.setEnabled(false);apply.setText("保存中");enableTools(false);crop.setEnabled(false);
        final float[] bounds=crop.geometry.source();final Bitmap source=bitmap;
        worker.execute(()->{
            Bitmap output=null;try{
                float w=bounds[2]-bounds[0],h=bounds[3]-bounds[1],scale=Math.min(1,1600/Math.max(w,h));int outW=Math.max(1,Math.round(w*scale)),outH=Math.max(1,Math.round(h*scale));output=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(output);canvas.drawColor(Color.WHITE);canvas.scale(outW/w,outH/h);canvas.translate(-bounds[0],-bounds[1]);canvas.drawBitmap(source,0,0,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));WallpaperStore.save(this,output);
                getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("wallpaperUserCrop",true).putBoolean("manualTheme",false).remove("manualThemeColor").apply();
                handler.post(()->{if(disposed)return;setResult(RESULT_OK);finish();});
            }catch(Exception|OutOfMemoryError e){handler.post(()->{if(disposed)return;saving=false;apply.setText("应用");apply.setEnabled(true);enableTools(true);crop.setEnabled(true);Toast.makeText(this,"保存失败，原壁纸已保留，请重试",Toast.LENGTH_LONG).show();});}
            finally{if(output!=null)output.recycle();}
        });
    }
    @Override public void onBackPressed(){if(!saving)super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(crop!=null&&crop.geometry.scale>0){out.putFloatArray("crop",crop.geometry.source());out.putFloat("ratio",crop.geometry.ratio);}}
    @Override protected void onDestroy(){disposed=true;final Bitmap release=bitmap;worker.execute(()->{if(release!=null&&!release.isRecycled())release.recycle();});worker.shutdown();super.onDestroy();}
    void enableTools(boolean enabled){for(int i=0;i<tools.getChildCount();i++){LinearLayout row=(LinearLayout)tools.getChildAt(i);for(int j=0;j<row.getChildCount();j++){row.getChildAt(j).setEnabled(enabled);row.getChildAt(j).setAlpha(enabled?1:.4f);}}}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView text(String label,int size,int color){TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(color);return v;}
    TextView button(String label,Runnable action){TextView v=text(label,14,theme.deepAccent);v.setGravity(Gravity.CENTER);v.setFocusable(true);GradientDrawable bg=new GradientDrawable();bg.setColor(theme.entrySurface);bg.setCornerRadius(dp(14));v.setBackground(bg);v.setOnClickListener(w->action.run());return v;}
    void add(LinearLayout row,String label,Runnable action){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(button(label,action),lp);}
    int dp(float n){return Math.round(getResources().getDisplayMetrics().density*n);}

    final class CropView extends View {
        final WallpaperCropGeometry geometry=new WallpaperCropGeometry(bitmap.getWidth(),bitmap.getHeight(),timetableRatio);
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final ScaleGestureDetector pinch;
        int handle=-1,pointer=-1;float lastX,lastY;float[] restored;
        CropView(){super(WallpaperCropActivity.this);setContentDescription("壁纸裁切预览：拖动图片、双指缩放或拖动四角调整选框");
            pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){geometry.zoom(d.getScaleFactor(),d.getFocusX(),d.getFocusY());invalidate();return true;}});
        }
        void zoom(float factor){geometry.zoom(factor,(geometry.left+geometry.right)/2,(geometry.top+geometry.bottom)/2);invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){geometry.resize(w,h);if(restored!=null){geometry.restore(restored);restored=null;}}
        @Override protected void onDraw(Canvas canvas){
            if(disposed||bitmap==null||bitmap.isRecycled())return;
            canvas.drawColor(theme.controlSurface);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);canvas.save();canvas.translate(geometry.imageX,geometry.imageY);canvas.scale(geometry.scale,geometry.scale);canvas.drawBitmap(bitmap,0,0,paint);canvas.restore();
            float l=geometry.left,t=geometry.top,r=geometry.right,b=geometry.bottom;paint.setColor(0x99000000);canvas.drawRect(0,0,getWidth(),t,paint);canvas.drawRect(0,b,getWidth(),getHeight(),paint);canvas.drawRect(0,t,l,b,paint);canvas.drawRect(r,t,getWidth(),b,paint);
            paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRect(l,t,r,b,paint);paint.setColor(0x88ffffff);paint.setStrokeWidth(dp(1));for(int i=1;i<3;i++){canvas.drawLine(l+(r-l)*i/3,t,l+(r-l)*i/3,b,paint);canvas.drawLine(l,t+(b-t)*i/3,r,t+(b-t)*i/3,paint);}
            paint.setStyle(Paint.Style.FILL);paint.setColor(theme.primary);for(float[] point:new float[][]{{l,t},{r,t},{r,b},{l,b}})canvas.drawCircle(point[0],point[1],dp(7),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!isEnabled())return true;pinch.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:pointer=e.getPointerId(0);lastX=e.getX();lastY=e.getY();handle=-1;float best=dp(28);float[][] corners={{geometry.left,geometry.top},{geometry.right,geometry.top},{geometry.right,geometry.bottom},{geometry.left,geometry.bottom}};for(int i=0;i<4;i++){float d=(float)Math.hypot(lastX-corners[i][0],lastY-corners[i][1]);if(d<best){best=d;handle=i;}}return true;
                case MotionEvent.ACTION_POINTER_DOWN:handle=-1;return true;
                case MotionEvent.ACTION_MOVE:int index=e.findPointerIndex(pointer);if(index>=0){float x=e.getX(index),y=e.getY(index);if(!pinch.isInProgress()&&e.getPointerCount()==1){if(handle>=0)geometry.corner(handle,x,y);else geometry.pan(x-lastX,y-lastY);invalidate();}lastX=x;lastY=y;}return true;
                case MotionEvent.ACTION_POINTER_UP:int next=e.getActionIndex()==0?1:0;pointer=e.getPointerId(next);lastX=e.getX(next);lastY=e.getY(next);handle=-1;return true;
                case MotionEvent.ACTION_UP:performClick();
                case MotionEvent.ACTION_CANCEL:pointer=-1;handle=-1;return true;
            }return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
