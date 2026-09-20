package cn.jhun.sanjiaohu;
public final class MapLocationPolicyTest {
    public static void main(String[] args){int n=0;String page="https://gis.jhun.edu.cn/m/";
        for(String origin:new String[]{"https://gis.jhun.edu.cn","https://gis.jhun.edu.cn:443/"}){if(!MapLocationPolicy.allow(origin,page,true,false)||!MapLocationPolicy.allow(origin,page,false,true)||MapLocationPolicy.allow(origin,page,false,false))throw new AssertionError(origin);n+=3;}
        for(String bad:new String[]{null,"","http://gis.jhun.edu.cn","https://gis.jhun.edu.cn:444","https://gis.jhun.edu.cn.evil.test","https://gis.jhun.edu.cn@evil.test","https://user@gis.jhun.edu.cn","https://jwxt.jhun.edu.cn","file:///map","https://gis.jhun.edu.cn\\@evil.test"}){if(MapLocationPolicy.allow(bad,page,true,true)||MapLocationPolicy.allow(page,bad,true,true))throw new AssertionError(bad);n+=2;}
        System.out.println(n+" map location policy checks passed");
    }
}
