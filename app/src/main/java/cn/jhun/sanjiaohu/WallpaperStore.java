package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;

public final class WallpaperStore {
    public static File file(Context c){return new File(c.getFilesDir(),"wallpaper.png");}
    public static Bitmap load(Context c){try{AtomicFile f=new AtomicFile(file(c));try(InputStream in=f.openRead()){return BitmapFactory.decodeStream(in);}}catch(Exception e){return null;}}
    public static int save(Context c,Uri uri)throws IOException{
        Bitmap bitmap=decode(c,uri);try{return save(c,bitmap);}finally{bitmap.recycle();}
    }
    /** Decode for a local preview; the existing wallpaper is not changed yet. */
    public static Bitmap decode(Context c,Uri uri)throws IOException{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
        if(o.outWidth<1||o.outHeight<1)throw new IOException("无法读取这张图片");
        o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1600||o.outHeight/o.inSampleSize>1600)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,o);}
        if(decoded==null)throw new IOException("图片格式不受支持");
        int orientation=1;try(InputStream in=c.getContentResolver().openInputStream(uri)){orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
        Matrix transform=new Matrix();
        switch(orientation){case 2:transform.setScale(-1,1);break;case 3:transform.setRotate(180);break;case 4:transform.setScale(1,-1);break;case 5:transform.setRotate(90);transform.postScale(-1,1);break;case 6:transform.setRotate(90);break;case 7:transform.setRotate(-90);transform.postScale(-1,1);break;case 8:transform.setRotate(-90);break;}
        if(!transform.isIdentity()){Bitmap rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),transform,true);if(rotated!=decoded)decoded.recycle();decoded=rotated;}
        Bitmap opaque=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(opaque);canvas.drawColor(Color.WHITE);canvas.drawBitmap(decoded,0,0,null);decoded.recycle();
        return opaque;
    }
    /** Atomically replace only after the user confirms the crop. Caller owns the bitmap. */
    public static int save(Context c,Bitmap bitmap)throws IOException{
        AtomicFile f=new AtomicFile(file(c));FileOutputStream out=null;
        try{int dominant=dominant(bitmap);out=f.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("图片保存失败");f.finishWrite(out);return dominant;}catch(IOException|RuntimeException|OutOfMemoryError e){if(out!=null)f.failWrite(out);throw e;}
    }
    public static int dominant(Bitmap b){Bitmap small=Bitmap.createScaledBitmap(b,64,64,true);int[] pixels=new int[4096];small.getPixels(pixels,0,64,0,0,64,64);int color=ThemePalette.dominant(pixels);if(small!=b)small.recycle();return color;}
    public static void clear(Context c){new AtomicFile(file(c)).delete();}
}
