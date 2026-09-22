package cn.jhun.sanjiaohu;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

final class UpdateVerifier {
    static final String CERT="2d5c4c7ab3f5e829dfb2273f333a2af987e512dc270fbe03347638329cdef22a";
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}
    static void verify(Context c,File file,UpdateManifest m)throws Exception{
        if(file.length()!=m.size||!hash(file).equals(m.sha256))throw new IOException("安装包校验失败");
        PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),local=pm.getPackageInfo(c.getPackageName(),flags);
        if(apk==null||!UpdatePolicy.PACKAGE.equals(apk.packageName)||!m.name.equals(apk.versionName))throw new IOException("安装包身份不匹配");
        long code=Build.VERSION.SDK_INT>=28?apk.getLongVersionCode():apk.versionCode;
        if(code!=m.code||code<=UpdateStore.installed(c)||apk.applicationInfo==null||apk.applicationInfo.minSdkVersion!=m.minSdk||m.minSdk>Build.VERSION.SDK_INT)throw new IOException("安装包版本不兼容");
        if(!signers(apk).equals(Collections.singleton(CERT))||!signers(local).equals(Collections.singleton(CERT)))throw new IOException("安装包签名不匹配");
    }
    static Set<String> signers(PackageInfo info)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        Set<String> out=new HashSet<>();if(signatures!=null)for(Signature s:signatures)out.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));return out;
    }
    static File snapshot(Context c,long id,InputStream input,UpdateManifest m)throws Exception{
        File dir=new File(c.getFilesDir(),"updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建更新目录");
        File file=new File(dir,"download-"+id+".apk");boolean ok=false;
        try{try(InputStream in=input;FileOutputStream out=new FileOutputStream(file)){byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>m.size)throw new IOException("安装包大小异常");out.write(b,0,n);}out.getFD().sync();}verify(c,file,m);ok=true;return file;}finally{if(!ok)file.delete();}
    }
    static File file(Context c,long id){return new File(new File(c.getFilesDir(),"updates"),"download-"+id+".apk");}
}
