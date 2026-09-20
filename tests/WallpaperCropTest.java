package cn.jhun.sanjiaohu;

import java.util.Random;

public final class WallpaperCropTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static void near(float a,float b){check(Math.abs(a-b)<.1f);}
    static void valid(WallpaperCropGeometry g){
        float[] r=g.source();check(Float.isFinite(g.scale)&&g.scale>0);check(r[0]>=0&&r[1]>=0&&r[2]<=g.imageWidth&&r[3]<=g.imageHeight);check(r[2]>r[0]&&r[3]>r[1]);
        check(g.left>=0&&g.top>=0&&g.right<=g.width+.1f&&g.bottom<=g.height+.1f);
        near((r[2]-r[0])/(r[3]-r[1]),g.cropWidth()/g.cropHeight());if(g.ratio>0)near(g.cropWidth()/g.cropHeight(),g.ratio);
    }
    public static void main(String[] args){
        Random random=new Random(42);
        for(float[] size:new float[][]{{1600,900},{900,1600},{400,400},{100,1600}}){
            WallpaperCropGeometry g=new WallpaperCropGeometry(size[0],size[1],.65f);g.resize(400,620);valid(g);
            g.zoom(2,200,300);g.pan(-100,200);valid(g);float[] before=g.source();g.resize(700,320);float[] after=g.source();for(int i=0;i<4;i++)near(before[i],after[i]);valid(g);
            g.setRatio(0);g.corner(2,g.right-40,g.bottom-20);valid(g);
            for(int i=0;i<100;i++){g.pan(random.nextFloat()*2000-1000,random.nextFloat()*2000-1000);g.zoom(.5f+random.nextFloat()*2,random.nextFloat()*700,random.nextFloat()*320);g.corner(i%4,random.nextFloat()*1400-350,random.nextFloat()*640-160);valid(g);}
            g.setRatio(size[0]/size[1]);g.reset();before=g.source();near(before[0],0);near(before[1],0);near(before[2],size[0]);near(before[3],size[1]);valid(g);
            before=g.source();g.zoom(Float.NaN,0,0);g.zoom(-1,0,0);after=g.source();for(int i=0;i<4;i++)near(before[i],after[i]);
            g.restore(new float[]{0,0,0,0});valid(g);
        }
        System.out.println("Wallpaper crop: "+checks+" checks passed");
    }
}
