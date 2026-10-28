package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;

/** Small private copies: every widget owns its image, and edits stay in a draft until Save. */
final class WidgetPhotoStore {
    static File photo(Context c,int id){if(id<=0)throw new IllegalArgumentException();return new File(c.getFilesDir(),"widget-photo-"+id+".png");}
    static File draft(Context c,String token){if(token==null||!token.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException();return new File(c.getCacheDir(),"widget-photo-draft-"+token+".png");}
    static Bitmap load(File file){try(InputStream in=new AtomicFile(file).openRead()){return BitmapFactory.decodeStream(in);}catch(IOException e){return null;}}
    static void save(File file,Bitmap bitmap)throws IOException{
        AtomicFile atomic=new AtomicFile(file);FileOutputStream out=null;
        try{out=atomic.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片保存失败");atomic.finishWrite(out);}
        catch(IOException|RuntimeException e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    static void commit(Context c,int id,String token,boolean reset)throws IOException{
        if(reset){new AtomicFile(photo(c,id)).delete();return;}
        Bitmap bitmap=load(draft(c,token));if(bitmap==null)throw new IOException("图片草稿已失效，请重新选择");
        try{save(photo(c,id),bitmap);}finally{bitmap.recycle();}
    }
    static void delete(Context c,int id){new AtomicFile(photo(c,id)).delete();}
}
