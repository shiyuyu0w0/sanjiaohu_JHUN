package cn.jhun.sanjiaohu;

import java.util.*;
import java.util.regex.*;

/** Pure Java parser; no dependency on the network or Android. */
public final class Course {
    public String name, teacher, room, weekText, raw;
    public String localId, term;
    public int day, start, end;
    public Set<Integer> weeks = new TreeSet<>();
    private static final Pattern LINE = Pattern.compile("^(.*?)\\s+([^\\s].*?)[(（]([0-9][0-9,，、\\-－~～单双周()（）\\s]*|-[0-9][0-9,\\-]*)\\s+([^()（）]+)[)）]$");

    public static Course parse(String raw, int day, int period, int maxWeek) {
        Matcher m = LINE.matcher(raw.trim());
        // Start at the final parenthesis containing a week expression, so teacher group parentheses survive.
        if (!m.matches()) throw new IllegalArgumentException("无法识别课程：" + raw);
        Course c = new Course(); c.raw=raw; c.name=m.group(1).trim(); c.teacher=m.group(2).trim(); c.weekText=m.group(3).trim(); c.room=m.group(4).trim();
        // A course title can contain spaces (大学物理实验Ⅰ ②); teacher starts at the last whitespace before a teacher group/name.
        String prefix = c.name + " " + c.teacher;
        int group = prefix.indexOf("物理组");
        int split = group > 0 ? group-1 : prefix.lastIndexOf(' ');
        if(split>0) { c.name=prefix.substring(0,split).trim(); c.teacher=prefix.substring(split+1).trim(); }
        c.day=day; c.start=period; c.end=period; c.weeks=parseWeeks(c.weekText,maxWeek);
        if(c.name.isEmpty() || c.weeks.isEmpty()) throw new IllegalArgumentException("课程字段不完整");
        return c;
    }
    public static Set<Integer> parseWeeks(String input, int max) {
        Set<Integer> result = new TreeSet<>();
        String s=input.replace('，', ',').replace('、', ',').replace('－','-').replace('～','-').replace('~','-').replace("周", "").replaceAll("\\s", "");
        boolean globalOdd=s.endsWith("(单)") || s.endsWith("（单）");
        boolean globalEven=s.endsWith("(双)") || s.endsWith("（双）");
        if(globalOdd || globalEven) s=s.substring(0,s.length()-3);
        for(String part:s.split(",",-1)) {
            boolean odd=globalOdd || part.contains("单"), even=globalEven || part.contains("双");
            part=part.replaceAll("[单双()（）]", "");
            if(!part.matches("\\d+|\\d*-\\d*")) throw new IllegalArgumentException("无法识别周次");
            int a,b;
            if(part.contains("-")) { String[] ab=part.split("-",-1); a=ab[0].isEmpty()?1:Integer.parseInt(ab[0]); b=ab[1].isEmpty()?max:Integer.parseInt(ab[1]); }
            else a=b=Integer.parseInt(part);
            if(a<1 || b>max || a>b || (odd&&even)) throw new IllegalArgumentException("周次超出范围");
            for(int w=a;w<=b;w++) if((!odd || w%2==1)&&(!even || w%2==0)) result.add(w);
        }
        return result;
    }
    public String key() {return day+"|"+name+"|"+teacher+"|"+room+"|"+weeks;}
    public static List<Course> merge(List<Course> input) {
        List<Course> sorted=new ArrayList<>(input);
        sorted.sort(Comparator.comparing(Course::key).thenComparingInt(c->c.start));
        List<Course> out=new ArrayList<>();
        for(Course c:sorted) {
            Course prev=out.isEmpty()?null:out.get(out.size()-1);
            if(prev!=null && prev.key().equals(c.key()) && prev.end+1==c.start && c.start!=5 && c.start!=9) prev.end=c.end;
            else out.add(c);
        }
        out.sort(Comparator.comparingInt((Course c)->c.day).thenComparingInt(c->c.start));
        return out;
    }
}
