package cn.jhun.sanjiaohu;
public final class AdaptiveTextTest {
    public static void main(String[] args){
        int count=0;
        for(int color:CourseColors.PALETTE)for(int wallpaper:new int[]{0xff000000,0xffffffff,0xffff0000,0xff00ff00,0xff0000ff})for(int t=0;t<=100;t++){
            int composite=ThemePalette.mix(wallpaper,color,(CourseAppearance.background(color,t)>>>24)/255.0);
            int ink=CourseAppearance.textColor(color,t,wallpaper);
            if(ThemePalette.contrast(ink,composite)<4.5)throw new AssertionError("Insufficient contrast on sampled background");
            count++;
        }
        if(CourseAppearance.textColor(0xff78b7f4,100,0xff000000)==CourseAppearance.textColor(0xff78b7f4,100,0xffffffff))throw new AssertionError("Text must respond to local brightness");
        System.out.println(count+" sampled-background text checks passed; busy-photo pixels may vary");
    }
}
