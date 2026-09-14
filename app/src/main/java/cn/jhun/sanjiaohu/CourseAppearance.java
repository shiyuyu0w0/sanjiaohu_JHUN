package cn.jhun.sanjiaohu;

final class CourseAppearance {
    static int clamp(int transparency){return Math.max(0,Math.min(100,transparency));}
    static int background(int color,int transparency){return (Math.round(255*(100-clamp(transparency))/100f)<<24)|(color&0xffffff);}
    static int textColor(int color,int transparency,int backdrop){return ThemePalette.neutralText(ThemePalette.mix(backdrop,color,(background(color,transparency)>>>24)/255.0));}
}
