package cn.jhun.sanjiaohu;

import org.json.JSONObject;
import java.util.*;

/** Synthetic school directory: no network requests and no payment submission. */
public final class ElectricitySharedAreaTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("check "+checks);}
    static void equal(Object expected,Object actual){checks++;if(!Objects.equals(expected,actual))throw new AssertionError(expected+" != "+actual);}

    static final String SHARED="学生空调/南9-12北18-24空调和照明/商住区";
    static final String[] NORTH={"北区18舍"};
    static final String[] SOUTH={"南区9舍"};
    static final String[] EXPLICIT={"北区19舍","北区20舍","北区21舍","北区22舍","北区23舍","北区24舍"};
    static final String[] UNITS={"南区10舍","南区11舍","南区12舍"};

    static final class Fake extends ElectricityApi {
        final boolean separateLighting,extraShared;
        Fake(boolean separateLighting,boolean extraShared){super("synthetic","test");this.separateLighting=separateLighting;this.extraShared=extraShared;}
        void permission(){}
        List<Item> list(int type,String area,String building,String level){
            List<Item> out=new ArrayList<>();
            if(type==1){
                out.add(new Item("S",SHARED));
                if(separateLighting){out.add(new Item("N","北校区照明"));out.add(new Item("D","南校区照明"));}
                if(extraShared)out.add(new Item("S2",SHARED));
            }else if(type==2){
                if(area.equals("S")||area.equals("S2")){
                    for(String name:NORTH)out.add(new Item(area+name,name));
                    for(String name:SOUTH)out.add(new Item(area+name,name));
                    if(area.equals("S")){for(String name:EXPLICIT)out.add(new Item(area+name,name));for(String name:UNITS)out.add(new Item(area+name,name));}
                }else if(area.equals("N"))for(String name:NORTH)out.add(new Item(area+name,name));
                else if(area.equals("D"))for(String name:SOUTH)out.add(new Item(area+name,name));
            }else if(type==3){
                if(Arrays.stream(EXPLICIT).anyMatch(building::endsWith)){
                    out.add(new Item("2-light-"+building,"2层（照明）"));
                    out.add(new Item("2-ac-"+building,"2层（空调）"));
                }else if(Arrays.stream(UNITS).anyMatch(building::endsWith))out.add(new Item("1-unit-"+building,"1单元"));
                else out.add(new Item("2-"+building,"2层"));
            }else if(type==4)out.add(new Item(area+"/"+building+"/"+level+"/205",level.startsWith("1-unit-")?"1101":area.equals("N")||area.equals("D")?"照明205":"205"));
            return out;
        }
        JSONObject get(String endpoint,String query)throws Exception{
            if(!endpoint.equals("getRoomState.do"))throw new AssertionError(endpoint);
            return new JSONObject().put("returncode","100")
                .put("quantity",query.contains("roomverify=S%2F")?"42.50":"12.25")
                .put("quantityunit","度").put("canbuy","true");
        }
    }

    static List<ElectricityApi.Meter> meters(Fake api,String name)throws Exception{
        SortedMap<String,List<ElectricityApi.Branch>> buildings=api.buildings();
        check(buildings.containsKey(name));
        SortedMap<String,List<ElectricityApi.Floor>> floors=api.floors(buildings.get(name));
        check(floors.containsKey("2"));
        SortedMap<String,List<ElectricityApi.Meter>> rooms=api.rooms(floors.get("2"));
        check(rooms.containsKey("205"));
        equal(1,rooms.size());
        return rooms.get("205");
    }

    static void checkBoth(Fake api,String name)throws Exception{
        List<ElectricityApi.Meter> meters=meters(api,name);
        equal(2,meters.size());
        ElectricityApi.Meter ac=null,light=null;
        for(ElectricityApi.Meter meter:meters){
            if(meter.floor.kind==ElectricityModel.AC)ac=meter;
            if(meter.floor.kind==ElectricityModel.LIGHT)light=meter;
        }
        check(ac!=null);check(light!=null);
        check(!ac.room.id.equals(light.room.id));
        equal("S",ac.floor.branch.area.id);
        equal(name,ac.floor.branch.building.name);
        check(api.reading(ac).ok&&api.reading(light).ok);
    }

    public static void main(String[] args)throws Exception{
        Fake paired=new Fake(true,false);
        for(String name:NORTH)checkBoth(paired,name);
        for(String name:SOUTH)checkBoth(paired,name);

        // The live page labels North 19–24 floors explicitly by meter type.
        for(String name:EXPLICIT){
            SortedMap<String,List<ElectricityApi.Branch>> buildings=paired.buildings();
            SortedMap<String,List<ElectricityApi.Floor>> floors=paired.floors(buildings.get(name));
            equal(2,floors.get("2").size());
            List<ElectricityApi.Meter> rows=paired.rooms(floors.get("2")).get("205");
            equal(2,rows.size());
            Set<Integer> kinds=new HashSet<>();for(ElectricityApi.Meter row:rows){kinds.add(row.floor.kind);check(paired.reading(row).ok);}
            equal(new HashSet<>(Arrays.asList(ElectricityModel.AC,ElectricityModel.LIGHT)),kinds);
        }

        // South 10–12 use units in place of the school's level-3 floor and
        // return a single meter without an AC/lighting label.
        for(String name:UNITS){
            SortedMap<String,List<ElectricityApi.Branch>> buildings=paired.buildings();
            SortedMap<String,List<ElectricityApi.Floor>> floors=paired.floors(buildings.get(name));
            check(floors.containsKey("1单元"));
            List<ElectricityApi.Meter> rows=paired.rooms(floors.get("1单元")).get("1101");
            equal(1,rows.size());equal(ElectricityModel.UNKNOWN,rows.get(0).floor.kind);
            check(paired.reading(rows.get(0)).ok);
        }

        // A single unlabelled shared-area meter cannot safely be called AC.
        List<ElectricityApi.Meter> single=meters(new Fake(false,false),"北区18舍");
        equal(1,single.size());equal(ElectricityModel.UNKNOWN,single.get(0).floor.kind);

        // Two unlabelled shared-area meters plus one lighting meter remain ambiguous.
        List<ElectricityApi.Meter> ambiguous=meters(new Fake(true,true),"北区18舍");
        equal(3,ambiguous.size());int ac=0,unknown=0,light=0;
        for(ElectricityApi.Meter meter:ambiguous){
            if(meter.floor.kind==ElectricityModel.AC)ac++;
            else if(meter.floor.kind==ElectricityModel.LIGHT)light++;
            else unknown++;
        }
        equal(0,ac);equal(2,unknown);equal(1,light);
        System.out.println("Electricity shared area: "+checks+" checks passed");
    }
}
