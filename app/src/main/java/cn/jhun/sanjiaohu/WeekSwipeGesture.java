package cn.jhun.sanjiaohu;

/** Direction locking keeps taps, vertical gestures and system edge-back separate. */
final class WeekSwipeGesture {
    final float density,slop;float width,startX,startY;boolean dragging,rejected;
    WeekSwipeGesture(float density,float slop){this.density=density;this.slop=slop;}
    void begin(float x,float y,float width){this.width=width;startX=x;startY=y;dragging=false;rejected=x<24*density||x>width-24*density;}
    boolean move(float x,float y){
        if(rejected)return false;if(dragging)return true;
        float dx=Math.abs(x-startX),dy=Math.abs(y-startY);
        if(dy>slop&&dy>=dx){rejected=true;return false;}
        if(dx>slop&&dx>dy*1.3f)dragging=true;
        return dragging;
    }
    int finish(float x,float y,float velocityX){
        move(x,y);float dx=x-startX;
        boolean distance=Math.abs(dx)>=Math.max(48*density,width*.18f);
        boolean fling=Math.abs(dx)>=24*density&&Math.abs(velocityX)>=600*density&&dx*velocityX>0;
        int direction=!rejected&&dragging&&(distance||fling)?(dx<0?1:-1):0;
        cancel();return direction;
    }
    void cancel(){dragging=false;rejected=true;}
    static int target(int current,int requested,int max){return Math.max(1,Math.min(Math.max(1,max),requested));}

    /** 退场页只走七成路程，跟进场页拉开速度差，翻周才有纵深感而不是整块硬推。 */
    static final float TRAIL=.72f;
    /** 退场页最多缩到 96%，再小就会露出下面的背景。 */
    static final float TRAIL_SCALE=.04f;
    /** 退场页最多淡到 82%，配合缩放把注意力交给进场页。 */
    static final float TRAIL_FADE=.18f;

    static float trailOffset(float progress,float width,int direction){return -width*direction*progress*TRAIL;}
    static float leadOffset(float progress,float width,int direction){return width*direction*(1-progress);}
    static float trailScale(float progress){return 1-TRAIL_SCALE*clamp(progress);}
    static float trailAlpha(float progress){return 1-TRAIL_FADE*clamp(progress);}
    static float leadAlpha(float progress){return .94f+.06f*clamp(progress);}
    static float clamp(float progress){return progress<0?0:progress>1?1:progress;}

    /**
     * 进场页左边缘与退场页右边缘之间的空隙（负数表示两页仍然叠着）。
     * 退场页既慢又缩，缩放是绕中心的，右边缘会再往里收 width*TRAIL_SCALE*progress/2，
     * 这一项必须算进来，否则速度差一旦调大就会在两页中间漏出背景。
     */
    static float seam(float progress,float width,int direction){
        float p=clamp(progress);
        float lead=Math.abs(leadOffset(p,width,direction));
        float trail=width*p*TRAIL+width*TRAIL_SCALE*p/2;
        return lead-(width-trail);
    }

    /** 边界周继续拖时的橡皮筋位移：越拖越钝，上限 limit，不像硬截断那样突然卡住。 */
    static float rubberBand(float offset,float limit){
        if(limit<=0)return 0;
        float sign=offset<0?-1:1,magnitude=Math.abs(offset);
        return sign*limit*(1-1/(1+magnitude/(limit*2.2f)));
    }
}
