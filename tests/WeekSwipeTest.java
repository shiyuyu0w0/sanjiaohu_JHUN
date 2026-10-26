package cn.jhun.sanjiaohu;

public final class WeekSwipeTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        for(float d:new float[]{1,2,3}){
            WeekSwipeGesture g=new WeekSwipeGesture(d,8*d);
            g.begin(200*d,100*d,400*d);check(!g.move(203*d,101*d));check(g.finish(203*d,101*d,0)==0); // course tap
            g.begin(200*d,100*d,400*d);check(g.move(170*d,102*d));check(g.finish(100*d,104*d,0)==1); // next
            g.begin(150*d,100*d,400*d);check(g.move(180*d,101*d));check(g.finish(250*d,104*d,0)==-1); // previous
            g.begin(200*d,100*d,400*d);check(!g.move(205*d,130*d));check(g.finish(100*d,150*d,-1500*d)==0); // vertical lock
            g.begin(200*d,100*d,400*d);check(g.finish(169*d,100*d,-1000*d)==1); // fast short fling
            g.begin(200*d,100*d,400*d);check(g.finish(169*d,100*d,1000*d)==0); // reversed velocity
            g.begin(200*d,100*d,400*d);check(g.finish(185*d,100*d,-3000*d)==0); // jitter
            g.begin(10*d,100*d,400*d);check(g.finish(150*d,100*d,2000*d)==0); // system edge back
            g.begin(390*d,100*d,400*d);check(g.finish(200*d,100*d,-2000*d)==0);
            g.begin(200*d,100*d,400*d);g.move(100*d,100*d);g.cancel();check(g.finish(50*d,100*d,-2000*d)==0); // cancel / multi-touch
        }
        check(WeekSwipeGesture.target(1,0,20)==1);check(WeekSwipeGesture.target(20,21,20)==20);check(WeekSwipeGesture.target(2,3,20)==3);check(WeekSwipeGesture.target(1,1,1)==1);
        parallax();rubberBand();
        System.out.println("Week swipe: "+checks+" checks passed");
    }

    /**
     * 翻周视差：退场页慢于进场页才有纵深，但速度差一旦把中缝拉开就会漏出背景。
     * 退场页还绕中心缩小，右边缘会再往里收，所以 seam 必须把缩放项算进去。
     */
    static void parallax(){
        for(float width:new float[]{720,1080,1440}){
            for(int direction:new int[]{1,-1}){
                for(int step=0;step<=40;step++){
                    float p=step/40f;
                    float lead=WeekSwipeGesture.leadOffset(p,width,direction);
                    float trail=WeekSwipeGesture.trailOffset(p,width,direction);
                    check(WeekSwipeGesture.seam(p,width,direction)<=0); // 全程不漏背景
                    check(Math.abs(trail)<=width*WeekSwipeGesture.TRAIL); // 退场页不会被甩出一屏
                    check(Math.abs(lead)<=width); // 进场页最远就是整屏在外
                    check(Math.abs(trail)<=width*p+.001f); // 退场页永远慢于满速
                    if(p>0&&p<1)check(Math.abs(trail)<width*p); // 中途严格更慢，速度差确实存在
                    check(Math.signum(trail)==-direction||trail==0); // 两页朝相反方向走
                }
                // 端点：起手进场页整屏在外，收手正好归零。
                check(Math.abs(WeekSwipeGesture.leadOffset(0,width,direction))==width);
                check(WeekSwipeGesture.leadOffset(1,width,direction)==0);
                check(WeekSwipeGesture.trailOffset(0,width,direction)==0);
            }
        }
        // 缩放和透明度单调、有界，且在两端回到确定值。
        check(WeekSwipeGesture.trailScale(0)==1);check(WeekSwipeGesture.trailAlpha(0)==1);
        check(WeekSwipeGesture.leadAlpha(1)==1);check(WeekSwipeGesture.leadAlpha(0)<1);
        check(WeekSwipeGesture.trailScale(1)>=.9f);check(WeekSwipeGesture.trailAlpha(1)>=.75f);
        float scale=2,alpha=2,lead=0;
        for(int step=0;step<=20;step++){
            float p=step/20f;
            float s=WeekSwipeGesture.trailScale(p),a=WeekSwipeGesture.trailAlpha(p),l=WeekSwipeGesture.leadAlpha(p);
            check(s<=scale);check(a<=alpha);check(l>=lead);scale=s;alpha=a;lead=l;
            check(s>0&&s<=1);check(a>0&&a<=1);check(l>0&&l<=1);
        }
        // 越界的 progress 不该把视图推到荒唐的位置。
        check(WeekSwipeGesture.trailScale(-1)==1);check(WeekSwipeGesture.trailScale(9)==WeekSwipeGesture.trailScale(1));
        check(WeekSwipeGesture.seam(9,1080,1)<=0);check(WeekSwipeGesture.seam(-9,1080,1)<=0);
    }

    /** 边界周的橡皮筋：同向、有界、越拖越钝，且不像硬截断那样突然卡死。 */
    static void rubberBand(){
        float limit=66; // 22dp @ 3x
        check(WeekSwipeGesture.rubberBand(0,limit)==0);
        check(WeekSwipeGesture.rubberBand(500,0)==0); // 没有余量就不动
        float previous=0,previousGain=Float.MAX_VALUE;
        for(float offset=10;offset<=4000;offset+=10){
            float value=WeekSwipeGesture.rubberBand(offset,limit);
            check(value>0&&value<limit); // 同向、永远到不了上限
            check(value>=previous); // 单调
            check(WeekSwipeGesture.rubberBand(-offset,limit)==-value); // 对称
            float gain=value-previous;
            if(offset>10)check(gain<=previousGain+1e-4f); // 边际位移递减 = 越拖越钝
            previous=value;previousGain=gain;
        }
        check(WeekSwipeGesture.rubberBand(4000,limit)>limit*.9f); // 拖到底基本贴着上限
        check(WeekSwipeGesture.rubberBand(limit,limit)<limit*.5f); // 手指走一个上限，视图只走不到一半
    }
}
