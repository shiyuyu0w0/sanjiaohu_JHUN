package cn.jhun.sanjiaohu;

import java.net.URI;
import java.util.ArrayDeque;

/** Memory-only allowlisted metadata. Never accept page text, query values or credentials. */
final class IdentityDiagnostics {
    enum Event { START, GET, POST, OTHER_METHOD, COMMIT, FINISH, UPGRADE, LOOP, ERROR, HTTP_ERROR, SSL_ERROR, DOCUMENT, RETRY, SCRIPT_ERROR, BLOCKED_MAIN, BLOCKED_FRAME, LENIENT_URL }
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    static String browserAgent(String agent){return agent==null?"":agent.replace("; wv","").replace("Version/4.0 ","");}
    static String route(String value){
        URI u=IdentityPolicy.navigationUri(value);String host=u.getHost(),scheme=u.getScheme();
        if(scheme!=null)scheme=scheme.toLowerCase(java.util.Locale.ROOT);
        if(!"http".equals(scheme)&&!"https".equals(scheme)){
            if("intent".equals(scheme)||"alipays".equals(scheme)||"weixin".equals(scheme)||"wanxiao".equals(scheme))return "[外部应用协议："+scheme+"]";
            return "[其他协议或错误地址]";
        }
        if(host!=null)host=host.toLowerCase(java.util.Locale.ROOT);
        // Report provider subdomains for debugging without permitting their navigation.
        boolean provider=host!=null&&(host.equals("17wanxiao.com")||host.endsWith(".17wanxiao.com"));
        if(!"authserver.jhun.edu.cn".equals(host)&&!"ehall.jhun.edu.cn".equals(host)&&!"hqfw.jhun.edu.cn".equals(host)&&!provider)return "[其他或错误页面]";
        String path=u.getPath();if(path==null)path="";path=path.replaceAll(";[^/]*","");
        if(!path.matches("/(?:authserver/login|new/index\\.html|login|appShow|wsbx/?|wsbx/login/cas|wsbx/html/yd/wsbx\\.html|wsbx/html/pc/index\\.html|bsacs/light\\.action|CloudPayment/bill/type\\.do)"))path="/[其他路径]";
        return scheme+"://"+host+(u.getPort()==-1?"":":"+u.getPort())+path;
    }
    void add(Event event,String url,int code){
        if(entries.size()==40)entries.removeFirst();entries.addLast(event.name()+" "+route(url)+" ["+code+"]");
    }
    String report(){return String.join("\n",entries);}
}
