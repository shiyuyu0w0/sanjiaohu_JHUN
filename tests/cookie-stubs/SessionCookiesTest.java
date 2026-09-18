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
        if(!WebStorage.INSTANCE.deleted.contains("https://hub.17wanxiao.com"))throw new AssertionError("Electricity storage retained");checks++;
        if(CookieManager.INSTANCE.writes.stream().noneMatch(s->s.startsWith("https://hub.17wanxiao.com/bsacs")&&s.contains("Max-Age=0")))throw new AssertionError("Electricity session retained");checks++;
        if(!WebStorage.INSTANCE.deleted.contains("https://h5cloud.17wanxiao.com:18443"))throw new AssertionError("CloudPayment storage retained");checks++;
        if(CookieManager.INSTANCE.writes.stream().noneMatch(s->s.startsWith("https://h5cloud.17wanxiao.com:18443/CloudPayment/")&&s.contains("Domain=h5cloud.17wanxiao.com")&&s.contains("Max-Age=0")))throw new AssertionError("CloudPayment cookie retained");checks++;
        for(String write:CookieManager.INSTANCE.writes){if(write.contains("Domain=h5cloud.17wanxiao.com:18443")||write.contains("Domain=.h5cloud.17wanxiao.com:18443"))throw new AssertionError("Cookie Domain incorrectly includes port");checks++;}
        if(!WebStorage.INSTANCE.deleted.contains("https://open.17wanxiao.com"))throw new AssertionError("Electricity relay storage retained");checks++;
        if(CookieManager.INSTANCE.writes.stream().noneMatch(s->s.startsWith("https://open.17wanxiao.com/")&&s.contains("Max-Age=0")))throw new AssertionError("Electricity relay session retained");checks++;
        System.out.println("Session cleanup scopes: "+checks+" fixture checks passed");
    }
}
