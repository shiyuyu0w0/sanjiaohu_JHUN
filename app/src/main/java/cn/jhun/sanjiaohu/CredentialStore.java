package cn.jhun.sanjiaohu;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Call on a worker thread. Only ciphertext is written; the key stays in Android Keystore. */
public final class CredentialStore {
    private static final String ALIAS="sanjiaohubian.login.v1";
    public static final class Credentials {
        public final String account,password;
        Credentials(String account,String password){this.account=account;this.password=password;}
    }
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"login.enc"));}
    public static boolean exists(Context c){return file(c).getBaseFile().exists();}
    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        if(!create)throw new IOException("凭证密钥不可用");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    public static synchronized void save(Context c,String account,String password)throws Exception{
        if(account.isEmpty()||password.isEmpty())throw new IllegalArgumentException("凭证不能为空");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);data.writeUTF(account);data.writeUTF(password);data.close();
        byte[] plain=bytes.toByteArray();
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
            byte[] encrypted=cipher.doFinal(plain),iv=cipher.getIV();
            AtomicFile target=file(c);FileOutputStream output=null;
            try{output=target.startWrite();output.write(1);output.write(iv.length);output.write(iv);output.write(encrypted);target.finishWrite(output);}
            catch(Exception e){if(output!=null)target.failWrite(output);throw e;}
        }finally{Arrays.fill(plain,(byte)0);}
    }
    public static synchronized Credentials load(Context c)throws Exception{
        byte[] encoded=file(c).readFully();if(encoded.length<31||encoded[0]!=1||encoded[1]!=12)throw new IOException("凭证格式错误");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,encoded,2,12));
        byte[] plain=cipher.doFinal(encoded,14,encoded.length-14);
        try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(plain))){return new Credentials(input.readUTF(),input.readUTF());}
        finally{Arrays.fill(plain,(byte)0);}
    }
    public static synchronized void clear(Context c){file(c).delete();}
}
