package cn.jhun.sanjiaohu;

public final class IdentityPolicyTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Case "+checks);}
    public static void main(String[] args){
        check(IdentityPolicy.auth(IdentityPolicy.LOGIN));check(IdentityPolicy.allowed(IdentityPolicy.REPAIR));check(!IdentityPolicy.auth(IdentityPolicy.REPAIR));check(IdentityPolicy.repair(IdentityPolicy.REPAIR));
        for(String value:new String[]{"https://authserver.jhun.edu.cn/authserver/login","https://ehall.jhun.edu.cn/new/index.html","http://ehall.jhun.edu.cn/login?ticket=ST-synthetic","https://hqfw.jhun.edu.cn/wsbx/html/yd/wsbx.html#/wybx"})check(IdentityPolicy.allowed(value));
        for(String value:new String[]{null,"","http://authserver.jhun.edu.cn:8080/authserver/login","javascript:alert(1)","file:///private","content://secrets","https://authserver.jhun.edu.cn.evil.invalid/authserver/login","https://evil.invalid/?https://authserver.jhun.edu.cn","https://u:p@authserver.jhun.edu.cn/authserver/login","https://authserver.jhun.edu.cn:8080/authserver/login","https://jwxt.jhun.edu.cn/cas/login.action","http://hqfw.jhun.edu.cn:81/wsbx","https://hqfw.jhun.edu.cn\\@evil.invalid/"})check(!IdentityPolicy.allowed(value));
        for(String service:new String[]{"https%3A%2F%2Fevil.invalid%2F","javascript%3Aalert%281%29","http%3A%2F%2Fehall.jhun.edu.cn%2Flogin%3Fservice%3Dhttps%253A%252F%252Fevil.invalid%252F","http%3A%2F%2Fuser%40hqfw.jhun.edu.cn%2F"})check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service="+service));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/resetPassword"));check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service="));
        check(IdentityPolicy.hall("http://ehall.jhun.edu.cn/new/index.html"));check(!IdentityPolicy.hall("https://ehall.jhun.edu.cn/login?ticket=ST-synthetic"));
        check(IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC.route"));
        check(IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC"));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;jsessionid=SYNTHETIC/elsewhere"));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login;other=1"));
        check(IdentityPolicy.LOGIN.equals(IdentityPolicy.REPAIR_ENTRY));
        check(IdentityPolicy.parse(IdentityPolicy.LOGIN).getFragment()==null);
        try{check(java.net.URLDecoder.decode(IdentityPolicy.parse(IdentityPolicy.LOGIN).getRawQuery().substring("service=".length()),"UTF-8").equals(IdentityPolicy.REPAIR_CALLBACK));}catch(Exception e){throw new AssertionError(e);}
        check(IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service=http://hqfw.jhun.edu.cn/wsbx/login/cas%23%2Fwybx"));
        check(!IdentityPolicy.repairLanding("http://hqfw.jhun.edu.cn/wsbx/login/cas?ticket=ST-synthetic#/wybx"));
        check(IdentityPolicy.repairLanding(IdentityPolicy.REPAIR));
        check(!IdentityPolicy.repairLanding("http://hqfw.jhun.edu.cn/wsbx/error"));
        check(IdentityPolicy.repairRoot("http://hqfw.jhun.edu.cn/wsbx/#/wybx"));
        check(!IdentityPolicy.repairRoot(IdentityPolicy.REPAIR_CALLBACK));
        check(!IdentityPolicy.repairRoot(IdentityPolicy.REPAIR));
        check(!IdentityPolicy.repairRoot("http://hqfw.jhun.edu.cn.evil.invalid/wsbx/"));
        check(IdentityPolicy.auth(IdentityPolicy.LOGIN.replace("https:","http:")));
        check(IdentityPolicy.allowed(IdentityPolicy.LOGIN.replace("https:","http:")));
        check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fevil.invalid"));
        System.out.println("Identity URL policy: "+checks+" checks passed");
    }
}
