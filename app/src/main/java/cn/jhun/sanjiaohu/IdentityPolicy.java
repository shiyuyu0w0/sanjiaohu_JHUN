package cn.jhun.sanjiaohu;

import java.net.URI;

/** Credentials only go to the school's exact identity-provider login endpoint. */
final class IdentityPolicy {
    static final String REPAIR_CALLBACK="http://hqfw.jhun.edu.cn/wsbx/login/cas#/wybx";
    // Direct callback supplied by the user; #/wybx belongs inside service.
    static final String LOGIN="https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fhqfw.jhun.edu.cn%2Fwsbx%2Flogin%2Fcas%23%2Fwybx";
    static final String REPAIR="http://hqfw.jhun.edu.cn/wsbx/html/yd/wsbx.html#/wybx";
    static final String REPAIR_ENTRY=LOGIN;
    static final String ELECTRICITY_SERVICE="https://hub.17wanxiao.com/bsacs/light.action?flag=cassso30_jhdxjrxyjf&ecardFunc=index";
    static final String ELECTRICITY_LOGIN="http://authserver.jhun.edu.cn/authserver/login?service=https%3A%2F%2Fhub.17wanxiao.com%2Fbsacs%2Flight.action%3Fflag%3Dcassso30_jhdxjrxyjf%26ecardFunc%3Dindex";
    static URI parse(String value){try{return new URI(value==null?"":value);}catch(Exception e){return URI.create("");}}
    // WebView accepts query/fragment characters (e.g. JSON braces) that java.net.URI
    // rejects. Navigation trust depends on the origin; leave the original URL intact.
    // Credential checks below still parse the entire URL and validate nested service.
    static URI navigationUri(String value){return parse(value==null?null:value.split("[?#]",2)[0]);}
    static boolean allowed(String value){
        URI u=navigationUri(value);String scheme=u.getScheme(),host=u.getHost();if(u.getRawUserInfo()!=null)return false;
        if(cloudPayment(value)||electricityRelay(value))return true;
        if("https".equalsIgnoreCase(scheme)&&(u.getPort()==-1||u.getPort()==443))return "authserver.jhun.edu.cn".equalsIgnoreCase(host)||"ehall.jhun.edu.cn".equalsIgnoreCase(host)||"hqfw.jhun.edu.cn".equalsIgnoreCase(host)||"hub.17wanxiao.com".equalsIgnoreCase(host);
        return "http".equalsIgnoreCase(scheme)&&(u.getPort()==-1||u.getPort()==80)&&("authserver.jhun.edu.cn".equalsIgnoreCase(host)||"ehall.jhun.edu.cn".equalsIgnoreCase(host)||"hqfw.jhun.edu.cn".equalsIgnoreCase(host));
    }
    static boolean loginPath(String path){return path!=null&&path.matches("/authserver/login(?:;jsessionid=[A-Za-z0-9._-]+)?");}
    static boolean auth(String value){URI u=parse(value);return allowed(value)&&"authserver.jhun.edu.cn".equalsIgnoreCase(u.getHost())&&loginPath(u.getPath())&&serviceAllowed(u,0);}
    static boolean serviceAllowed(URI uri,int depth){
        if(depth>4)return false;String query=uri.getRawQuery();if(query==null)return true;
        for(String pair:query.split("&")){String[] bits=pair.split("=",2);try{if("service".equals(java.net.URLDecoder.decode(bits[0],"UTF-8"))){if(bits.length!=2)return false;String next=java.net.URLDecoder.decode(bits[1],"UTF-8");URI target=parse(next);if(target.getHost()==null||!allowed(next)||cloudPayment(next)||electricityRelay(next)||"authserver.jhun.edu.cn".equalsIgnoreCase(target.getHost())||!serviceAllowed(target,depth+1))return false;}}catch(Exception e){return false;}}
        return true;
    }
    static boolean hall(String value){URI u=parse(value);return allowed(value)&&"ehall.jhun.edu.cn".equalsIgnoreCase(u.getHost())&&"/new/index.html".equals(u.getPath());}
    static boolean repair(String value){URI u=parse(value);return allowed(value)&&"hqfw.jhun.edu.cn".equalsIgnoreCase(u.getHost());}
    static boolean desktopRepair(String value){return repair(value)&&"/wsbx/html/pc/index.html".equals(parse(value).getPath());}
    static boolean repairLanding(String value){return repair(value)&&("/wsbx/html/yd/wsbx.html".equals(parse(value).getPath())||desktopRepair(value));}
    static boolean repairRoot(String value){return repair(value)&&("/wsbx/".equals(parse(value).getPath())||"/wsbx".equals(parse(value).getPath()));}
    // CloudPayment is the post-CAS destination, not the credential form or CAS service.
    static boolean cloudPayment(String value){URI u=navigationUri(value);return u.getRawUserInfo()==null&&"https".equalsIgnoreCase(u.getScheme())&&"h5cloud.17wanxiao.com".equalsIgnoreCase(u.getHost())&&u.getPort()==18443;}
    // Phone diagnostics confirm this HTTPS hop after hub, before the bill page.
    static boolean electricityRelay(String value){URI u=navigationUri(value);return u.getRawUserInfo()==null&&"https".equalsIgnoreCase(u.getScheme())&&"open.17wanxiao.com".equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==443);}
    static boolean electricity(String value){return cloudPayment(value)||electricityRelay(value)||(allowed(value)&&"hub.17wanxiao.com".equalsIgnoreCase(navigationUri(value).getHost()));}
}
