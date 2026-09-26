package cn.jhun.sanjiaohu;

import org.json.*;
import java.util.*;

final class UpdateManifest {
    final long revision,code,size;
    final int minSdk;
    final boolean enabled;
    final String name,url,gitee,mirror,sha256,releasePage,notes,json,canonical;
    UpdateManifest(String raw)throws Exception{
        if(raw==null||raw.length()>65536)throw new IllegalArgumentException("更新信息过大");
        checkNesting(raw);
        JSONObject o=new JSONObject(raw);
        if(number(o,"schemaVersion",1,1)!=1||!UpdatePolicy.PACKAGE.equals(string(o,"packageName",100))||!"stable".equals(string(o,"channel",20)))throw new IllegalArgumentException("更新信息不适用于三角狐");
        revision=number(o,"manifestRevision",1,2147483647L);code=number(o,"versionCode",1,2100000000);
        Object active=o.get("enabled");if(!(active instanceof Boolean))throw new IllegalArgumentException("更新开关无效");enabled=(Boolean)active;
        name=string(o,"versionName",40);if(!name.matches("[0-9]+(?:\\.[0-9]+){1,3}"))throw new IllegalArgumentException("版本名称无效");
        minSdk=(int)number(o,"minSdk",26,1000);releasePage=string(o,"releasePage",500);
        if(!releasePage.equals(UpdatePolicy.REPO+"releases/tag/v"+name))throw new IllegalArgumentException("发布页地址无效");
        JSONObject apk=o.getJSONObject("apk");url=string(apk,"url",600);
        if(!UpdatePolicy.apkUrl(url)||!url.equals(UpdatePolicy.REPO+"releases/download/v"+name+"/Sanjiaohu-"+name+".apk"))throw new IllegalArgumentException("安装包地址无效");
        Object giteeField=apk.opt("gitee");
        gitee=giteeField==null?"":string(apk,"gitee",600);
        if(!gitee.isEmpty()&&!UpdatePolicy.gitee(gitee,name))throw new IllegalArgumentException("Gitee 下载地址无效");
        size=number(apk,"sizeBytes",enabled?1:0,UpdatePolicy.MAX_APK);sha256=string(apk,"sha256",64).toLowerCase(Locale.ROOT);
        if(!sha256.matches("[a-f0-9]{64}")&&(enabled||!sha256.isEmpty()))throw new IllegalArgumentException("安装包校验值无效");
        JSONArray mirrors=apk.getJSONArray("mirrors");if(mirrors.length()>1)throw new IllegalArgumentException("不支持的下载线路");
        String accepted="";if(mirrors.length()==1){JSONObject m=mirrors.getJSONObject(0);accepted=string(m,"url",1000);if(!"ghproxy".equals(string(m,"id",30))||!UpdatePolicy.mirror(accepted,url))throw new IllegalArgumentException("不受支持的镜像地址");}mirror=accepted;
        JSONArray lines=o.getJSONArray("releaseNotes");if(lines.length()>30)throw new IllegalArgumentException("更新说明过长");StringBuilder text=new StringBuilder();
        for(int i=0;i<lines.length();i++){Object line=lines.get(i);if(!(line instanceof String)||((String)line).length()>500)throw new IllegalArgumentException("更新说明无效");if(i>0)text.append('\n');text.append("• ").append(line);}notes=text.toString();
        json=o.toString();canonical=canonical(o);
    }
    static String string(JSONObject o,String key,int max)throws Exception{Object v=o.get(key);if(!(v instanceof String)||((String)v).length()>max)throw new IllegalArgumentException("无效字段："+key);return (String)v;}
    static long number(JSONObject o,String key,long min,long max)throws Exception{Object v=o.get(key);if(!(v instanceof Number)||!v.toString().matches("[0-9]+"))throw new IllegalArgumentException("无效数字："+key);long n=Long.parseLong(v.toString());if(n<min||n>max)throw new IllegalArgumentException("数字超出范围："+key);return n;}
    // Bound nesting before Android's recursive JSON parser sees untrusted input.
    static void checkNesting(String raw){
        int depth=0;char quote=0;boolean escape=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(quote!=0){if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;}
            else if(c=='"')quote=c;
            else if(c=='/'||c=='\'')throw new IllegalArgumentException("更新信息必须是标准 JSON");
            else if(c=='{'||c=='['){if(++depth>16)throw new IllegalArgumentException("更新信息嵌套过深");}
            else if(c=='}'||c==']')depth--;
        }
    }
    static String canonical(Object value)throws Exception{
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String k:keys)b.append(JSONObject.quote(k)).append(':').append(canonical(o.get(k))).append(',');return b.append('}').toString();}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++)b.append(canonical(a.get(i))).append(',');return b.append(']').toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
    static UpdateManifest newest(List<UpdateManifest> results,UpdateManifest cached)throws Exception{
        if(results.isEmpty())throw new java.io.IOException("暂时无法检查更新，请稍后重试");
        Map<Long,String> seen=new HashMap<>();UpdateManifest best=cached;if(cached!=null)seen.put(cached.revision,cached.canonical);
        for(UpdateManifest m:results){String previous=seen.put(m.revision,m.canonical);if(previous!=null&&!previous.equals(m.canonical))throw new Conflict();if(best==null||m.revision>best.revision)best=m;}
        // A successful old cache response is not proof of the newest known revision.
        if(cached!=null&&results.stream().noneMatch(m->m.revision>=cached.revision))throw new java.io.IOException("更新来源尚未同步，请稍后重试");
        return best;
    }
    static final class Conflict extends java.io.IOException {Conflict(){super("更新信息异常：同一修订号内容不一致，已停止下载");}}
}
