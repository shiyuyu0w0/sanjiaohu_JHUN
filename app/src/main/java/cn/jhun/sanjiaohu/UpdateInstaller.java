package cn.jhun.sanjiaohu;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.Build;
import java.io.*;
import java.util.UUID;

final class UpdateInstaller {
    static final String ACTION="cn.jhun.sanjiaohu.UPDATE_INSTALL_RESULT";
    static volatile Intent confirmation;
    static void install(Context c)throws Exception{
        synchronized(UpdateDownload.class){
            UpdateStore s=new UpdateStore(c);UpdateManifest m=s.task();long id=s.prefs.getLong("verifiedId",-1);
            if(m==null||id<0||!s.available(c)||!c.getPackageManager().canRequestPackageInstalls())throw new IOException("请先允许安装并下载有效的新版本");
            UpdateManifest latest=s.manifest();if(latest.code!=m.code||!latest.sha256.equals(m.sha256))throw new IOException("版本信息已变化，请重新下载");
            File file=UpdateVerifier.file(c,id);UpdateVerifier.verify(c,file,m);abandon(c);
            PackageInstaller installer=c.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(UpdatePolicy.PACKAGE);params.setSize(m.size);if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            int sessionId=installer.createSession(params);String nonce=UUID.randomUUID().toString();
            try(PackageInstaller.Session session=installer.openSession(sessionId)){
                try(InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,m.size)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}
                Intent callback=new Intent(c,UpdateReceiver.class).setAction(ACTION).setData(android.net.Uri.parse("sanjiaohu-update://install/"+nonce));
                int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                PendingIntent pending=PendingIntent.getBroadcast(c,sessionId,callback,flags);
                if(!s.prefs.edit().putInt("session",sessionId).putString("nonce",nonce).putString("phase","installing").putString("message","正在等待系统安装确认…").commit())throw new IOException("无法保存安装状态");
                session.commit(pending.getIntentSender());
            }catch(Exception e){installer.abandonSession(sessionId);s.prefs.edit().remove("session").putString("phase","ready").commit();throw e;}
        }
    }
    static void abandon(Context c){UpdateStore s=new UpdateStore(c);int id=s.prefs.getInt("session",-1);s.prefs.edit().remove("session").remove("nonce").apply();confirmation=null;if(id>=0)try{c.getPackageManager().getPackageInstaller().abandonSession(id);}catch(Exception ignored){}}
    static void status(Context c,Intent intent){
        synchronized(UpdateDownload.class){
            UpdateStore s=new UpdateStore(c);String nonce=s.prefs.getString("nonce","");
            if(nonce.isEmpty()||!android.net.Uri.parse("sanjiaohu-update://install/"+nonce).equals(intent.getData())||intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=s.prefs.getInt("session",-1))return;
            int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
            if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);s.prefs.edit().putString("phase","confirm").putString("message","请在系统界面确认安装；也可点击继续安装").commit();UpdatePrompt prompt=UpdatePrompt.visible.get();if(prompt!=null&&confirmation!=null)prompt.host.runOnUiThread(prompt::continueConfirmation);}
            else if(status==PackageInstaller.STATUS_SUCCESS){UpdateManifest m=s.task();if(m!=null&&UpdateStore.installed(c)>=m.code)UpdateDownload.cancel(c,"已更新到新版本");else s.prefs.edit().putString("message","系统已处理安装，重新打开应用后确认版本").apply();}
            else{abandon(c);s.prefs.edit().putString("phase","ready").putString("message",status==PackageInstaller.STATUS_FAILURE_ABORTED?"已取消安装，可稍后重试":"系统未完成安装，请检查空间、安装权限后重试").commit();}
        }
    }
}
