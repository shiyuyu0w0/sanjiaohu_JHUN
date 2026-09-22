package cn.jhun.sanjiaohu;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class UpdateClient {
    interface Fetch {String get(String url,long deadline)throws Exception;}
    static UpdateManifest check(UpdateManifest cached)throws Exception{return check(cached,UpdateClient::fetch,12000);}
    static UpdateManifest check(UpdateManifest cached,Fetch fetch,long budget)throws Exception{
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budget),fallbackAt=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(budget/2);
        ExecutorService pool=Executors.newFixedThreadPool(3);CompletionService<UpdateManifest> done=new ExecutorCompletionService<>(pool);List<Future<UpdateManifest>> tasks=new ArrayList<>();List<UpdateManifest> valid=new ArrayList<>();
        try{
            for(int i=0;i<2;i++){final String url=UpdatePolicy.SOURCES[i];tasks.add(done.submit(()->new UpdateManifest(fetch.get(url,end))));}
            int received=0;boolean fallback=false;
            while(System.nanoTime()<end&&received<tasks.size()){
                long now=System.nanoTime();
                if(!fallback&&now>=fallbackAt){tasks.add(done.submit(()->new UpdateManifest(fetch.get(UpdatePolicy.SOURCES[2],end))));fallback=true;}
                long wait=Math.min(end-System.nanoTime(),fallback?end-System.nanoTime():Math.max(1,fallbackAt-System.nanoTime()));
                Future<UpdateManifest> task=done.poll(Math.max(1,wait),TimeUnit.NANOSECONDS);if(task==null)continue;received++;
                try{valid.add(task.get());}catch(ExecutionException failure){if(!fallback){tasks.add(done.submit(()->new UpdateManifest(fetch.get(UpdatePolicy.SOURCES[2],end))));fallback=true;}}
            }
            return UpdateManifest.newest(valid,cached);
        }finally{for(Future<?> task:tasks)task.cancel(true);pool.shutdownNow();}
    }
    static String fetch(String url,long deadline)throws Exception{
        for(int redirects=0;redirects<4;redirects++){
            if(!UpdatePolicy.source(url))throw new IOException("不受支持的更新来源");
            int remaining=(int)Math.min(4000,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));if(remaining<=0||Thread.currentThread().isInterrupted())throw new IOException("更新检查超时");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            try{
                c.setInstanceFollowRedirects(false);c.setConnectTimeout(remaining);c.setReadTimeout(remaining);c.setUseCaches(false);
                c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Sanjiaohu-Updater/1");c.setRequestProperty("Cookie","");c.setRequestProperty("Authorization","");
                int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){url=new URL(new URL(url),c.getHeaderField("Location")).toString();continue;}
                if(status!=200)throw new IOException("更新来源暂不可用");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("更新检查超时");if(out.size()+n>65536)throw new IOException("更新信息过大");out.write(bytes,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }finally{c.disconnect();}
        }throw new IOException("更新来源跳转异常");
    }
}
