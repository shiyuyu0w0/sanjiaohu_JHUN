package cn.jhun.sanjiaohu;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * 当前网络的标识，用于判断「换网了」。
 *
 * 图书馆的两条线路可达性**取决于所处网络**：
 *   - 校园网：桌面版与移动版都通
 *   - 流量（校园网外）：只有移动版通，桌面版 8080 端口不可达
 *
 * 所以线路选择必须能感知网络变化：一旦从流量切回校园网（或反之），
 * 就要立刻重新尝试桌面版，而不是死等免打扰窗口过期。
 */
final class LibraryNetwork {
    private LibraryNetwork(){}

    private static Context context;

    /** 由 Application/Activity 在启动时注入，用于查询网络状态。 */
    static void attach(Context value){
        if(value!=null)context=value.getApplicationContext();
    }

    /**
     * 网络签名。变化即代表换了网络（Wi-Fi <-> 流量，或换了另一个 Wi-Fi）。
     *
     * 形如 "WIFI|42" / "CELLULAR|17"；无法判断时返回 null（表示「未知」，
     * 此时调用方不应据此认为网络变了）。
     */
    static String signature(){
        Context c=context;
        if(c==null)return null;
        try{
            ConnectivityManager manager=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(manager==null)return null;
            Network active=manager.getActiveNetwork();
            if(active==null)return "NONE";
            NetworkCapabilities capabilities=manager.getNetworkCapabilities(active);
            if(capabilities==null)return "NONE";
            String kind;
            if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))kind="WIFI";
            else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))kind="CELLULAR";
            else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))kind="ETHERNET";
            else if(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))kind="VPN";
            else kind="OTHER";
            // netId 能区分「换到了另一个 Wi-Fi」——同一个 SSID 重连也算变化，
            // 对线路选择而言这是安全的（宁可多试一次，也不要卡在替补线路上）。
            return kind+"|"+active.getNetworkHandle();
        }catch(Exception e){
            return null;
        }
    }

    /** 当前是否在 Wi-Fi 上（校园网通常是 Wi-Fi）。仅用于界面提示。 */
    static boolean onWifi(){
        String signature=signature();
        return signature!=null&&signature.startsWith("WIFI");
    }
}
