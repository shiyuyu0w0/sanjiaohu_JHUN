package cn.jhun.sanjiaohu;

import java.util.*;
import org.json.*;

final class Grades {
    final String term;final long savedAt;final List<Entry> entries=new ArrayList<>();
    final List<Metric> groups=new ArrayList<>();Metric total;
    final boolean effective;
    static final class Entry {
        final String name,credits,hours,category,nature,assessment,method,score,note,point;
        Entry(JSONObject item)throws Exception{
            name=item.getString("name");credits=item.getString("credits");hours=item.getString("hours");category=item.getString("category");nature=item.getString("nature");assessment=item.getString("assessment");method=item.getString("method");score=item.getString("score");note=item.getString("note");
            point=item.optString("point","");
            if(name.trim().isEmpty())throw new IllegalArgumentException("成绩课程缺失");
        }
    }
    static final class Metric {
        final String name,earned,average,gpa,weighted;
        Metric(JSONObject value)throws Exception{name=value.getString("name");earned=value.getString("earned");average=value.getString("average");gpa=value.getString("gpa");weighted=value.getString("weighted");}
    }
    Grades(JSONObject data)throws Exception{
        int version=data.getInt("version");if((version!=1&&version!=2) || !data.getBoolean("complete"))throw new IllegalArgumentException("成绩不完整");
        effective=data.optString("mode").equals("effective");
        term=data.optString("scope").equals("all")?"入学以来":Term.label(data.getString("term"));savedAt=data.optLong("savedAt");JSONArray rows=data.getJSONArray("entries");
        if(rows.length()>2000)throw new IllegalArgumentException("成绩过多");
        for(int i=0;i<rows.length();i++)entries.add(new Entry(rows.getJSONObject(i)));
        JSONObject summary=data.optJSONObject("summary");
        if(summary!=null){JSONArray list=summary.getJSONArray("groups");for(int i=0;i<list.length();i++)groups.add(new Metric(list.getJSONObject(i)));if(summary.optJSONObject("total")!=null)total=new Metric(summary.getJSONObject("total"));}
    }
}
