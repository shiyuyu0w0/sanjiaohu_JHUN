package cn.jhun.sanjiaohu;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import java.io.*;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Only the school's host-only JSESSIONID is saved. Disk/Keystore operations run off the UI thread. */
final class TeachingSessionStore {
    private static final String ALIAS="cn.jhun.sanjiaohu.teaching-session.v1";
    static final String ORIGIN="https://jwxt.jhun.edu.cn/";
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"teaching-session.enc"));}
    static boolean exists(Context c){return file(c).getBaseFile().exists();}
    static String capture(){return sessionId(CookieManager.getInstance().getCookie(ORIGIN));}
    static String sessionId(String cookies){
        if(cookies==null)return null;
        String found=null;
        for(String item:cookies.split(";")){int eq=item.indexOf('=');if(eq>0&&item.substring(0,eq).trim().equals("JSESSIONID")){
            String value=item.substring(eq+1).trim();if(!valid(value)||(found!=null&&!found.equals(value)))return null;found=value;
        }}return found;
    }
    static boolean valid(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{16,256}(?:\\.[A-Za-z0-9_-]{1,64})?");}
    static void restore(String id,ValueCallback<Boolean> done){
        if(!valid(id)){done.onReceiveValue(false);return;}
        // No Domain or Max-Age: retain the school's host scope and session lifetime.
        CookieManager.getInstance().setCookie(ORIGIN,"JSESSIONID="+id+"; Path=/; Secure; HttpOnly",done);
    }
    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        if(!create)throw new IOException("会话密钥不可用");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    static synchronized void save(Context c,String id)throws Exception{
        if(!valid(id))throw new IOException("学校会话不可用");
        byte[] plain=id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
            byte[] encrypted=cipher.doFinal(plain),iv=cipher.getIV();AtomicFile target=file(c);FileOutputStream output=null;
            try{output=target.startWrite();output.write(1);output.write(iv.length);output.write(iv);output.write(encrypted);target.finishWrite(output);}
            catch(Exception e){if(output!=null)target.failWrite(output);throw e;}
        }finally{Arrays.fill(plain,(byte)0);}
    }
    static synchronized String load(Context c)throws Exception{
        byte[] encoded=file(c).readFully();if(encoded.length<46||encoded.length>512||encoded[0]!=1||encoded[1]!=12)throw new IOException("会话格式错误");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,encoded,2,12));
        byte[] plain=cipher.doFinal(encoded,14,encoded.length-14);
        try{String id=new String(plain,java.nio.charset.StandardCharsets.UTF_8);if(!valid(id))throw new IOException("会话格式错误");return id;}
        finally{Arrays.fill(plain,(byte)0);}
    }
    static synchronized void clear(Context c){file(c).delete();}
}
