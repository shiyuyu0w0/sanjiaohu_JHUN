package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
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
            result.putString("stream","PASS: real Android first-pass measurement and layout at 3 sizes");finish(-1,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error);finish(0,result);}
    }
}
