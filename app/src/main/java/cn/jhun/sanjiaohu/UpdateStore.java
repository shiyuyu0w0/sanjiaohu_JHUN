package cn.jhun.sanjiaohu;

import android.content.*;
import android.os.Build;
import java.util.concurrent.*;

final class UpdateStore {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static final ExecutorService CHECKS=Executors.newSingleThreadExecutor();
    private static boolean checking;
    private static final java.util.List<Done> listeners=new java.util.ArrayList<>();
    final SharedPreferences prefs;
    UpdateStore(Context c){prefs=c.getSharedPreferences("app_updates",Context.MODE_PRIVATE);}
    UpdateManifest manifest(){return read("manifest");}
    UpdateManifest task(){return read("task");}
    private UpdateManifest read(String key){try{return new UpdateManifest(prefs.getString(key,""));}catch(Exception e){return null;}}
    static long installed(Context c){try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Exception e){return Long.MAX_VALUE;}}
    boolean available(Context c){return !prefs.getBoolean("blocked",false)&&UpdatePolicy.canUpdate(manifest(),installed(c),Build.VERSION.SDK_INT);}
    interface Done {void complete(String error);}
    static synchronized void check(Context context,boolean manual,Done done){
        Context c=context.getApplicationContext();UpdateStore store=new UpdateStore(c);long now=System.currentTimeMillis();
        if(checking){if(done!=null)listeners.add(done);return;}
        if(!manual&&!UpdatePolicy.due(now,store.prefs.getLong("success",0),store.prefs.getLong("attempt",0)))return;
        checking=true;if(done!=null)listeners.add(done);store.prefs.edit().putLong("attempt",now).apply();
        CHECKS.execute(()->{
            String error=null;
            try{UpdateManifest m=UpdateClient.check(store.manifest());synchronized(UpdateDownload.class){store.prefs.edit().putString("manifest",m.json).putBoolean("blocked",false).putLong("success",System.currentTimeMillis()).commit();UpdateManifest t=store.task();if(t!=null&&(!m.enabled||m.code!=t.code||!m.sha256.equals(t.sha256)))UpdateDownload.cancel(c,"版本信息已变更，请重新选择更新");}}
            catch(Exception e){error=e instanceof java.io.IOException?e.getMessage():"暂时无法检查更新，请稍后重试";if(e instanceof UpdateManifest.Conflict){synchronized(UpdateDownload.class){store.prefs.edit().putBoolean("blocked",true).commit();UpdateDownload.cancel(c,error);}}}
            java.util.List<Done> callbacks;
            synchronized(UpdateStore.class){checking=false;callbacks=new java.util.ArrayList<>(listeners);listeners.clear();}
            for(Done callback:callbacks)callback.complete(error);
        });
    }
}
