package cn.jhun.sanjiaohu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Two equal columns; every card uses the tallest measured card across the entire grid. */
final class HomeEntryGrid extends ViewGroup {
    private final int gap,minimumCardHeight;
    private Geometry geometry;

    HomeEntryGrid(Context context,int gap,int minimumCardHeight){
        super(context);this.gap=gap;this.minimumCardHeight=minimumCardHeight;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int available=Math.max(0,width-getPaddingLeft()-getPaddingRight());
        int cardWidth=Geometry.cardWidth(available,gap);
        int count=0;for(int i=0;i<getChildCount();i++)if(getChildAt(i).getVisibility()!=GONE)count++;
        int[] heights=new int[count];int index=0,state=0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            // Always measure natural height first, including after a width/font-size change.
            child.measure(MeasureSpec.makeMeasureSpec(cardWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            heights[index++]=child.getMeasuredHeight();state=combineMeasuredStates(state,child.getMeasuredState());
        }
        geometry=new Geometry(available,gap,minimumCardHeight,heights);
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            child.measure(MeasureSpec.makeMeasureSpec(geometry.width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(geometry.height,MeasureSpec.EXACTLY));
        }
        int height=getPaddingTop()+geometry.totalHeight+getPaddingBottom();
        setMeasuredDimension(resolveSizeAndState(width,widthSpec,state),resolveSizeAndState(height,heightSpec,state<<MEASURED_HEIGHT_STATE_SHIFT));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        if(geometry==null)return;
        boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;int index=0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            int x=rtl?getWidth()-getPaddingRight()-geometry.left(index)-geometry.width:getPaddingLeft()+geometry.left(index);
            int y=getPaddingTop()+geometry.top(index++);
            child.layout(x,y,x+geometry.width,y+geometry.height);
        }
    }

    /** Pixel geometry shared by measurement/layout and regression checks. */
    static final class Geometry {
        final int width,height,totalHeight,gap;
        static int cardWidth(int available,int gap){return Math.max(0,(available-gap)/2);}
        Geometry(int available,int gap,int minimumHeight,int[] measuredHeights){
            this.gap=gap;width=cardWidth(available,gap);int tallest=minimumHeight;
            for(int measured:measuredHeights)tallest=Math.max(tallest,measured);
            height=tallest;int rows=(measuredHeights.length+1)/2;
            totalHeight=rows==0?0:rows*height+(rows-1)*gap;
        }
        int left(int index){return index%2*(width+gap);}
        int top(int index){return index/2*(height+gap);}
    }
}
