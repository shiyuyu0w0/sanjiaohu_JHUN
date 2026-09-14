package cn.jhun.sanjiaohu;
import java.util.*;
public final class ThemeTest {
    public static void main(String[]args){
        Random random=new Random(134);int[] cases=new int[1007];int[] fixed={0xff2ecbff,0xff000000,0xffffffff,0xff808080,0xffff0000,0xff00ff00,0xff0000ff};System.arraycopy(fixed,0,cases,0,7);for(int i=7;i<cases.length;i++)cases[i]=0xff000000|random.nextInt(0x1000000);
        for(int c:cases){ThemePalette p=new ThemePalette(c);if(((p.text>>16)&255)!=((p.text>>8)&255)||((p.text>>8)&255)!=(p.text&255))throw new AssertionError("body is not neutral");if(ThemePalette.contrast(0xffffffff,p.deepAccent)<7)throw new AssertionError("today marker white text contrast");if(ThemePalette.contrast(p.text,p.entrySurface)<7)throw new AssertionError("entry text contrast");if(p.entrySurface==p.surface)throw new AssertionError("entry needs a distinct tint");if(ThemePalette.contrast(p.deepAccent,p.entrySurface)<4.5)throw new AssertionError("secondary action text contrast");if(p.primary!=c)throw new AssertionError("primary changed");if(ThemePalette.contrast(p.text,p.surface)<7)throw new AssertionError("body contrast");if(ThemePalette.contrast(p.onPrimary,p.primary)<4.5)throw new AssertionError("button contrast");if(ThemePalette.contrast(p.panelText,p.panel)<4.5)throw new AssertionError("header contrast");for(int card:p.cards){int ink=ThemePalette.neutralText(card);if(ThemePalette.contrast(ink,card)<4.5)throw new AssertionError("card contrast");}}
        if(new ThemePalette(0xffff0000).deepAccent==new ThemePalette(0xff0000ff).deepAccent)throw new AssertionError("deep accent must follow hue");
        if(ThemePalette.dominant(new int[]{0xffff0000,0xffff0000,0xff0000ff})!=0xffff0000)throw new AssertionError("dominant color");
        if(ThemePalette.dominant(new int[]{0x00000000})!=0xff2ecbff)throw new AssertionError("transparent fallback");
        System.out.println("PASS: 1007 palettes, readable neutral text, theme-derived dark markers, dominant color and transparency fallback");
    }
}
