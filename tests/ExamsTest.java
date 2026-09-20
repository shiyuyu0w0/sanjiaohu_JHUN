package cn.jhun.sanjiaohu;
import java.time.*;
import org.json.*;
public final class ExamsTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static Exams.Entry exam(String time)throws Exception{return new Exams.Entry(new JSONObject().put("name","测试考试").put("time",time));}
    static ZonedDateTime now(String s){return LocalDateTime.parse(s).atZone(Exams.ZONE);}
    public static void main(String[] args)throws Exception{
        Exams.Entry e=exam("2026-11-12(10周 星期四)13:40-15:40");
        check(e.badge(now("2026-09-20T12:00")).equals("还剩\n53 天"),"school date countdown");
        check(e.badge(now("2026-11-11T23:59")).equals("还剩\n1 天"),"calendar day, not 24h rounding");
        check(e.badge(now("2026-11-12T00:00")).equals("今天"),"today before start");
        check(e.badge(now("2026-11-12T13:40")).equals("考试中"),"start boundary");
        check(!e.finished(now("2026-11-12T15:39")),"ongoing not completed");
        check(e.finished(now("2026-11-12T15:40")),"end boundary");
        check(e.badge(now("2026-11-12T15:40")).equals("已考完"),"completed label");
        check(e.badge(now("2026-11-11T23:59").withZoneSameInstant(ZoneId.of("America/New_York"))).equals("还剩\n1 天"),"phone timezone does not change countdown");
        for(String value:new String[]{"","时间待定","2026-02-30 10:00-12:00","2026-13-01","2026-11-120"}){
            Exams.Entry unknown=exam(value);check(!unknown.finished(now("2027-01-01T00:00")),"unknown date cannot finish "+value);check(unknown.badge(now("2027-01-01T00:00")).equals("待安排"),"unknown badge");
        }
        for(String date:new String[]{"2026/11/12","2026.11.12","2026年11月12日","2026-11-12"}){
            Exams.Entry plain=exam(date);check(plain.badge(now("2026-11-12T23:59")).equals("今天"),"date-only remains today");check(plain.finished(now("2026-11-13T00:00")),"date-only next day");
        }
        Exams.Entry overnight=exam("2026-11-12 23:00-01:00");check(!overnight.finished(now("2026-11-13T00:30")),"overnight ongoing");check(overnight.finished(now("2026-11-13T01:00")),"overnight finished");
        Exams.Entry badTime=exam("2026-11-12 29:00-30:00");check(badTime.end==null,"invalid hour never parses");
        JSONObject data=new JSONObject().put("version",1).put("complete",true).put("term","2026-2027学年第一学期").put("entries",new JSONArray().put(new JSONObject().put("name","过去").put("time","2026-09-01")).put(new JSONObject().put("name","未来").put("time","2026-11-01")).put(new JSONObject().put("name","待定")));
        Exams exams=new Exams(data);check(exams.ordered(now("2026-09-20T12:00")).get(0).name.equals("未来"),"upcoming sorted first");check(exams.ordered(now("2026-09-20T12:00")).get(2).name.equals("过去"),"finished last");
        data.put("complete",false);boolean rejected=false;try{new Exams(data);}catch(Exception expected){rejected=true;}check(rejected,"incomplete cache rejected");
        System.out.println(checks+" exam date and data checks passed");
    }
}
