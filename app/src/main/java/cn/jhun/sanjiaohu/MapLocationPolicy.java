package cn.jhun.sanjiaohu;

import java.net.URI;

/** Only the school's secure map can receive WebView geolocation access. */
final class MapLocationPolicy {
    static boolean trusted(String url){
        try{URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&"gis.jhun.edu.cn".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}
    }
    static boolean allow(String origin,String page,boolean fine,boolean coarse){return trusted(origin)&&trusted(page)&&(fine||coarse);}
}
