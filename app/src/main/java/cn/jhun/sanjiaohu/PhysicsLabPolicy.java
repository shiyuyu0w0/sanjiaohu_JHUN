package cn.jhun.sanjiaohu;

import java.net.URI;
import java.util.Locale;

/** The physics lab report service is reachable only from the campus network, so this browser
 *  stays inside school addresses: the report site itself, school HTTPS pages (single sign-on
 *  and redirects) and the exact cleartext hosts the network configuration already permits. */
final class PhysicsLabPolicy {
    // Supplied by the user; kept byte for byte, including the escaped "&amp_sec_version_".
    static final String REPORT="http://wlxpk.jhun.edu.cn:6603/Page/Android/html/index.htm?t_s=1789478335510&amp_sec_version_=1&gid_=Uy9IS1Q1ZEd1WFMxTTBVT213M3duVmlEVERrQU56QzBLSTJFOGpna1N0aUEza08wOVVLQzFtQ2dERm5RN3JuZkwrUkNITDJkTmZrenowR2NQa3poaXc9PQ&EMAP_LANG=zh&THEME=indigo#/";
    static final String HOST="wlxpk.jhun.edu.cn";
    static final int PORT=6603;
    // Cleartext stays limited to the hosts network_security_config.xml already allows.
    static final String[] CLEARTEXT={"authserver.jhun.edu.cn","ehall.jhun.edu.cn","hqfw.jhun.edu.cn",HOST};
    static URI parse(String value){try{return new URI(value==null?"":value);}catch(Exception e){return URI.create("");}}
    // WebView accepts query and fragment characters (raw braces, JSON) that java.net.URI
    // rejects; trust is decided by the origin, and the original address is left untouched.
    static URI navigationUri(String value){return parse(value==null?null:value.split("[?#]",2)[0]);}
    static boolean schoolHost(String host){
        if(host==null)return false;String lower=host.toLowerCase(Locale.ROOT);
        return lower.equals("jhun.edu.cn")||lower.endsWith(".jhun.edu.cn");
    }
    static boolean reportSite(String value){
        URI u=navigationUri(value);
        return u.getRawUserInfo()==null&&("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))
            &&HOST.equalsIgnoreCase(u.getHost())&&u.getPort()==PORT;
    }
    static boolean schoolHttps(String value){
        URI u=navigationUri(value);
        return u.getRawUserInfo()==null&&"https".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)&&schoolHost(u.getHost());
    }
    static boolean schoolCleartext(String value){
        URI u=navigationUri(value);
        if(u.getRawUserInfo()!=null||!"http".equalsIgnoreCase(u.getScheme())||!(u.getPort()==-1||u.getPort()==80))return false;
        for(String host:CLEARTEXT)if(host.equalsIgnoreCase(u.getHost()))return true;
        return false;
    }
    static boolean allowed(String value){return reportSite(value)||schoolHttps(value)||schoolCleartext(value);}
    /** Null keeps the form's own accept types. Any bare extension (".docx") in that list would
     *  match no MIME type and hide the report file, so the whole list widens to any file. */
    static String pickerType(String[] accept){
        if(accept==null||accept.length==0)return null;
        for(String type:accept)if(type==null||type.indexOf('/')<=0)return "*/*";
        return null;
    }
}
