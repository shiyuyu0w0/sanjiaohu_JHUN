package cn.jhun.sanjiaohu;

import java.net.URI;

/** Credentials only go to the school's exact identity-provider login endpoint. */
final class IdentityPolicy {
    static final String REPAIR_CALLBACK="http://hqfw.jhun.edu.cn/wsbx/login/cas#/wybx";
    // Direct callback supplied by the user; #/wybx belongs inside service.
    static final String LOGIN="https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fhqfw.jhun.edu.cn%2Fwsbx%2Flogin%2Fcas%23%2Fwybx";
    static final String REPAIR="http://hqfw.jhun.edu.cn/wsbx/html/yd/wsbx.html#/wybx";
    static final String REPAIR_ENTRY=LOGIN;
    static URI parse(String value){try{return new URI(value==null?"":value);}catch(Exception e){return URI.create("");}}
    static boolean allowed(String value){
        URI u=parse(value);String scheme=u.getScheme(),host=u.getHost();if(u.getRawUserInfo()!=null)return false;
        if("https".equalsIgnoreCase(scheme)&&(u.getPort()==-1||u.getPort()==443))return "authserver.jhun.edu.cn".equalsIgnoreCase(host)||"ehall.jhun.edu.cn".equalsIgnoreCase(host)||"hqfw.jhun.edu.cn".equalsIgnoreCase(host);
        return "http".equalsIgnoreCase(scheme)&&(u.getPort()==-1||u.getPort()==80)&&("authserver.jhun.edu.cn".equalsIgnoreCase(host)||"ehall.jhun.edu.cn".equalsIgnoreCase(host)||"hqfw.jhun.edu.cn".equalsIgnoreCase(host));
    }
    static boolean loginPath(String path){return path!=null&&path.matches("/authserver/login(?:;jsessionid=[A-Za-z0-9._-]+)?");}
    static boolean auth(String value){URI u=parse(value);return allowed(value)&&"authserver.jhun.edu.cn".equalsIgnoreCase(u.getHost())&&loginPath(u.getPath())&&serviceAllowed(u,0);}
    static boolean serviceAllowed(URI uri,int depth){
        if(depth>4)return false;String query=uri.getRawQuery();if(query==null)return true;
        for(String pair:query.split("&")){String[] bits=pair.split("=",2);try{if("service".equals(java.net.URLDecoder.decode(bits[0],"UTF-8"))){if(bits.length!=2)return false;String next=java.net.URLDecoder.decode(bits[1],"UTF-8");URI target=parse(next);if(!allowed(next)||"authserver.jhun.edu.cn".equalsIgnoreCase(target.getHost())||!serviceAllowed(target,depth+1))return false;}}catch(Exception e){return false;}}
        return true;
    }
    static boolean hall(String value){URI u=parse(value);return allowed(value)&&"ehall.jhun.edu.cn".equalsIgnoreCase(u.getHost())&&"/new/index.html".equals(u.getPath());}
    static boolean repair(String value){URI u=parse(value);return allowed(value)&&"hqfw.jhun.edu.cn".equalsIgnoreCase(u.getHost());}
    static boolean desktopRepair(String value){return repair(value)&&"/wsbx/html/pc/index.html".equals(parse(value).getPath());}
    static boolean repairLanding(String value){return repair(value)&&("/wsbx/html/yd/wsbx.html".equals(parse(value).getPath())||desktopRepair(value));}
    static boolean repairRoot(String value){return repair(value)&&("/wsbx/".equals(parse(value).getPath())||"/wsbx".equals(parse(value).getPath()));}
}
