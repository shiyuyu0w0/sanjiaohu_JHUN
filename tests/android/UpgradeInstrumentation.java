package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import org.json.JSONObject;

/** Seed on the published APK, then verify/restore after adb install -r of the final APK. */
public final class UpgradeInstrumentation extends Instrumentation {
    private static final String ID="release-upgrade-1.2.0",SENTINEL="releaseUpgradeSentinel";
    private static final String[] FILES={"custom-courses.json","course-notes.properties","wallpaper.png"};
    private String phase;
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            if("seed".equals(phase))seed();
            else if("verify".equals(phase))verify();
            else if("restore".equals(phase))restore();
            else throw new IllegalArgumentException("Expected phase=seed, verify or restore");
            result.putString("stream","PASS: upgrade "+phase+" (custom course, note, wallpaper and preferences)\n");finish(-1,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(error));finish(0,result);}
    }
    private Context context(){return getTargetContext();}
    private File file(String name){return new File(context().getFilesDir(),name);}
    private File backup(){return file(ID+"-backup.json");}
    private SharedPreferences settings(){return context().getSharedPreferences("settings",Context.MODE_PRIVATE);}
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void version(String name,int code)throws Exception{
        android.content.pm.PackageInfo info=context().getPackageManager().getPackageInfo(context().getPackageName(),0);
        require(name.equals(info.versionName)&&code==info.versionCode,"unexpected installed version: "+info.versionName);
    }
    private void write(File file,byte[] data)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}}
    private void save(JSONObject state)throws Exception{write(backup(),state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private JSONObject read()throws Exception{return new JSONObject(new String(Files.readAllBytes(backup().toPath()),java.nio.charset.StandardCharsets.UTF_8));}
    private String hash(File file)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));StringBuilder out=new StringBuilder();
        for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private void seed()throws Exception{
        version("1.1.9",52);require(!backup().exists()&&!settings().contains(SENTINEL),"restore previous upgrade fixture first");
        JSONObject state=new JSONObject(),original=new JSONObject();
        for(String name:FILES){File f=file(name);original.put(name,f.exists()?Base64.encodeToString(Files.readAllBytes(f.toPath()),Base64.NO_WRAP):JSONObject.NULL);}
        state.put("original",original);save(state);
        try{
            CustomCourseStore store=new CustomCourseStore(context());List<Course> courses=store.load();
            Course course=CustomCourses.create(ID,"*","升级保留测试","测试教师","测试教室",1,1,2,"1-16");courses.add(course);store.save(courses);
            new CourseNoteStore(context()).save(course,"*","升级后备注仍保留");
            if(!WallpaperStore.file(context()).exists()){
                Bitmap image=Bitmap.createBitmap(20,32,Bitmap.Config.ARGB_8888);image.eraseColor(0xff159aca);
                try{WallpaperStore.save(context(),image);}finally{image.recycle();}
            }
            require(settings().edit().putString(SENTINEL,ID).commit(),"seed preferences");
            JSONObject hashes=new JSONObject();for(String name:FILES)hashes.put(name,hash(file(name)));state.put("hashes",hashes);save(state);
        }catch(Exception|AssertionError error){restore();throw error;}
    }
    private void verify()throws Exception{
        try{
            version("1.2.0",53);JSONObject hashes=read().getJSONObject("hashes");
            for(String name:FILES)require(hashes.getString(name).equals(hash(file(name))),"upgrade changed "+name);
            require(ID.equals(settings().getString(SENTINEL,"")),"preferences lost");
            Course found=null;for(Course course:new CustomCourseStore(context()).load())if(ID.equals(course.localId))found=course;
            require(found!=null,"custom course lost");
            require("升级后备注仍保留".equals(new CourseNoteStore(context()).get(found,"*")),"course note lost");
            Bitmap image=WallpaperStore.load(context());require(image!=null,"wallpaper cannot decode");image.recycle();
        }finally{restore();}
    }
    private void restore()throws Exception{
        JSONObject original=read().getJSONObject("original");
        for(String name:FILES){if(original.isNull(name)){require(!file(name).exists()||file(name).delete(),"remove fixture "+name);}else write(file(name),Base64.decode(original.getString(name),Base64.NO_WRAP));}
        require(settings().edit().remove(SENTINEL).commit(),"restore preferences");require(backup().delete(),"remove fixture backup");
    }
}
