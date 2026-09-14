package android.webkit;
import java.util.*;
/** Test-only origin storage fixture. */
public final class WebStorage {
    public static final WebStorage INSTANCE=new WebStorage();
    public final List<String> deleted=new ArrayList<>();
    public static WebStorage getInstance(){return INSTANCE;}
    public void deleteOrigin(String origin){deleted.add(origin);}
}
