package cn.jhun.sanjiaohu;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Read-only school API. The payment itself stays in the authenticated school WebView. */
class ElectricityApi {
    static final String ORIGIN="https://h5cloud.17wanxiao.com:18443";
    static final String PAGE=ORIGIN+"/CloudPayment/bill/selectPayProject.do?txcode=2&interurl=substituted_pay&payProId=7033&amtflag=0&payamt=&payproname=%E7%94%A8%E7%94%B5%E6%94%AF%E5%87%BA&subPayProId=";
    final String cookie,agent;
    ElectricityApi(String cookie,String agent){this.cookie=cookie;this.agent=agent;}
    static final class SessionExpired extends IOException {SessionExpired(){super("电费登录已过期，请重新登录");}}
    static final class Item {
        final String id,name;
        Item(String id,String name){this.id=id;this.name=name;}
        @Override public String toString(){return name;}
    }
    static final class Branch {
        final Item area,building;
        Branch(Item a,Item b){area=a;building=b;}
    }
    static final class Floor {
        final Branch branch;final Item level;final int kind;
        Floor(Branch b,Item l){branch=b;level=l;kind=ElectricityModel.kind(b.area.name,b.building.name,l.name);}
        Floor(Floor source,int inferredKind){branch=source.branch;level=source.level;kind=inferredKind;}
    }
    static final class Meter {
        final Floor floor;final Item room;
        Meter(Floor f,Item r){floor=f;room=r;}
        String description(){return floor.branch.area.name+"-"+floor.branch.building.name+"--"+floor.level.name+"-"+room.name;}
    }
    static final class Reading {
        final boolean ok,canBuy,expired;final double quantity;final String message;final long at;
        Reading(boolean ok,boolean canBuy,boolean expired,double quantity,String message){this.ok=ok;this.canBuy=canBuy;this.expired=expired;this.quantity=quantity;this.message=message;at=System.currentTimeMillis();}
        static Reading failure(String text,boolean expired){return new Reading(false,false,expired,0,text);}
    }
    JSONObject get(String endpoint,String query)throws Exception{
        if(cookie==null||cookie.isEmpty())throw new SessionExpired();
        if(Thread.currentThread().isInterrupted())throw new IOException("查询已取消");
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/CloudPayment/user/"+endpoint+"?"+query).openConnection();
        try{
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(18000);
            c.setRequestProperty("Cookie",cookie);c.setRequestProperty("User-Agent",agent);
            c.setRequestProperty("Referer",PAGE);c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-Requested-With","XMLHttpRequest");
            int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308||status==401||status==403)throw new SessionExpired();
            if(status!=200)throw new IOException("电费服务暂时不可用（"+status+"）");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("查询已取消");out.write(bytes,0,n);if(out.size()>1024*1024)throw new IOException("服务器响应过大");}
                String body=out.toString("UTF-8").trim();if(!body.startsWith("{"))throw new SessionExpired();return new JSONObject(body);
            }
        }finally{c.disconnect();}
    }
    void permission()throws Exception{
        JSONObject data=get("checkRoomCount.do","schoolCode=1862").optJSONObject("businessData");
        if(data==null)throw new IOException("无法确认学校选房权限，请稍后重试");
        if(!"1".equals(data.optString("useFlag")))throw new IOException("学校暂未允许选择房间："+data.optString("msg","请稍后重试"));
    }
    List<Item> list(int type,String area,String building,String level)throws Exception{
        JSONObject result=get("getRoom.do","payProId=7033&schoolcode=1862&businesstype=2&unitid=0&optype="+type+"&areaid="+enc(area)+"&buildid="+enc(building)+"&levelid="+enc(level));
        if(!"SUCCESS".equals(result.optString("code")))throw new IOException("房间列表获取失败，请刷新重试");
        JSONArray array=result.getJSONArray("roomlist");List<Item> out=new ArrayList<>();
        for(int i=0;i<array.length();i++){JSONObject item=array.getJSONObject(i);String id=item.optString("id"),name=item.optString("name");if(!id.isEmpty()&&!name.isEmpty()&&id.length()<160&&name.length()<160)out.add(new Item(id,name));}
        return out;
    }
    SortedMap<String,List<Branch>> buildings()throws Exception{
        permission();SortedMap<String,List<Branch>> out=new TreeMap<>();
        for(Item area:list(1,"0","0","0"))for(Item building:list(2,area.id,"0","0"))out.computeIfAbsent(building.name,k->new ArrayList<>()).add(new Branch(area,building));
        if(out.isEmpty())throw new IOException("学校未返回可选宿舍楼");return pair(out);
    }
    // Some dorms are listed as two buildings ("X照明" + "X空调") but the page shows a
    // single option with both meters. Pair suffixes only within the same area id.
    // Plain names retain the existing shared-meter/lighting directory grouping.
    static SortedMap<String,List<Branch>> pair(SortedMap<String,List<Branch>> source){
        SortedMap<String,SortedMap<String,List<Branch>>> areas=new TreeMap<>();
        SortedMap<String,List<List<Branch>>> options=new TreeMap<>();
        for(Map.Entry<String,List<Branch>> entry:source.entrySet()){
            String name=entry.getKey();
            if(ElectricityModel.meterBase(name)==null){
                addBuildingOption(options,name,entry.getValue());
            }else{
                for(Branch branch:entry.getValue()){
                    areas.computeIfAbsent(branch.area.id,k->new TreeMap<>())
                        .computeIfAbsent(name,k->new ArrayList<>()).add(branch);
                }
            }
        }
        for(SortedMap<String,List<Branch>> area:areas.values()){
            for(Map.Entry<String,List<Branch>> entry:area.entrySet()){
                String name=entry.getKey(),base=ElectricityModel.meterBase(name);
                String light=base+ElectricityModel.LIGHT_SUFFIX,ac=base+ElectricityModel.AC_SUFFIX;
                if(area.containsKey(light)&&area.containsKey(ac)){
                    if(!name.equals(light))continue; // Emit each pair once.
                    List<Branch> merged=new ArrayList<>(area.get(light));
                    merged.addAll(area.get(ac));addBuildingOption(options,base,merged);
                }else addBuildingOption(options,name,entry.getValue());
            }
        }
        SortedMap<String,List<Branch>> out=new TreeMap<>();
        // Reserve actual names before qualifying duplicates, so a generated label
        // cannot overwrite another building. Old ambiguous saved names disappear
        // and the panel asks the user to choose the area instead of guessing.
        Set<String> reserved=new HashSet<>(options.keySet());
        for(Map.Entry<String,List<List<Branch>>> entry:options.entrySet()){
            String name=entry.getKey();
            if(entry.getValue().size()==1){out.put(name,entry.getValue().get(0));continue;}
            for(List<Branch> branches:entry.getValue()){
                Item area=branches.get(0).area;
                String label=name+"（"+area.name+"）";
                if(reserved.contains(label)||out.containsKey(label))label=name+"（"+area.name+" · "+area.id+"）";
                String prefix=label;int index=2;
                while(reserved.contains(label)||out.containsKey(label))label=prefix+" · "+index++;
                out.put(label,branches);
            }
        }
        return out;
    }
    private static void addBuildingOption(Map<String,List<List<Branch>>> options,String name,List<Branch> branches){
        if(!branches.isEmpty())options.computeIfAbsent(name,k->new ArrayList<>()).add(new ArrayList<>(branches));
    }
    SortedMap<String,List<Floor>> floors(List<Branch> branches)throws Exception{
        permission();SortedMap<String,List<Floor>> out=new TreeMap<>(ElectricityModel::compareFloors);
        for(Branch b:branches)for(Item l:list(3,b.area.id,b.building.id,"0")){
            String number=ElectricityModel.floor(l.name);if(number!=null)out.computeIfAbsent(number,k->new ArrayList<>()).add(new Floor(b,l));
        }
        if(out.isEmpty())throw new IOException("学校未返回可选楼层");return out;
    }
    SortedMap<String,List<Meter>> rooms(List<Floor> floors)throws Exception{
        SortedMap<String,List<Meter>> out=new TreeMap<>();
        for(Floor f:floors)for(Item r:list(4,f.branch.area.id,f.branch.building.id,f.level.id))out.computeIfAbsent(ElectricityModel.room(r.name),k->new ArrayList<>()).add(new Meter(f,r));
        for(List<Meter> candidates:out.values())inferMissingKind(candidates);
        if(out.isEmpty())throw new IOException("该楼层没有可选房间");return out;
    }
    // Some ordinary building names occur in both a dedicated lighting/AC area
    // and the shared area, whose plain floor labels do not identify the meter.
    // Infer the other kind only for an unambiguous, room-level directory pair.
    // A lone unknown entry, a duplicate meter id, or more than two entries stays
    // unknown rather than being assigned a payment destination by guesswork.
    static void inferMissingKind(List<Meter> candidates){
        if(candidates.size()!=2)return;
        Meter a=candidates.get(0),b=candidates.get(1);
        Meter known=a.floor.kind==ElectricityModel.UNKNOWN?b:a;
        Meter unknown=a.floor.kind==ElectricityModel.UNKNOWN?a:b;
        if(unknown.floor.kind!=ElectricityModel.UNKNOWN
            ||(known.floor.kind!=ElectricityModel.AC&&known.floor.kind!=ElectricityModel.LIGHT)
            ||known.room.id.equals(unknown.room.id))return;
        Branch left=known.floor.branch,right=unknown.floor.branch;
        if(left.area.id.equals(right.area.id))return;
        // A dedicated area provides the positive type signal for the known
        // meter; the mixed area alone cannot prove which meter is which.
        String dedicated=left.area.name;
        boolean dedicatedAc=dedicated.contains("空调"),dedicatedLight=dedicated.contains("照明")||dedicated.contains("灯光");
        if(dedicatedAc==dedicatedLight||known.floor.kind!=(dedicatedAc?ElectricityModel.AC:ElectricityModel.LIGHT))return;
        String area=right.area.name;
        if(!area.contains("空调")||(!area.contains("照明")&&!area.contains("灯光")))return;
        int kind=known.floor.kind==ElectricityModel.AC?ElectricityModel.LIGHT:ElectricityModel.AC;
        candidates.set(candidates.indexOf(unknown),new Meter(new Floor(unknown.floor,kind),unknown.room));
    }
    Reading reading(Meter meter){
        try{
            JSONObject data=get("getRoomState.do","payProId=7033&schoolcode=1862&businesstype=2&roomverify="+enc(meter.room.id));
            if(!"100".equals(data.optString("returncode")))return Reading.failure("电表查询失败，请刷新重试",false);
            double value;
            try{value=ElectricityModel.quantity(data.optString("quantity",""),data.optString("quantityunit","度"));}catch(IllegalArgumentException invalid){return Reading.failure("学校暂未提供有效的电量读数",false);}
            return new Reading(true,"true".equalsIgnoreCase(data.optString("canbuy")),false,value,"");
        }catch(SessionExpired e){return Reading.failure(e.getMessage(),true);}catch(Exception e){return Reading.failure("查询失败，请检查网络后刷新",false);}
    }
    static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8");}
}
