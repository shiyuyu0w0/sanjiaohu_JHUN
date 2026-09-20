package cn.jhun.sanjiaohu;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** School-local dates; missing or ambiguous times never become a completed exam. */
final class Exams {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    final String term;final long savedAt;final List<Entry> entries=new ArrayList<>();
    Exams(JSONObject data)throws Exception{
        if(!data.optBoolean("complete")||data.optInt("version")!=1)throw new IllegalArgumentException("考试数据不完整");
        term=Term.label(data.getString("term"));savedAt=data.optLong("savedAt");
        JSONArray rows=data.getJSONArray("entries");if(rows.length()>1000)throw new IllegalArgumentException();
        for(int i=0;i<rows.length();i++)entries.add(new Entry(rows.getJSONObject(i)));
    }
    List<Entry> ordered(ZonedDateTime now){
        List<Entry> result=new ArrayList<>(entries);result.sort((a,b)->{
            int status=Boolean.compare(a.finished(now),b.finished(now));if(status!=0)return status;
            if(a.date==null)return b.date==null?a.name.compareTo(b.name):1;if(b.date==null)return -1;
            int d=a.date.compareTo(b.date);if(a.finished(now))d=-d;
            return d!=0?d:a.rawTime.compareTo(b.rawTime);
        });return result;
    }
    static final class Entry {
        final String name,rawTime,room,seat,note;final LocalDate date;final LocalTime start,end;
        Entry(JSONObject j){
            name=j.optString("name").trim();rawTime=j.optString("time").trim();room=j.optString("room").trim();seat=j.optString("seat").trim();note=j.optString("note").trim();
            if(name.isEmpty()||name.length()>500||rawTime.length()>300)throw new IllegalArgumentException("考试字段异常");
            LocalDate parsed=null;LocalTime first=null,last=null;
            Matcher day=Pattern.compile("(?<!\\d)(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})(?:日)?(?!\\d)").matcher(rawTime);
            if(day.find())try{parsed=LocalDate.of(Integer.parseInt(day.group(1)),Integer.parseInt(day.group(2)),Integer.parseInt(day.group(3)));}catch(DateTimeException ignored){}
            Matcher times=Pattern.compile("(?<!\\d)([0-2]?\\d)[:：]([0-5]\\d)(?!\\d)").matcher(rawTime);
            try{if(times.find())first=LocalTime.of(Integer.parseInt(times.group(1)),Integer.parseInt(times.group(2)));if(times.find())last=LocalTime.of(Integer.parseInt(times.group(1)),Integer.parseInt(times.group(2)));}catch(DateTimeException ignored){first=null;last=null;}
            date=parsed;start=first;end=last;
        }
        boolean finished(ZonedDateTime now){
            if(date==null)return false;ZonedDateTime school=now.withZoneSameInstant(ZONE);
            if(end!=null){LocalDate endDate=date;if(start!=null&&end.isBefore(start))endDate=endDate.plusDays(1);return !school.isBefore(ZonedDateTime.of(endDate,end,ZONE));}
            return school.toLocalDate().isAfter(date);
        }
        String badge(ZonedDateTime now){
            if(finished(now))return "已考完";if(date==null)return "待安排";
            ZonedDateTime school=now.withZoneSameInstant(ZONE);long days=ChronoUnit.DAYS.between(school.toLocalDate(),date);
            if(days>0)return "还剩\n"+days+" 天";
            if(start!=null&&end!=null&&!school.isBefore(ZonedDateTime.of(date,start,ZONE)))return "考试中";
            return "今天";
        }
    }
}
