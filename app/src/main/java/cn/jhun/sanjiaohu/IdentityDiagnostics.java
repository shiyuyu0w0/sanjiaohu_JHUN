package cn.jhun.sanjiaohu;

import java.net.URI;
import java.util.ArrayDeque;

/** Memory-only allowlisted metadata. Never accept page text, query values or credentials. */
final class IdentityDiagnostics {
    enum Event { START, GET, POST, OTHER_METHOD, COMMIT, FINISH, UPGRADE, LOOP, ERROR, HTTP_ERROR, SSL_ERROR, DOCUMENT, RETRY, SCRIPT_ERROR }
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    static String browserAgent(String agent){return agent==null?"":agent.replace("; wv","").replace("Version/4.0 ","");}
    static String route(String value){
        URI u=IdentityPolicy.parse(value);String host=u.getHost(),scheme=u.getScheme();
        if(!"authserver.jhun.edu.cn".equals(host)&&!"ehall.jhun.edu.cn".equals(host)&&!"hqfw.jhun.edu.cn".equals(host))return "[其他或错误页面]";
        if(!"http".equals(scheme)&&!"https".equals(scheme))return "[其他协议]";
        String path=u.getPath();if(path==null)path="";path=path.replaceAll(";[^/]*","");
        if(!path.matches("/(?:authserver/login|new/index\\.html|login|appShow|wsbx/?|wsbx/login/cas|wsbx/html/yd/wsbx\\.html|wsbx/html/pc/index\\.html)"))path="/[其他路径]";
        return scheme+"://"+host+path;
    }
    void add(Event event,String url,int code){
        if(entries.size()==40)entries.removeFirst();entries.addLast(event.name()+" "+route(url)+" ["+code+"]");
    }
    String report(){return String.join("\n",entries);}
}
