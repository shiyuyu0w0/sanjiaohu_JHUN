package cn.jhun.sanjiaohu;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifiers always come from the school's room directory, never from guessed ranges. */
final class ElectricityModel {
    static final int UNKNOWN=0, AC=1, LIGHT=2;
    // The school lists some dorms as two separate buildings ("X照明" and "X空调")
    // while the app shows one option with both cards. Pair them only by their
    // exact shared base name, and only when both halves are present.
    static final String AC_SUFFIX="空调", LIGHT_SUFFIX="照明";
    /** Base name of a building that names its meter kind, or null when it does not. */
    static String meterBase(String building){
        if(building==null)return null;
        String name=building.trim();
        String base=null;
        if(name.endsWith(AC_SUFFIX))base=name.substring(0,name.length()-AC_SUFFIX.length());
        else if(name.endsWith(LIGHT_SUFFIX))base=name.substring(0,name.length()-LIGHT_SUFFIX.length());
        // A bare "空调"/"照明" would leave an empty base; never pair on that.
        return base!=null&&!base.isEmpty()?base:null;
    }
    /** True when this building names the AC half of a pair. */
    static boolean isAcHalf(String building){
        return building!=null&&building.trim().endsWith(AC_SUFFIX)&&meterBase(building)!=null;
    }
    /** True when this building names the LIGHT half of a pair. */
    static boolean isLightHalf(String building){
        return building!=null&&building.trim().endsWith(LIGHT_SUFFIX)&&meterBase(building)!=null;
    }
    static String savedSelection(Object value){return value instanceof String&&((String)value).length()<=160?((String)value).trim():"";}
    // TreeMap also invokes its comparator for lookups of absent/empty saved keys.
    // Keep a total ordering; only parse the bounded numeric keys we recognize.
    static int compareFloors(String left,String right){
        boolean a=left.matches("[0-9]{1,2}"),b=right.matches("[0-9]{1,2}");
        if(a&&b){int order=Integer.compare(Integer.parseInt(left),Integer.parseInt(right));return order!=0?order:left.compareTo(right);}
        if(a!=b)return a?-1:1;
        return left.compareTo(right);
    }
    static String floor(String name){
        Matcher m=Pattern.compile("^(\\d{1,2})\\s*层.*$").matcher(name.trim());
        return m.matches()?m.group(1):null;
    }
    static int kind(String area,String building,String level){
        String specific=building+" "+level;
        boolean ac=specific.contains("空调"),light=specific.contains("照明")||specific.contains("灯光");
        if(ac&&light)return UNKNOWN;
        if(ac)return AC;if(light)return LIGHT;
        // The shared area includes both meters; an unlabelled floor is ambiguous.
        if(area.contains("空调")&&(area.contains("照明")||area.contains("灯光")))return UNKNOWN;
        if(area.contains("照明")||area.contains("灯光"))return LIGHT;
        return area.contains("空调")?AC:UNKNOWN;
    }
    static String room(String name){
        Matcher m=Pattern.compile("^\\d+-[A-Za-z0-9]+-(\\d{3,4}[A-Za-z]?)$").matcher(name.trim());
        return m.matches()?m.group(1):name.trim();
    }
    static double quantity(String raw,String unit){
        if(!unit.equals("度")&&!unit.equalsIgnoreCase("kWh"))throw new IllegalArgumentException("电表返回的单位无法识别");
        double value=Double.parseDouble(raw);
        if(Double.isNaN(value)||Double.isInfinite(value)||value==-1)throw new IllegalArgumentException("学校暂未提供有效读数");
        return value;
    }
    static String amount(String input,boolean integerOnly){
        String s=input.trim();
        if(!s.matches("[0-9]{1,5}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("请输入金额，最多保留两位小数");
        BigDecimal value=new BigDecimal(s);
        if(value.compareTo(new BigDecimal("0.01"))<0||value.compareTo(new BigDecimal("99999"))>0)throw new IllegalArgumentException("金额应为 0.01～99999 元");
        if(integerOnly&&(value.stripTrailingZeros().scale()>0||value.compareTo(BigDecimal.TEN)<0||value.compareTo(new BigDecimal("200"))>0))throw new IllegalArgumentException("学校要求金额为 10～200 元的整数");
        return value.setScale(2).toPlainString();
    }
}
