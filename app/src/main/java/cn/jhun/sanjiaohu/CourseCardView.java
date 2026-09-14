package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.TextView;

/** Ordinary text over the original wallpaper: no backing panel or outline. */
final class CourseCardView extends TextView {
    private int surfaceColor,transparency,fallback;
    CourseCardView(Context c){super(c);}
    void setAppearance(int color,int setting,int fallbackColor){surfaceColor=color;transparency=CourseAppearance.clamp(setting);fallback=fallbackColor;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        int background=fallback;float x=0,y=0;View child=this;
        if(transparency>0)for(int depth=0;depth<4&&child.getParent() instanceof View;depth++){
            View parent=(View)child.getParent();x+=child.getLeft()-parent.getScrollX();y+=child.getTop()-parent.getScrollY();
            if(parent.getBackground() instanceof WallpaperDrawable){background=((WallpaperDrawable)parent.getBackground()).sample(new RectF(x,y,x+getWidth(),y+getHeight()));break;}child=parent;
        }
        int ink=CourseAppearance.textColor(surfaceColor,transparency,background);if(getCurrentTextColor()!=ink)setTextColor(ink);
        float density=getResources().getDisplayMetrics().density;int shadow=ThemePalette.luminance(ink)>.5?0x90000000:0x70ffffff;
        getPaint().setShadowLayer(transparency==0?0:density,0,transparency==0?0:.3f*density,shadow);
        super.onDraw(canvas);
    }
}
