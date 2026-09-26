package cn.jhun.sanjiaohu;

import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;

public final class UpdateTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    interface Work {void run()throws Exception;}
    static void rejects(Work work,String why)throws Exception{checks++;try{work.run();}catch(Exception expected){return;}throw new AssertionError(why);}
    static JSONObject fixture(long revision,long code)throws Exception{
        String url=UpdatePolicy.REPO+"releases/download/v1.1.2/Sanjiaohu-1.1.2.apk";
        return new JSONObject().put("schemaVersion",1).put("manifestRevision",revision).put("enabled",true).put("channel","stable")
            .put("packageName",UpdatePolicy.PACKAGE).put("versionCode",code).put("versionName","1.1.2").put("minSdk",26)
            .put("releaseNotes",new JSONArray().put("测试更新")).put("releasePage",UpdatePolicy.REPO+"releases/tag/v1.1.2")
            .put("apk",new JSONObject().put("url",url).put("gitee",UpdatePolicy.giteeUrl("1.1.2")).put("sizeBytes",200).put("sha256",String.join("",Collections.nCopies(64,"a")))
                .put("mirrors",new JSONArray().put(new JSONObject().put("id","ghproxy").put("name","加速线路").put("url",UpdatePolicy.PROXY+url))));
    }
    public static void main(String[] args)throws Exception{
        UpdateManifest a=new UpdateManifest(fixture(1,45).toString()),b=new UpdateManifest(fixture(2,46).toString());
        check(UpdatePolicy.canUpdate(a,44,26),"new version offered");check(!UpdatePolicy.canUpdate(a,45,26),"equal code not offered");check(!UpdatePolicy.canUpdate(a,46,26),"downgrade not offered");check(!UpdatePolicy.canUpdate(a,44,25),"incompatible system");
        UpdateManifest disabled=new UpdateManifest(fixture(3,46).put("enabled",false).toString());check(!UpdatePolicy.canUpdate(disabled,44,36),"disabled release not offered");
        check(UpdateManifest.newest(Arrays.asList(a,b),null)==b,"newest revision wins regardless of order");check(UpdateManifest.newest(Arrays.asList(b,a),null)==b,"newest first");check(UpdateManifest.newest(Arrays.asList(b,disabled),null)==disabled,"newer withdrawal wins");
        rejects(()->UpdateManifest.newest(Arrays.asList(a),b),"old cache cannot roll back last known revision");rejects(()->UpdateManifest.newest(Collections.emptyList(),b),"cache alone is not a successful check");
        JSONObject conflict=fixture(1,45).put("enabled",false);rejects(()->UpdateManifest.newest(Arrays.asList(a,new UpdateManifest(conflict.toString())),null),"conflicting equal revisions stop updates");
        rejects(()->UpdateManifest.newest(Arrays.asList(new UpdateManifest(conflict.toString())),a),"conflict with persistent accepted manifest");
        JSONObject reverse=new JSONObject();JSONObject original=fixture(1,45);List<String> keys=new ArrayList<>(original.keySet());Collections.reverse(keys);for(String key:keys)reverse.put(key,original.get(key));check(new UpdateManifest(reverse.toString()).canonical.equals(a.canonical),"JSON field order does not conflict");
        for(String key:new String[]{"schemaVersion","manifestRevision","versionCode","minSdk"})rejects(()->new UpdateManifest(fixture(1,45).put(key,"45").toString()),"numeric strings rejected: "+key);
        rejects(()->new UpdateManifest(fixture(1,45).put("manifestRevision",new java.math.BigInteger("18446744073709551617")).toString()),"oversized integer cannot wrap into an accepted revision");
        JSONObject nested=fixture(1,45),cursor=nested;for(int i=0;i<20;i++){JSONObject next=new JSONObject();cursor.put("extra",next);cursor=next;}rejects(()->new UpdateManifest(nested.toString()),"deep nesting rejected before JSON parsing");
        check(new UpdateManifest(fixture(1,45).put("releaseNotes",new JSONArray().put("quoted brackets: [ { \\\" ' / https://example.com")).toString()).code==45,"brackets and escapes inside strings do not count as nesting");
        for(String key:new String[]{"packageName","channel","releasePage","versionName"})rejects(()->new UpdateManifest(fixture(1,45).put(key,"wrong").toString()),"wrong identity: "+key);
        rejects(()->new UpdateManifest(fixture(0,45).toString()),"revision must be positive");rejects(()->new UpdateManifest(fixture(1,45).put("enabled","true").toString()),"switch must be boolean");
        for(long size:new long[]{0,-1,UpdatePolicy.MAX_APK+1}){JSONObject o=fixture(1,45);o.getJSONObject("apk").put("sizeBytes",size);rejects(()->new UpdateManifest(o.toString()),"invalid APK length");}
        JSONObject hash=fixture(1,45);hash.getJSONObject("apk").put("sha256","");rejects(()->new UpdateManifest(hash.toString()),"empty hash in enabled release");
        hash.put("enabled",false);hash.getJSONObject("apk").put("sizeBytes",0);check(!new UpdateManifest(hash.toString()).enabled,"disabled bootstrap manifest");
        String good=a.url;
        for(String bad:new String[]{good.replace("https:","http:"),good+"?redirect=evil",good+"#x",good.replace("github.com/","github.com.evil/"),good.replace("github.com/","github.com@evil/"),good.replace("shiyuyu0w0/","someone/"),good.replace("v1.1.2/","../"),good.replace("Sanjiaohu-","%53anjiaohu-"),good.replace("github.com","github.com:443")})check(!UpdatePolicy.apkUrl(bad),"reject URL: "+bad);
        check(UpdatePolicy.mirror(UpdatePolicy.PROXY+good,good),"exact nested repo allowed");check(!UpdatePolicy.mirror(UpdatePolicy.PROXY+good+"/../x",good),"mirror path cannot escape");
        check(UpdatePolicy.gitee(a.gitee,a.name),"exact Gitee release URL accepted");
        for(String bad:new String[]{a.gitee.replace("https:","http:"),a.gitee+"?redirect=evil",a.gitee+"#x",a.gitee.replace("gitee.com/","gitee.com.evil/"),a.gitee.replace("shiyuyu0w0/","someone/"),a.gitee.replace("v1.1.2/","v1.1.3/"),a.gitee.replace("gitee.com","gitee.com:443")}){
            check(!UpdatePolicy.gitee(bad,a.name),"reject Gitee URL: "+bad);
            JSONObject tampered=fixture(1,45);tampered.getJSONObject("apk").put("gitee",bad);
            rejects(()->new UpdateManifest(tampered.toString()),"remote cannot redirect Gitee source");
        }
        check(UpdatePolicy.firstRoute(a)==UpdatePolicy.GITEE,"Gitee is preferred");
        check(UpdatePolicy.nextRoute(a,UpdatePolicy.GITEE)==UpdatePolicy.GHPROXY,"Gitee falls back to GitHub proxy");
        check(UpdatePolicy.nextRoute(a,UpdatePolicy.GHPROXY)==UpdatePolicy.GITHUB,"proxy falls back to GitHub official");
        check(UpdatePolicy.nextRoute(a,UpdatePolicy.GITHUB)==-1,"automatic retries stop after official");
        check(UpdatePolicy.nextChoice(a,UpdatePolicy.GITHUB)==UpdatePolicy.GITEE,"manual route choice wraps");
        JSONObject legacyJson=fixture(1,45);legacyJson.getJSONObject("apk").remove("gitee");
        UpdateManifest legacy=new UpdateManifest(legacyJson.toString());
        check(legacy.gitee.isEmpty()&&UpdatePolicy.firstRoute(legacy)==UpdatePolicy.GHPROXY,"old manifest keeps existing route order");
        JSONObject malicious=fixture(1,45);malicious.getJSONObject("apk").getJSONArray("mirrors").getJSONObject(0).put("url","https://evil.test/x.apk");rejects(()->new UpdateManifest(malicious.toString()),"remote cannot introduce arbitrary mirror");
        long now=2*UpdatePolicy.DAY;check(UpdatePolicy.due(now,0,0),"first automatic check");check(!UpdatePolicy.due(now,now-1000,0),"24 hour success interval");check(!UpdatePolicy.due(now,0,now-1000),"failed checks back off");check(UpdatePolicy.due(now,now+1000,now+1000),"clock rollback recovers");
        UpdateManifest actual=UpdateClient.check(null,(url,end)->{if(url.equals(UpdatePolicy.SOURCES[1])){Thread.sleep(80);return b.json;}return a.json;},1000);check(actual.revision==2,"wait for newer primary, not first old response");
        actual=UpdateClient.check(null,(url,end)->{if(!url.equals(UpdatePolicy.SOURCES[2]))throw new IOException("offline");return b.json;},1000);check(actual.revision==2,"proxy manifest fallback");
        rejects(()->UpdateClient.check(a,(url,end)->{throw new IOException("offline");},500),"all failures never latest");
        rejects(()->UpdateClient.check(null,(url,end)->url.equals(UpdatePolicy.SOURCES[0])?a.json:conflict.toString(),1000),"network conflict fails closed");
        long started=System.nanoTime();rejects(()->UpdateClient.check(null,(url,end)->{Thread.sleep(3000);return a.json;},150),"check deadline");check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<1000,"total deadline is bounded");
        System.out.println("Update policy and multi-source checks: "+checks+" passed");
    }
}
