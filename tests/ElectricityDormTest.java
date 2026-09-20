package cn.jhun.sanjiaohu;

import org.json.*;
import java.util.*;

/**
 * 留学生公寓 / 研究生公寓 的合并用电表处理。
 *
 * The school lists these dorms as two separate buildings ("X照明" and "X空调"),
 * while the page shows one option with an AC card and a light card. Both dorm
 * types must therefore be merged into a single option and behave identically.
 *
 * Synthetic directory data only: this never contacts the school and never
 * creates an order.
 */
public final class ElectricityDormTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static void equal(Object want,Object got){checks++;if(want==null?got!=null:!want.equals(got))throw new AssertionError(want+" != "+got);}

    static final String SOUTH="南校区照明";
    static final String SHARED="学生空调/南9-12北18-24空调和照明/商住区";

    /** The real 南校区照明 building list, plus the shared area's header rows. */
    static class Fake extends ElectricityApi {
        final List<String> requested=new ArrayList<>();
        Fake(){super("synthetic","test");}
        JSONObject get(String endpoint,String query)throws Exception{
            requested.add(endpoint+"?"+query);
            if(endpoint.equals("checkRoomCount.do"))
                return new JSONObject().put("businessData",new JSONObject().put("useFlag","1"));
            if(endpoint.equals("getRoomState.do")){
                // Distinguish the two meters of a room by their building id.
                boolean ac=query.contains("BK")||query.contains("AK");
                return new JSONObject().put("returncode","100")
                    .put("quantity",ac?"33.50":"11.50")
                    .put("quantityunit","度").put("canbuy","true");
            }
            JSONArray list=new JSONArray();
            if(query.contains("optype=1")){
                list.put(item("1",SHARED));
                list.put(item("2",SOUTH));
            } else if(query.contains("optype=2")){
                if(query.contains("areaid=1")){
                    list.put(item("29","北区16舍"));
                } else {
                    // exactly as the school returns it
                    list.put(item("1","南区1舍"));
                    list.put(item("9","南区9舍"));
                    list.put(item("QY","清源书店"));
                    list.put(item("B","留学生公寓B照明"));
                    list.put(item("BK","留学生公寓B空调"));
                    list.put(item("A","研究生公寓A照明"));
                    list.put(item("AK","研究生公寓A空调"));
                }
            } else if(query.contains("optype=3")){
                // Every building exposes floor "3层" (the real dorms label floors plainly).
                String build=value(query,"buildid");
                list.put(item("L-"+build,"3层"));
            } else if(query.contains("optype=4")){
                String build=value(query,"buildid"),level=value(query,"levelid");
                list.put(item("2-"+build+"-"+level+"-301","301"));
            }
            return new JSONObject().put("code","SUCCESS").put("roomlist",list);
        }
    }
    static JSONObject item(String id,String name)throws Exception{
        return new JSONObject().put("id",id).put("name",name);
    }
    static String value(String query,String key){
        for(String part:query.split("&"))if(part.startsWith(key+"="))return part.substring(key.length()+1);
        return "";
    }

    static Map<Integer,ElectricityApi.Meter> metersOf(ElectricityApi api,String building,String room)throws Exception{
        SortedMap<String,List<ElectricityApi.Branch>> buildings=api.buildings();
        SortedMap<String,List<ElectricityApi.Floor>> floors=api.floors(buildings.get(building));
        SortedMap<String,List<ElectricityApi.Meter>> rooms=api.rooms(floors.get("3"));
        Map<Integer,ElectricityApi.Meter> out=new TreeMap<>();
        for(ElectricityApi.Meter m:rooms.get(room))out.put(m.floor.kind,m);
        return out;
    }

    public static void main(String[] args)throws Exception{
        Fake api=new Fake();
        SortedMap<String,List<ElectricityApi.Branch>> buildings=api.buildings();

        // --- 两类公寓都合并成一个选项 ---
        check(buildings.containsKey("留学生公寓B"));
        check(buildings.containsKey("研究生公寓A"));
        check(!buildings.containsKey("留学生公寓B照明"));
        check(!buildings.containsKey("留学生公寓B空调"));
        check(!buildings.containsKey("研究生公寓A照明"));
        check(!buildings.containsKey("研究生公寓A空调"));
        equal(2,buildings.get("留学生公寓B").size());
        equal(2,buildings.get("研究生公寓A").size());
        // 7 rows in 南校区照明 minus the two merged pairs = 5 options, plus
        // 北区16舍 from the shared area = 6 names overall.
        equal(6,buildings.size());

        // --- 两个公寓走同一条路径，结果必须一致 ---
        Map<Integer,ElectricityApi.Meter> liu=metersOf(api,"留学生公寓B","301");
        Map<Integer,ElectricityApi.Meter> yan=metersOf(api,"研究生公寓A","301");
        check(liu.containsKey(ElectricityModel.AC));
        check(liu.containsKey(ElectricityModel.LIGHT));
        check(yan.containsKey(ElectricityModel.AC));
        check(yan.containsKey(ElectricityModel.LIGHT));
        equal(liu.keySet(),yan.keySet());
        equal(ElectricityModel.AC,liu.get(ElectricityModel.AC).floor.kind);
        equal(ElectricityModel.LIGHT,liu.get(ElectricityModel.LIGHT).floor.kind);
        // the two meters are genuinely different meters, not the same one twice
        check(!liu.get(ElectricityModel.AC).room.id.equals(liu.get(ElectricityModel.LIGHT).room.id));
        check(!yan.get(ElectricityModel.AC).room.id.equals(yan.get(ElectricityModel.LIGHT).room.id));
        // and the two dorms never collide on the same meter id
        check(!liu.get(ElectricityModel.AC).room.id.equals(yan.get(ElectricityModel.AC).room.id));

        // --- readings resolve per meter ---
        ElectricityApi.Reading ac=api.reading(liu.get(ElectricityModel.AC));
        ElectricityApi.Reading light=api.reading(liu.get(ElectricityModel.LIGHT));
        check(ac.ok&&light.ok);
        equal(33.50,ac.quantity);
        equal(11.50,light.quantity);

        // --- 不成对的楼栋不受影响 ---
        check(buildings.containsKey("南区1舍"));
        equal(1,buildings.get("南区1舍").size());
        check(buildings.containsKey("清源书店"));
        equal(1,buildings.get("清源书店").size());
        check(buildings.containsKey("北区16舍"));

        // --- 只出现一半时不合并 ---
        SortedMap<String,List<ElectricityApi.Branch>> onlyLight=new TreeMap<>();
        ElectricityApi.Item area=new ElectricityApi.Item("2",SOUTH);
        onlyLight.put("留学生公寓B照明",
            new ArrayList<>(Collections.singletonList(new ElectricityApi.Branch(area,new ElectricityApi.Item("B","留学生公寓B照明")))));
        SortedMap<String,List<ElectricityApi.Branch>> kept=ElectricityApi.pair(onlyLight);
        equal(1,kept.size());
        check(kept.containsKey("留学生公寓B照明"));

        // --- 幂等：已合并的目录再合并一次不变 ---
        SortedMap<String,List<ElectricityApi.Branch>> again=ElectricityApi.pair(buildings);
        equal(buildings.size(),again.size());
        for(String key:buildings.keySet())equal(buildings.get(key).size(),again.get(key).size());

        // --- 基名解析与边界 ---
        equal("留学生公寓B",ElectricityModel.meterBase("留学生公寓B空调"));
        equal("留学生公寓B",ElectricityModel.meterBase("留学生公寓B照明"));
        equal("研究生公寓A",ElectricityModel.meterBase("研究生公寓A空调"));
        equal(null,ElectricityModel.meterBase("北区16舍"));
        equal(null,ElectricityModel.meterBase("空调"));
        equal(null,ElectricityModel.meterBase("照明"));
        equal(null,ElectricityModel.meterBase(null));
        check(ElectricityModel.isAcHalf("留学生公寓B空调"));
        check(!ElectricityModel.isAcHalf("留学生公寓B照明"));
        check(ElectricityModel.isLightHalf("留学生公寓B照明"));
        check(!ElectricityModel.isLightHalf("留学生公寓B空调"));
        check(!ElectricityModel.isAcHalf("北区16舍"));
        check(!ElectricityModel.isAcHalf(null));

        // --- 共享区里的普通楼栋仍然按楼层标签分表 ---
        equal(ElectricityModel.AC,ElectricityModel.kind(SHARED,"北区16舍","2层（空调）"));
        equal(ElectricityModel.LIGHT,ElectricityModel.kind(SHARED,"北区16舍","2层（照明）"));
        equal(ElectricityModel.UNKNOWN,ElectricityModel.kind(SHARED,"北区16舍","2层"));

        System.out.println("Electricity dorm merge: "+checks+" checks passed");
    }
}
