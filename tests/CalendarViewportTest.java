package cn.jhun.sanjiaohu;

public final class CalendarViewportTest {
    static int checks;
    static void near(float actual,float expected){checks++;if(Math.abs(actual-expected)>.05f)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        CalendarViewport v=new CalendarViewport(1280,1811);
        for(int[] size:new int[][]{{360,600},{400,700},{800,300},{300,800}}){
            v.quarterTurns=3;v.fit();v.resize(size[0],size[1]);
            // Upright whole image fits both portrait and landscape without cropping.
            near(v.rotatedWidth(),1811);near(v.rotatedHeight(),1280);
            if(v.rotatedWidth()*v.scale()>v.width+.01||v.rotatedHeight()*v.scale()>v.height+.01)throw new AssertionError("Fit cropped");checks++;
            v.zoomBy(4,v.width/2,v.height/2);near(v.zoom,4);near(v.panX,0);near(v.panY,0);
            float fx=v.width/2+5,fy=v.height/2;
            float sourceX=(fx-v.width/2-v.panX)/v.scale();
            v.zoomBy(1.2f,fx,fy);near((fx-v.width/2-v.panX)/v.scale(),sourceX);
            v.pan(999999,999999);near(v.panX,Math.max(0,(v.rotatedWidth()*v.scale()-v.width)/2));near(v.panY,Math.max(0,(v.rotatedHeight()*v.scale()-v.height)/2));
            v.pan(-999999,-999999);near(v.panX,-Math.max(0,(v.rotatedWidth()*v.scale()-v.width)/2));near(v.panY,-Math.max(0,(v.rotatedHeight()*v.scale()-v.height)/2));
            v.zoomBy(100,fx,fy);near(v.zoom,12);v.zoomBy(.0001f,fx,fy);near(v.zoom,1);near(v.panX,0);near(v.panY,0);
            v.zoomBy(Float.NaN,fx,fy);near(v.zoom,1);
            for(int turn=0;turn<4;turn++){v.rotate();near(v.zoom,1);near(v.panX,0);near(v.panY,0);}
            near(v.quarterTurns,3);
        }
        System.out.println("Calendar viewport: "+checks+" checks passed");
    }
}
