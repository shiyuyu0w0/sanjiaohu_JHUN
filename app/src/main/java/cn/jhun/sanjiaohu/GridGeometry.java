package cn.jhun.sanjiaohu;

/** Integer boundaries cover the available viewport exactly, with no scroll container. */
public final class GridGeometry {
    public final int width,height,time,header,breakHeight;
    public GridGeometry(int w,int h,int gutter,int head){this(w,h,gutter,head,0);}
    public GridGeometry(int w,int h,int gutter,int head,int gap){width=Math.max(1,w);height=Math.max(1,h);time=Math.min(gutter,width/5);header=Math.min(head,height/5);breakHeight=Math.max(0,Math.min(gap,(height-header)/16));}
    public int x(int dayBoundary){return time+(width-time)*dayBoundary/7;}
    public int y(int periodBoundary){return header+(height-header-2*breakHeight)*periodBoundary/12+Math.min(2,periodBoundary/4)*breakHeight;}
    public int periodTop(int period){return y(period-1);}
    public int periodBottom(int period){return header+(height-header-2*breakHeight)*period/12+((period-1)/4)*breakHeight;}
    /** Rectangle used by both native measure/layout and JVM regression tests. */
    public int[] bounds(int kind,int day,int start,int end,int lane,int lanes){
        if(kind==0)return new int[]{x(day-1),0,x(day),header};
        if(kind==1)return new int[]{0,periodTop(start),time,periodBottom(end)};
        if(kind==3)return new int[]{time,header,width,height};
        if(kind==4)return new int[]{0,periodBottom(start),width,periodBottom(start)+breakHeight};
        int span=x(day)-x(day-1);
        int l=x(day-1)+span*lane/lanes+1,r=x(day-1)+span*(lane+1)/lanes-1;
        int t=periodTop(start)+1,b=periodBottom(end)-1;
        return new int[]{l,t,Math.max(l,r),Math.max(t,b)};
    }
}
