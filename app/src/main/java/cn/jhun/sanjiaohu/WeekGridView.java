package cn.jhun.sanjiaohu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Children exist before measurement. No view mutation from layout callbacks. */
public final class WeekGridView extends ViewGroup {
    public static final int HEADER=0, TIME=1, CELL=2, EMPTY=3, BREAK=4;
    private final int gutter,header,gap;
    public WeekGridView(Context context,int gutter,int header){this(context,gutter,header,0);}
    public WeekGridView(Context context,int gutter,int header,int gap){super(context);this.gutter=gutter;this.header=header;this.gap=gap;}
    public void add(View view,int kind,int day,int start,int end,int lane,int lanes){
        addView(view,new CellParams(kind,day,start,end,lane,lanes));
    }
    private static final class CellParams extends ViewGroup.LayoutParams {
        final int kind,day,start,end,lane,lanes;
        CellParams(int kind,int day,int start,int end,int lane,int lanes){super(0,0);this.kind=kind;this.day=day;this.start=start;this.end=end;this.lane=lane;this.lanes=lanes;}
    }
    private GridGeometry geometry(int w,int h){return new GridGeometry(w,h,gutter,header,gap);}
    private int[] bounds(GridGeometry g,CellParams p){return g.bounds(p.kind,p.day,p.start,p.end,p.lane,p.lanes);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=resolveSize(getSuggestedMinimumWidth(),widthSpec),h=resolveSize(getSuggestedMinimumHeight(),heightSpec);
        setMeasuredDimension(w,h);GridGeometry g=geometry(w,h);
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int[] r=bounds(g,(CellParams)child.getLayoutParams());child.measure(MeasureSpec.makeMeasureSpec(r[2]-r[0],MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(r[3]-r[1],MeasureSpec.EXACTLY));}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        GridGeometry g=geometry(right-left,bottom-top);
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);int[] r=bounds(g,(CellParams)child.getLayoutParams());child.layout(r[0],r[1],r[2],r[3]);}
    }
}
