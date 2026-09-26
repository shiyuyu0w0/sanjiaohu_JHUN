package cn.jhun.sanjiaohu;

import java.net.URI;
import java.util.*;

/** Public update traffic is independent of school authentication. */
final class UpdatePolicy {
    static final String PACKAGE="cn.jhun.sanjiaohu";
    static final String REPO="https://github.com/shiyuyu0w0/sanjiaohu_JHUN/";
    static final String PROXY="https://ghproxy.net/";
    static final String GITEE_REPO="https://gitee.com/shiyuyu0w0/sanjiaohu_JHUN/";
    static final int GITEE=0, GHPROXY=1, GITHUB=2;
    static final String RAW="https://raw.githubusercontent.com/shiyuyu0w0/sanjiaohu_JHUN/main/updates/stable/version.json";
    static final String[] SOURCES={"https://cdn.jsdelivr.net/gh/shiyuyu0w0/sanjiaohu_JHUN@main/updates/stable/version.json",RAW,PROXY+RAW};
    static final long MAX_APK=256L*1024*1024, DAY=24L*60*60*1000;
    static boolean apkUrl(String value){
        if(value==null||!value.startsWith(REPO+"releases/download/"))return false;
        try{URI u=new URI(value);return u.getRawQuery()==null&&u.getRawFragment()==null&&u.getUserInfo()==null&&u.getPort()==-1
            &&value.substring((REPO+"releases/download/").length()).matches("v[0-9]+(?:\\.[0-9]+){1,3}/Sanjiaohu-[0-9]+(?:\\.[0-9]+){1,3}\\.apk");}catch(Exception e){return false;}
    }
    static boolean mirror(String value,String official){return apkUrl(official)&&(PROXY+official).equals(value);}
    static String giteeUrl(String version){return GITEE_REPO+"releases/download/v"+version+"/Sanjiaohu-"+version+".apk";}
    static boolean gitee(String value,String version){return version.matches("[0-9]+(?:\\.[0-9]+){1,3}")&&giteeUrl(version).equals(value);}
    static String downloadUrl(UpdateManifest manifest,int route){
        if(route==GITEE)return manifest.gitee.isEmpty()?null:manifest.gitee;
        if(route==GHPROXY)return manifest.mirror.isEmpty()?null:manifest.mirror;
        return route==GITHUB?manifest.url:null;
    }
    static String routeName(int route){return route==GITEE?"Gitee":route==GHPROXY?"GitHub 加速":"GitHub 官方";}
    static int firstRoute(UpdateManifest manifest){for(int route=GITEE;route<=GITHUB;route++)if(downloadUrl(manifest,route)!=null)return route;return -1;}
    static int nextRoute(UpdateManifest manifest,int current){for(int route=current+1;route<=GITHUB;route++)if(downloadUrl(manifest,route)!=null)return route;return -1;}
    static int nextChoice(UpdateManifest manifest,int current){
        for(int step=1;step<=3;step++){int route=(current+step)%3;if(downloadUrl(manifest,route)!=null)return route;}
        return current;
    }
    static boolean source(String value){return Arrays.asList(SOURCES).contains(value);}
    static boolean canUpdate(UpdateManifest m,long installed,int sdk){return m!=null&&m.enabled&&m.code>installed&&m.minSdk<=sdk;}
    static boolean due(long now,long success,long attempt){return (success==0||now<success||now-success>=DAY)&&(attempt==0||now<attempt||now-attempt>=15*60*1000);}
}
