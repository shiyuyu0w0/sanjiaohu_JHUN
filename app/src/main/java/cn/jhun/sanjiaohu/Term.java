package cn.jhun.sanjiaohu;

import java.util.regex.*;

/** A stable file key, independent of spacing in portal labels. */
final class Term {
    static String key(String label) {
        Matcher m=Pattern.compile("(20\\d{2})\\s*[-－—]\\s*(20\\d{2})\\s*学年?\\s*第?([一二12])学期").matcher(label);
        if(!m.find() || Integer.parseInt(m.group(2))!=Integer.parseInt(m.group(1))+1) throw new IllegalArgumentException("学期无法识别");
        return m.group(1)+"-"+(m.group(3).equals("一")||m.group(3).equals("1")?"1":"2");
    }
    static String label(String input) {String[] bits=key(input).split("-");int year=Integer.parseInt(bits[0]);return year+"-"+(year+1)+"学年第"+(bits[1].equals("1")?"一":"二")+"学期";}
    static boolean same(String a,String b){try{return key(a).equals(key(b));}catch(Exception e){return false;}}
}
