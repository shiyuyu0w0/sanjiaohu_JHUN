package cn.jhun.sanjiaohu;

/** The order viewer can navigate only between the official read-only list and detail pages. */
final class ElectricityOrdersPolicy {
    static final String LIST="https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/record.do";
    static boolean path(String url,String path){return IdentityPolicy.cloudPayment(url)&&path.equals(IdentityPolicy.navigationUri(url).getPath());}
    static boolean list(String url){return path(url,"/CloudPayment/bill/record.do");}
    static boolean detail(String url){return path(url,"/CloudPayment/bill/detail.do");}
    static boolean page(String url){return list(url)||detail(url);}
    static boolean query(String url){return path(url,"/CloudPayment/bill/queryOrderList.do");}
    static boolean home(String url){return path(url,"/CloudPayment/bill/type.do");}
}
