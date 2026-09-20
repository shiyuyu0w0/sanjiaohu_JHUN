package cn.jhun.sanjiaohu;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Each semester and data type has its own atomic cache. */
final class AcademicStore {
    private final File directory;
    AcademicStore(Context context){directory=context.getFilesDir();}
    private AtomicFile file(String kind,String term){if(!kind.equals("schedule")&&!kind.equals("grades")&&!kind.equals("summary")&&!kind.equals("exams"))throw new IllegalArgumentException();return new AtomicFile(new File(directory,kind+"-"+(kind.equals("summary")?"all":Term.key(term))+".json"));}
    JSONObject load(String kind,String term)throws Exception{
        JSONObject data=new JSONObject(new String(file(kind,term).readFully(),StandardCharsets.UTF_8));
        boolean scope=kind.equals("summary")?data.optString("scope").equals("all"):Term.same(term,data.getString("term"));
        if(!scope||!CachePolicy.usable(data.optLong("savedAt"),data.optString("source")))throw new IOException("缓存校验失败");
        return data;
    }
    void save(String kind,JSONObject data)throws Exception{
        AtomicFile target=file(kind,data.getString("term"));FileOutputStream stream=null;
        try{stream=target.startWrite();stream.write(data.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(stream);}catch(Exception error){if(stream!=null)target.failWrite(stream);throw error;}
    }
}
