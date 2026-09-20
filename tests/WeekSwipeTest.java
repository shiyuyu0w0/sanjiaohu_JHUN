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
        System.out.println("Week swipe: "+checks+" checks passed");
    }
}
