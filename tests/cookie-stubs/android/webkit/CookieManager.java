package android.webkit;
import java.util.*;
import java.util.function.Consumer;
/** Test-only cookie fixture; never packaged in the application. */
public final class CookieManager {
    public static final CookieManager INSTANCE=new CookieManager();
    public final List<String> writes=new ArrayList<>();
    public static CookieManager getInstance(){return INSTANCE;}
    public String getCookie(String url){return "SESSION=synthetic; SESSIONID=synthetic";}
    public void setCookie(String url,String value,Consumer<Boolean> callback){writes.add(url+" "+value);callback.accept(true);}
    public void flush(){}
}
