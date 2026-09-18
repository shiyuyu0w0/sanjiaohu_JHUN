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
        check(IdentityPolicy.auth(IdentityPolicy.ELECTRICITY_LOGIN));
        check(IdentityPolicy.auth(IdentityPolicy.ELECTRICITY_LOGIN.replace("http:","https:")));
        check(IdentityPolicy.parse(IdentityPolicy.ELECTRICITY_LOGIN).getFragment()==null);
        try{check(java.net.URLDecoder.decode(IdentityPolicy.parse(IdentityPolicy.ELECTRICITY_LOGIN).getRawQuery().substring("service=".length()),"UTF-8").equals(IdentityPolicy.ELECTRICITY_SERVICE));}catch(Exception e){throw new AssertionError(e);}
        check(IdentityPolicy.electricity(IdentityPolicy.ELECTRICITY_SERVICE));
        check(!IdentityPolicy.auth(IdentityPolicy.ELECTRICITY_SERVICE));
        check(!IdentityPolicy.repairLanding(IdentityPolicy.ELECTRICITY_SERVICE));
        for(String value:new String[]{"http://hub.17wanxiao.com/bsacs/light.action","https://hub.17wanxiao.com.evil.invalid/","https://evil@hub.17wanxiao.com/","https://hub.17wanxiao.com:8443/","https://other.17wanxiao.com/"}){
            check(!IdentityPolicy.allowed(value));check(!IdentityPolicy.electricity(value));
            try{check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode(value,"UTF-8")));}catch(Exception e){throw new AssertionError(e);}
        }
        check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service=https%3A%2F%2Fhub.17wanxiao.com%2F%3Fservice%3Dhttps%253A%252F%252Fevil.invalid"));
        String cloud="https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do";
        check(IdentityPolicy.allowed(cloud));check(IdentityPolicy.electricity(cloud));check(!IdentityPolicy.auth(cloud));
        check(IdentityPolicy.allowed(cloud+"?ticket=ST-synthetic#test"));
        for(String invalid:new String[]{cloud.replace("https:","http:"),cloud.replace(":18443",":443"),cloud.replace(":18443",""),cloud.replace(":18443",":18444"),cloud.replace("h5cloud.17wanxiao.com","h5cloud.17wanxiao.com.evil.invalid"),cloud.replace("h5cloud.17wanxiao.com","user@h5cloud.17wanxiao.com"),cloud.replace("h5cloud.17wanxiao.com","other.17wanxiao.com"),cloud.replace("h5cloud.17wanxiao.com","hub.17wanxiao.com"),cloud.replace("h5cloud.17wanxiao.com","authserver.jhun.edu.cn")}){
            check(!IdentityPolicy.allowed(invalid));check(!IdentityPolicy.electricity(invalid));
        }
        // The known CAS service remains hub; adding a navigation origin must not expand credential injection.
        try{check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode(cloud,"UTF-8")));}catch(Exception e){throw new AssertionError(e);}
        // A browser-valid destination with raw JSON query/fragment was previously rejected
        // before its allowlisted origin could be checked. Never rewrite its parameters.
        for(String suffix:new String[]{"?data={\"test\":1}","?data=one|two","#state={\"test\":1}"}){
            check(IdentityPolicy.parse(cloud+suffix).getHost()==null);
            check(IdentityPolicy.allowed(cloud+suffix));check(IdentityPolicy.electricity(cloud+suffix));
            check(!IdentityPolicy.auth(cloud+suffix));
            check(!IdentityPolicy.allowed(cloud.replace("h5cloud.17wanxiao.com","evil.invalid")+suffix));
            check(!IdentityPolicy.allowed(cloud.replace("https:","http:")+suffix));
            check(!IdentityPolicy.allowed(cloud.replace(":18443",":18444")+suffix));
            check(!IdentityPolicy.allowed(cloud.replace("h5cloud.17wanxiao.com","user@h5cloud.17wanxiao.com")+suffix));
        }
        check(!IdentityPolicy.auth(IdentityPolicy.ELECTRICITY_LOGIN+"&data={\"test\":1}"));
        try{check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode("https://hub.17wanxiao.com/?data={test}&service=https://evil.invalid/","UTF-8")));}catch(Exception e){throw new AssertionError(e);}
        String relay="https://open.17wanxiao.com/";
        for(String value:new String[]{relay,relay.replace(".com/",".com:443/"),relay+"?state={test}"}){
            check(IdentityPolicy.allowed(value));check(IdentityPolicy.electricityRelay(value));check(IdentityPolicy.electricity(value));check(!IdentityPolicy.auth(value));
        }
        for(String value:new String[]{relay.replace("https:","http:"),relay.replace(".com/",".com:18443/"),relay.replace(".com/",".com:8443/"),relay.replace(".com/",".com.evil.invalid/"),relay.replace("open.","user@open."),relay.replace("open.","other.")}){
            check(!IdentityPolicy.allowed(value));check(!IdentityPolicy.electricityRelay(value));check(!IdentityPolicy.electricity(value));
        }
        try{check(!IdentityPolicy.auth("http://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode(relay,"UTF-8")));}catch(Exception e){throw new AssertionError(e);}
        System.out.println("Identity URL policy: "+checks+" checks passed");
    }
}
