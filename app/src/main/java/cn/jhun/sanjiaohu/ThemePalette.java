package cn.jhun.sanjiaohu;

/** Pure color math, shared by image themes and contrast regression tests. */
public final class ThemePalette {
    public final int primary,surface,text,muted,accent,onPrimary,panel,panelText,deepAccent,entrySurface;
    public final int[] cards=new int[6];
    public ThemePalette(int color){
        primary=0xff000000|(color&0xffffff);
        surface=mix(primary,0xffffffff,.94);
        text=readable(0xff262626,surface,7);muted=readable(0xff666666,surface,4.5);accent=text;
        onPrimary=neutralText(primary);
        deepAccent=readable(mix(primary,0xff000000,.5),0xffffffff,7);
        int entry=mix(primary,0xffffffff,.7);
        if(contrast(entry,surface)<1.08)entry=mix(entry,0xff000000,.08);
        entrySurface=entry;
        panel=surface;panelText=text;
        for(int i=0;i<cards.length;i++)cards[i]=mix(primary,0xffffffff,.55+i*.065);
    }
    public static int neutralText(int background){return readable(0xff262626,background,4.5);}
    public static int mix(int a,int b,double t){int out=0xff000000;for(int shift:new int[]{16,8,0})out|=((int)Math.round(((a>>shift)&255)*(1-t)+((b>>shift)&255)*t))<<shift;return out;}
    static double channel(int c){double v=c/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    public static double luminance(int c){return .2126*channel((c>>16)&255)+.7152*channel((c>>8)&255)+.0722*channel(c&255);}
    public static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    public static int readable(int preferred,int background,double ratio){
        if(contrast(preferred,background)>=ratio)return preferred;
        int target=contrast(0xff000000,background)>contrast(0xffffffff,background)?0xff000000:0xffffffff;
        for(int i=1;i<=100;i++){int c=mix(preferred,target,i/100.0);if(contrast(c,background)>=ratio)return c;}return target;
    }
    public static int dominant(int[] pixels){
        long[] r=new long[4096],g=new long[4096],b=new long[4096];int[] counts=new int[4096];
        for(int c:pixels){if((c>>>24)<128)continue;int red=(c>>16)&255,green=(c>>8)&255,blue=c&255;int k=(red>>4)*256+(green>>4)*16+(blue>>4);counts[k]++;r[k]+=red;g[k]+=green;b[k]+=blue;}
        int best=-1;double score=-1;
        for(int i=0;i<counts.length;i++){if(counts[i]==0)continue;double max=Math.max(r[i],Math.max(g[i],b[i]))/(double)counts[i],min=Math.min(r[i],Math.min(g[i],b[i]))/(double)counts[i];double weight=counts[i]*(1+(max-min)/255.0*.3);if(weight>score){score=weight;best=i;}}
        if(best<0)return 0xff2ecbff;return 0xff000000|((int)(r[best]/counts[best])<<16)|((int)(g[best]/counts[best])<<8)|(int)(b[best]/counts[best]);
    }
}
