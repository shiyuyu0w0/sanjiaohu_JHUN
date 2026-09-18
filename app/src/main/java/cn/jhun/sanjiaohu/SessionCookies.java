package cn.jhun.sanjiaohu;

import android.webkit.CookieManager;
import java.util.*;

/** Remove each site's cookies, preserving the other account's host and path scopes. */
final class SessionCookies {
    static void teaching(Runnable done){clear(new String[]{"jwxt.jhun.edu.cn"},new String[]{"/","/cas","/cas/","/frame","/frame/"},done);}
    static void identity(Runnable done){clear(new String[]{"authserver.jhun.edu.cn","ehall.jhun.edu.cn","hqfw.jhun.edu.cn","hub.17wanxiao.com","open.17wanxiao.com"},new String[]{"/","/authserver","/authserver/","/new","/new/","/wsbx","/wsbx/","/wsbx/html/yd","/wsbx/html/yd/","/bsacs","/bsacs/"},()->clear(new String[]{"h5cloud.17wanxiao.com:18443"},new String[]{"/","/CloudPayment","/CloudPayment/","/CloudPayment/bill","/CloudPayment/bill/","/CloudPayment/bill/type.do"},done));}
    private static void clear(String[] authorities,String[] paths,Runnable done){
        CookieManager jar=CookieManager.getInstance();List<String[]> removals=new ArrayList<>();
        for(String authority:authorities){String host=java.net.URI.create("https://"+authority).getHost();android.webkit.WebStorage.getInstance().deleteOrigin("https://"+authority);android.webkit.WebStorage.getInstance().deleteOrigin("http://"+authority);Set<String> names=new HashSet<>();
            for(String scheme:new String[]{"http","https"})for(String path:paths){String cookies=jar.getCookie(scheme+"://"+authority+path);if(cookies!=null)for(String cookie:cookies.split(";")){int eq=cookie.indexOf('=');if(eq>0)names.add(cookie.substring(0,eq).trim());}}
            // Cookie Domain has no port; WebStorage and request URLs retain the port.
            for(String name:names)for(String path:paths)for(String domain:new String[]{"","; Domain="+host,"; Domain=."+host})removals.add(new String[]{"https://"+authority+path,name+"=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path="+path+domain});
        }
        removeNext(jar,removals,0,done);
    }
    private static void removeNext(CookieManager jar,List<String[]> work,int index,Runnable done){if(index==work.size()){jar.flush();done.run();return;}String[] item=work.get(index);jar.setCookie(item[0],item[1],ok->removeNext(jar,work,index+1,done));}
}
