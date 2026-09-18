package cn.jhun.sanjiaohu;

import java.net.URI;
/** Payment navigation never grants access to native school credentials. */
final class PaymentNavigation {
    static final String ALIPAY_PACKAGE="com.eg.android.AlipayGphone";
    static boolean alipayWeb(String url){
        URI u=IdentityPolicy.navigationUri(url);
        return "https".equalsIgnoreCase(u.getScheme())&&"mclient.alipay.com".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);
    }
    static boolean sourceAllowed(boolean electricity,String from,boolean mainFrame,String method){
        // Alipay H5 may invoke its app through a hidden iframe. Only its exact H5
        // origin may do so without a main-frame navigation.
        return electricity&&(mainFrame||alipayWeb(from))&&"GET".equals(method)&&(IdentityPolicy.electricity(from)||alipayWeb(from));
    }
    static String alipayLink(String url){
        if(url==null)return null;
        String link=url;
        if(url.startsWith("intent://")){
            int split=url.indexOf("#Intent;");if(split<0||!url.endsWith(";end"))return null;
            String scheme=null,packageName=null;
            for(String part:url.substring(split+8,url.length()-4).split(";")){
                if(part.equals("SEL"))return null;
                if(part.startsWith("scheme=")){if(scheme!=null)return null;scheme=part.substring(7);}
                if(part.startsWith("package=")){if(packageName!=null)return null;packageName=part.substring(8);}
            }
            if(!"alipays".equals(scheme)&&!"alipay".equals(scheme))return null;
            if(packageName!=null&&!ALIPAY_PACKAGE.equals(packageName))return null;
            // Discard all supplied actions, components, flags, extras and fallback URLs.
            link=scheme+url.substring("intent".length(),split);
        }
        URI u=IdentityPolicy.navigationUri(link);
        if((!"alipays".equalsIgnoreCase(u.getScheme())&&!"alipay".equalsIgnoreCase(u.getScheme()))||!"platformapi".equalsIgnoreCase(u.getHost())||!"/startapp".equalsIgnoreCase(u.getPath())||u.getPort()!=-1||u.getRawUserInfo()!=null)return null;
        return link;
    }
}
