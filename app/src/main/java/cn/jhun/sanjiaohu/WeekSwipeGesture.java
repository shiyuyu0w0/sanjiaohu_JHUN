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
}
