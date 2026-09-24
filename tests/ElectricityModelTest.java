package cn.jhun.sanjiaohu;

public final class ElectricityModelTest {
    static int count;
    static void equal(Object want,Object got){count++;if(!want.equals(got))throw new AssertionError(want+" != "+got);}
    static void rejected(String value,boolean integer){count++;try{ElectricityModel.amount(value,integer);throw new AssertionError("Accepted "+value);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        equal("",ElectricityModel.savedSelection(null));equal("",ElectricityModel.savedSelection(2));equal("2",ElectricityModel.savedSelection(" 2 "));
        java.util.TreeMap<String,String> floorList=new java.util.TreeMap<>(ElectricityModel::compareFloors);
        floorList.put("10","10层");floorList.put("2","2层");floorList.put("1","1层");equal("[1, 2, 10]",floorList.keySet().toString());
        for(Object saved:new Object[]{null,"","旧楼层",2,"999999999999999999999"}){equal(false,floorList.containsKey(ElectricityModel.savedSelection(saved)));}
        // Comparator must remain transitive even when a missing preference is looked up.
        String[] keys={"","1","01","2","10","99","100","旧楼层"};
        for(String a:keys)for(String b:keys)for(String c:keys){if(ElectricityModel.compareFloors(a,b)<=0&&ElectricityModel.compareFloors(b,c)<=0)equal(true,ElectricityModel.compareFloors(a,c)<=0);}
        equal("2",ElectricityModel.floor("2层（空调）"));equal("10",ElectricityModel.floor("10层（照明）"));equal("0",ElectricityModel.floor("0层商业门店"));
        equal("1单元",ElectricityModel.floor("1单元"));equal("5单元",ElectricityModel.floor("5 单元"));
        equal("4 层",ElectricityModel.levelLabel("4"));equal("1单元",ElectricityModel.levelLabel("1单元"));
        equal(ElectricityModel.AC,ElectricityModel.kind("学生空调/南9-12北18-24空调和照明/商住区","北区16舍","2层（空调）"));
        equal(ElectricityModel.LIGHT,ElectricityModel.kind("北校区照明","北区16舍","2层"));
        equal(ElectricityModel.LIGHT,ElectricityModel.kind("学生空调/空调和照明","北区20舍","2层（照明）"));
        equal(ElectricityModel.UNKNOWN,ElectricityModel.kind("学生空调/空调和照明","北区20舍","2层"));
        equal(ElectricityModel.UNKNOWN,ElectricityModel.kind("学生空调","北区20舍","2层（空调和照明）"));
        equal("217",ElectricityModel.room("3-16-217"));equal("217",ElectricityModel.room("217"));equal("101A",ElectricityModel.room("2-15A-101A"));equal("门店A",ElectricityModel.room("门店A"));
        equal("10.00",ElectricityModel.amount("10",false));equal("0.01",ElectricityModel.amount("0.01",false));equal("20.10",ElectricityModel.amount("20.1",false));equal("99999.00",ElectricityModel.amount("99999",false));equal("100.00",ElectricityModel.amount("100.00",true));
        for(String value:new String[]{"", "0", "-1", "0.001", "1e2", "NaN", "Infinity", "100000", "99999.99", "12.345", "1;alert(1)", ".5"})rejected(value,false);
        for(String value:new String[]{"9.99","200.01","10.01","201"})rejected(value,true);
        equal(0.0,ElectricityModel.quantity("0.00","度"));equal(15.25,ElectricityModel.quantity("15.25","kWh"));
        for(String value:new String[]{"", "NaN", "Infinity", "-1", "invalid"}){count++;try{ElectricityModel.quantity(value,"度");throw new AssertionError("Invalid reading accepted");}catch(IllegalArgumentException expected){}}
        count++;try{ElectricityModel.quantity("10","元");throw new AssertionError("Money shown as energy");}catch(IllegalArgumentException expected){}
        System.out.println("ElectricityModel: "+count+" checks passed");
    }
}
