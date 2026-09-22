package cn.jhun.sanjiaohu;

import android.app.DownloadManager;
import android.content.*;

public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Context c=context.getApplicationContext();
        if(UpdateInstaller.ACTION.equals(intent.getAction())){UpdateInstaller.status(c,intent);return;}
        if(DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())&&intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-2)==new UpdateStore(c).prefs.getLong("downloadId",-1)){
            // Defer potentially large verification to the next foreground visit.
            // The task id and system status survive process death.
            new UpdateStore(c).prefs.edit().putBoolean("downloadChanged",true).apply();
        }
    }
}
