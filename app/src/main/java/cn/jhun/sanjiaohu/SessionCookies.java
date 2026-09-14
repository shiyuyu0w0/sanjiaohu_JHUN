package cn.jhun.sanjiaohu;

import android.webkit.CookieManager;
import java.util.*;

/** Remove each site's cookies, preserving the other account's host and path scopes. */
final class SessionCookies {
    static void teaching(Runnable done){clear(new String[]{"jwxt.jhun.edu.cn"},new String[]{"/","/cas","/cas/","/frame","/frame/"},done);}
    static void identity(Runnable done){clear(new String[]{"authserver.jhun.edu.cn","ehall.jhun.edu.cn","hqfw.jhun.edu.cn"},new String[]{"/","/authserver","/authserver/","/new","/new/","/wsbx","/wsbx/","/wsbx/html/yd","/wsbx/html/yd/"},done);}
    private static void clear(String[] hosts,String[] paths,Runnable done){
        CookieManager jar=CookieManager.getInstance();List<String[]> removals=new ArrayList<>();
        for(String host:hosts){android.webkit.WebStorage.getInstance().deleteOrigin("https://"+host);android.webkit.WebStorage.getInstance().deleteOrigin("http://"+host);Set<String> names=new HashSet<>();
            for(String scheme:new String[]{"http","https"})for(String path:paths){String cookies=jar.getCookie(scheme+"://"+host+path);if(cookies!=null)for(String cookie:cookies.split(";")){int eq=cookie.indexOf('=');if(eq>0)names.add(cookie.substring(0,eq).trim());}}
            for(String name:names)for(String path:paths)for(String domain:new String[]{"","; Domain="+host,"; Domain=."+host})removals.add(new String[]{"https://"+host+path,name+"=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path="+path+domain});
        }
        removeNext(jar,removals,0,done);
    }
    private static void removeNext(CookieManager jar,List<String[]> work,int index,Runnable done){if(index==work.size()){jar.flush();done.run();return;}String[] item=work.get(index);jar.setCookie(item[0],item[1],ok->removeNext(jar,work,index+1,done));}
}
