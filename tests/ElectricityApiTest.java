package cn.jhun.sanjiaohu;

import org.json.*;
import java.util.*;
import java.io.*;

/** Synthetic directory responses; never connects to the school or creates an order. */
public final class ElectricityApiTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static class Fake extends ElectricityApi {
        String quantity="15.50",unit="度",code="100",canBuy="true",lastQuery;
        boolean expired,denied;
        Fake(){super("synthetic","test");}
        JSONObject get(String endpoint,String query)throws Exception{
            lastQuery=query;if(expired)throw new SessionExpired();
            if(endpoint.equals("checkRoomCount.do"))return new JSONObject().put("businessData",new JSONObject().put("useFlag",denied?"0":"1"));
            if(endpoint.equals("getRoomState.do"))return new JSONObject().put("returncode",code).put("quantity",quantity).put("quantityunit",unit).put("canbuy",canBuy);
            JSONArray list=new JSONArray();
            if(query.contains("optype=1"))list.put(new JSONObject().put("id","1").put("name","学生空调/空调和照明"));
            else if(query.contains("optype=2"))list.put(new JSONObject().put("id","7").put("name","合成样本楼"));
            else if(query.contains("optype=3")){list.put(new JSONObject().put("id","31").put("name","2层（空调）"));list.put(new JSONObject().put("id","92").put("name","2层（照明）"));}
            else if(query.contains("optype=4")){String level=query.contains("levelid=31")?"31":"92";list.put(new JSONObject().put("id","1-7--"+level+"-205").put("name","205"));}
            return new JSONObject().put("code","SUCCESS").put("roomlist",list);
        }
    }
    public static void main(String[] args)throws Exception{
        Fake api=new Fake();SortedMap<String,List<ElectricityApi.Branch>> buildings=api.buildings();check(buildings.size()==1);
        SortedMap<String,List<ElectricityApi.Floor>> floors=api.floors(buildings.get("合成样本楼"));check(floors.size()==1);check(floors.get("2").size()==2);
        // This is the UI callback's first lookup after selecting a building.
        // A fresh install / changed building has no previously selected floor.
        check(!floors.containsKey(""));
        check(!floors.containsKey("旧楼层"));
        check(!floors.containsKey("999999999999999999999"));
        check(floors.get("")==null);
        SortedMap<String,List<ElectricityApi.Meter>> rooms=api.rooms(floors.get("2"));check(rooms.size()==1);check(rooms.get("205").size()==2);
        ElectricityApi.Meter meter=rooms.get("205").get(0);check(meter.floor.kind==ElectricityModel.AC);check(rooms.get("205").get(1).floor.kind==ElectricityModel.LIGHT);
        ElectricityApi.Reading reading=api.reading(meter);check(reading.ok&&reading.quantity==15.50&&reading.canBuy);check(api.lastQuery.contains("roomverify=1-7--31-205"));
        api.quantity="0.00";check(api.reading(meter).ok&&api.reading(meter).quantity==0);
        api.canBuy="false";check(!api.reading(meter).canBuy);
        api.canBuy="true";api.quantity="NaN";check(!api.reading(meter).ok);api.quantity="";check(!api.reading(meter).ok);api.quantity="-1";check(!api.reading(meter).ok);
        api.quantity="10";api.unit="元";check(!api.reading(meter).ok);api.unit="度";api.code="FAIL";check(!api.reading(meter).ok);
        api.expired=true;check(api.reading(meter).expired);api.expired=false;api.denied=true;
        try{api.buildings();throw new AssertionError("Denied school permission ignored");}catch(IOException expected){checks++;}
        check(meter.description().contains("2层（空调）"));
        System.out.println("Electricity API: "+checks+" checks passed");
    }
}
