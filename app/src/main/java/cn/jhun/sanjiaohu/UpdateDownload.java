package cn.jhun.sanjiaohu;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.UUID;

final class UpdateDownload {
    static DownloadManager manager(Context c){return (DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);}
    static synchronized void start(Context c,UpdateManifest m,int route)throws Exception{
        UpdateStore s=new UpdateStore(c);UpdateManifest latest=s.manifest();
        if(!s.available(c)||latest==null||!latest.canonical.equals(m.canonical))throw new IOException("请先检查有效的新版本");
        if(!validRoute(m,route))throw new IOException("下载地址无效");
        cancel(c,"");s.prefs.edit().putString("task",m.json).commit();
        enqueue(c,s,m,route);
    }
    private static boolean validRoute(UpdateManifest m,int route){
        String url=UpdatePolicy.downloadUrl(m,route);
        return url!=null&&(route==UpdatePolicy.GITEE?UpdatePolicy.gitee(url,m.name):route==UpdatePolicy.GHPROXY?UpdatePolicy.mirror(url,m.url):route==UpdatePolicy.GITHUB&&UpdatePolicy.apkUrl(url));
    }
    private static void enqueue(Context c,UpdateStore s,UpdateManifest m,int route)throws Exception{
        if(!validRoute(m,route))throw new IOException("下载地址无效");
        String url=UpdatePolicy.downloadUrl(m,route),label=UpdatePolicy.routeName(route);
        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));request.setTitle("三角狐 "+m.name).setDescription(label);
        // Only the verified private snapshot should offer installation in the app.
        request.setMimeType("application/vnd.android.package-archive");request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setAllowedOverMetered(true);request.setAllowedOverRoaming(false);
        request.setDestinationInExternalFilesDir(c,"updates",UUID.randomUUID()+".apk");
        long id=manager(c).enqueue(request);
        if(!s.prefs.edit().putLong("downloadId",id).putLong("bytes",0).putInt("route",route).remove("direct").remove("triedDirect").putString("phase","downloading").putString("message","正在使用 "+label+" 下载").remove("verifiedId").commit()){manager(c).remove(id);throw new IOException("无法保存下载任务");}
    }
    static synchronized void cancel(Context c,String message){
        UpdateStore s=new UpdateStore(c);long id=s.prefs.getLong("downloadId",-1),verified=s.prefs.getLong("verifiedId",-1);
        s.prefs.edit().remove("downloadId").remove("verifiedId").remove("task").remove("route").putString("phase","idle").putString("message",message).commit();
        if(id!=-1){try{manager(c).remove(id);}catch(Exception ignored){}UpdateVerifier.file(c,id).delete();}
        if(verified!=-1)UpdateVerifier.file(c,verified).delete();UpdateInstaller.abandon(c);
    }
    static boolean online(Context c){ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    static void reconcile(Context c){
        UpdateStore s=new UpdateStore(c);UpdateManifest m;long id;
        synchronized(UpdateDownload.class){
            m=s.task();if(m==null)return;
            if(UpdateStore.installed(c)>=m.code){cancel(c,"已更新到新版本");return;}
            id=s.prefs.getLong("downloadId",-1);if(id==-1)return;
        }
        try(Cursor cur=manager(c).query(new DownloadManager.Query().setFilterById(id))){
            if(cur==null||!cur.moveToFirst()){failed(c,id,"下载任务已被移除");return;}
            int state=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long received=cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            synchronized(UpdateDownload.class){if(s.prefs.getLong("downloadId",-1)!=id)return;s.prefs.edit().putLong("bytes",received).apply();}
            if(received>m.size){failed(c,id,"安装包大小异常");return;}
            if(state==DownloadManager.STATUS_SUCCESSFUL){
                synchronized(UpdateDownload.class){if(s.prefs.getLong("downloadId",-1)!=id)return;s.prefs.edit().putString("phase","verifying").putString("message","正在校验安装包…").commit();}
                File file;
                file=UpdateVerifier.snapshot(c,id,new ParcelFileDescriptor.AutoCloseInputStream(manager(c).openDownloadedFile(id)),m);
                synchronized(UpdateDownload.class){
                    if(s.prefs.getLong("downloadId",-1)!=id){file.delete();return;}
                    s.prefs.edit().putLong("verifiedId",id).remove("downloadId").putString("phase","ready").putString("message","下载完成，安装包校验通过").commit();manager(c).remove(id);
                }
            }else if(state==DownloadManager.STATUS_FAILED){failed(c,id,"当前线路下载失败");}
            else synchronized(UpdateDownload.class){if(s.prefs.getLong("downloadId",-1)==id)s.prefs.edit().putString("phase","downloading").putString("message",state==DownloadManager.STATUS_PAUSED?"等待网络或系统继续下载…":"正在下载…").apply();}
        }catch(Exception e){failed(c,id,"下载或校验失败，请重试");}
    }
    private static synchronized void failed(Context c,long id,String message){
        UpdateStore s=new UpdateStore(c);if(s.prefs.getLong("downloadId",-1)!=id)return;
        UpdateManifest m=s.task();if(m==null)return;
        if(!online(c)){s.prefs.edit().putString("message","等待网络恢复后继续处理…").apply();return;}
        manager(c).remove(id);UpdateVerifier.file(c,id).delete();s.prefs.edit().remove("downloadId").commit();
        int current=s.prefs.getInt("route",s.prefs.getBoolean("direct",false)?UpdatePolicy.GITHUB:UpdatePolicy.GHPROXY);
        for(int next=UpdatePolicy.nextRoute(m,current);next!=-1;next=UpdatePolicy.nextRoute(m,next)){
            try{enqueue(c,s,m,next);s.prefs.edit().putString("message",UpdatePolicy.routeName(current)+" 暂不可用，正在尝试 "+UpdatePolicy.routeName(next)).apply();return;}catch(Exception ignored){}
        }
        s.prefs.edit().putString("phase","error").putString("message",message+"，可重试、切换线路或打开发布页").commit();
    }
}
