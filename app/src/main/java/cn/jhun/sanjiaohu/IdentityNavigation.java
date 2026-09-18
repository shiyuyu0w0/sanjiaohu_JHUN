package cn.jhun.sanjiaohu;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/** A failed navigation stays stopped until an explicit user retry. */
final class IdentityNavigation {
    private final Map<String,Integer> visits=new HashMap<>();
    private int total;
    private long started;
    boolean stopped,completed;
    void begin(long now){visits.clear();total=0;started=now;stopped=false;completed=false;}
    static String key(String url){
        URI uri=IdentityPolicy.navigationUri(url);java.util.List<String> parameters=new java.util.ArrayList<>();
        String withoutFragment=url==null?"":url.split("#",2)[0];int queryAt=withoutFragment.indexOf('?');
        if(queryAt>=0)for(String pair:withoutFragment.substring(queryAt+1).split("&")){
            String name=pair.split("=",2)[0];
            try{name=java.net.URLDecoder.decode(name,"UTF-8");}catch(Exception ignored){}
            if(!name.equals("ticket")&&!name.equals("nonce")&&!name.equals("timestamp")&&!name.equals("_"))parameters.add(pair);
        }
        java.util.Collections.sort(parameters);
        // service and execution distinguish normal CAS steps on the same servlet.
        return String.valueOf(uri.getScheme())+"://"+uri.getHost()+(uri.getPort()==-1?"":":"+uri.getPort())+uri.getRawPath()+"?"+String.join("&",parameters);
    }
    boolean visit(String url,long now){
        if(stopped)return false;
        // Ignore volatile CAS tickets, nonce and timestamp parameters when detecting loops.
        String key=key(url);int count=visits.getOrDefault(key,0)+1;visits.put(key,count);
        if(++total>24||count>4){stopped=true;return false;}return true;
    }
    void userGesture(long now){if(!stopped){visits.clear();total=0;started=now;}}
    boolean finishOnce(){if(stopped||completed)return false;completed=true;return true;}
    boolean expired(long now){return !stopped&&now-started>45000;}
    void stop(){stopped=true;}
}
