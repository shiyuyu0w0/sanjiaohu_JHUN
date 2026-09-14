package cn.jhun.sanjiaohu;
import android.webkit.*;
public final class SessionCookiesTest {
    public static void main(String[] args){
        boolean[] done={false};SessionCookies.teaching(()->done[0]=true);if(!done[0])throw new AssertionError("Missing completion");
        int checks=1;for(String write:CookieManager.INSTANCE.writes){if(!write.startsWith("https://jwxt.jhun.edu.cn/")||!write.contains("Max-Age=0"))throw new AssertionError("Teaching cleanup crossed scope");checks++;}
        for(String origin:WebStorage.INSTANCE.deleted){if(!origin.endsWith("://jwxt.jhun.edu.cn"))throw new AssertionError("Wrong storage removed");checks++;}
        CookieManager.INSTANCE.writes.clear();WebStorage.INSTANCE.deleted.clear();done[0]=false;SessionCookies.identity(()->done[0]=true);if(!done[0])throw new AssertionError("Missing completion");checks++;
        for(String write:CookieManager.INSTANCE.writes){if(write.contains("jwxt")||write.contains("gis.jhun")||write.contains("Domain=.jhun.edu.cn")||!write.contains("Max-Age=0"))throw new AssertionError("Identity cleanup crossed scope");checks++;}
        for(String origin:WebStorage.INSTANCE.deleted){if(origin.contains("jwxt")||origin.contains("gis.jhun"))throw new AssertionError("Other account storage removed");checks++;}
        System.out.println("Session cleanup scopes: "+checks+" fixture checks passed");
    }
}
