package cn.jhun.sanjiaohu;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Fingerprint ignores row ordering, split rows and refresh timestamps. */
final class ScheduleRevision {
    static String fingerprint(JSONObject data)throws Exception{
        Schedule schedule=new Schedule(data);TreeSet<String> entries=new TreeSet<>();
        for(Course c:schedule.courses)for(int week:c.weeks)for(int period=c.start;period<=c.end;period++)entries.add(new JSONArray().put(c.name.trim()).put(c.teacher.trim()).put(c.room.trim()).put(c.day).put(week).put(period).toString());
        JSONArray extra=data.optJSONArray("unscheduled");if(extra!=null)for(int i=0;i<extra.length();i++){JSONObject item=extra.getJSONObject(i);entries.add(new JSONArray().put("unscheduled").put(item.optString("name")).put(item.optString("teacher")).put(item.optString("note")).toString());}
        String canonical=Term.key(schedule.term)+"\n"+String.join("\n",entries);byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
    static boolean needsReview(JSONObject active,JSONObject fresh)throws Exception{
        new Schedule(fresh);JSONArray issues=fresh.optJSONArray("issues");
        if(fresh.optBoolean("requiresReview")||(issues!=null&&issues.length()>0))return true;
        return active!=null&&!fingerprint(active).equals(fingerprint(fresh));
    }
    static int courseCount(JSONObject data)throws Exception{Set<String> names=new HashSet<>();for(Course c:new Schedule(data).courses)names.add(c.name);return names.size();}
}
