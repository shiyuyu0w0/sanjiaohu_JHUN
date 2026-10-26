package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/** Device regression: all children must be visible after the very first measure/layout pass. */
public final class GridInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        final Bundle result=new Bundle();
        try {
            runOnMainSync(()->{
                wallpaperCoverage();
                for(int[] size:new int[][]{{320,400},{360,520},{1080,1500}}){
                    WeekGridView grid=new WeekGridView(getTargetContext(),25,32);
                    for(int day=1;day<=7;day++)for(int p=1;p<=12;p++){
                        TextView card=new TextView(getTargetContext());card.setText("测试课程");grid.add(card,WeekGridView.CELL,day,p,p,0,1);
                    }
                    if(grid.getChildCount()!=84)throw new AssertionError("children missing before measurement");
                    grid.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));
                    grid.layout(0,0,size[0],size[1]);
                    for(int i=0;i<84;i++){
                        View v=grid.getChildAt(i);
                        if(v.getMeasuredWidth()<=0||v.getMeasuredHeight()<=0||v.getWidth()<=0||v.getHeight()<=0)throw new AssertionError("unmeasured child in first layout: "+i);
                        if(v.getRight()>size[0]||v.getBottom()>size[1])throw new AssertionError("child outside grid");
                    }
                }
            });
            result.putString("stream","PASS: real Android first-pass layout and wallpaper edge coverage at changing viewport sizes");finish(-1,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error);finish(0,result);}
    }
    private void wallpaperCoverage(){
        // A saved portrait crop must cover a shorter uncalibrated timetable and landscape too.
        Bitmap source=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);source.eraseColor(Color.CYAN);
        WallpaperDrawable wallpaper=new WallpaperDrawable(source,33,32,Color.WHITE);
        try{
            for(int[] size:new int[][]{{360,640},{360,550},{361,551},{720,320},{320,720}}){
                int w=size[0],h=size[1];Bitmap output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                try{
                    wallpaper.setBounds(0,0,w,h);wallpaper.draw(new Canvas(output));
                    for(int y=32;y<h;y++)for(int x=33;x<w;x++){
                        if(output.getPixel(x,y)!=Color.CYAN)throw new AssertionError("wallpaper gap at "+x+","+y+" in "+w+"x"+h);
                    }
                    if(wallpaper.sample(new RectF(w-8,40,w,h-8))!=Color.CYAN)throw new AssertionError("card contrast samples a blank edge");
                    if(output.getPixel(0,h-1)!=Color.WHITE||output.getPixel(w-1,0)!=Color.WHITE)throw new AssertionError("header/gutter lost");
                }finally{output.recycle();}
            }
        }finally{source.recycle();}
    }
}
