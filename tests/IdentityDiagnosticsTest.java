package cn.jhun.sanjiaohu;

public final class IdentityDiagnosticsTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("case "+checks);}
    public static void main(String[] args){
        String web="Mozilla/5.0 (Linux; Android 14; Test Build/TEST; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/125.0.0.0 Mobile Safari/537.36";
        String compatible=IdentityDiagnostics.browserAgent(web);
        check(!compatible.contains("; wv"));check(!compatible.contains("Version/4.0"));check(compatible.contains("Chrome/125.0.0.0 Mobile"));check(compatible.contains("Android 14"));check(IdentityDiagnostics.browserAgent(compatible).equals(compatible));
        String[] urls={
            "https://authserver.jhun.edu.cn/authserver/login;jsessionid=SECRET?username=SECRET&password=SECRET&ticket=ST-SECRET&execution=SECRET#SECRET",
            "http://hqfw.jhun.edu.cn/wsbx/login/cas?ticket=ST-SECRET#SECRET",
            "https://hub.17wanxiao.com/bsacs/light.action?ticket=ST-SECRET&flag=SECRET#SECRET",
            "https://hub.17wanxiao.com/bsacs/SECRET?ticket=ST-SECRET",
            "https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do;jsessionid=SECRET?token=SECRET&ticket=ST-SECRET#SECRET",
            "https://h5cloud.17wanxiao.com:18443/CloudPayment/SECRET?token=SECRET",
            "https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do?data={\"token\":\"SECRET\"}#SECRET",
            "https://intermediate.17wanxiao.com:18443/SECRET?data={\"token\":\"SECRET\"}",
            "intent://SECRET/path?token=SECRET#Intent;scheme=SECRET;end",
            "https://mclient.alipay.com/h5pay/h5RouteAppSenior/index.html?contextId=SECRET&cookieToken=SECRET&server_param=SECRET#SECRET",
            "alipays://platformapi/startapp?appId=SECRET&url=SECRET",
            "alipay://platformapi/startapp?order=SECRET",
            "https://authserver.jhun.edu.cn/authserver/SECRET?secret=SECRET",
            "https://SECRET.example/SECRET?SECRET=SECRET",
            "https://SECRET:SECRET@authserver.jhun.edu.cn/authserver/login",
            "data:text/html,SECRET",null
        };
        IdentityDiagnostics trace=new IdentityDiagnostics();
        for(String url:urls){String safe=IdentityDiagnostics.route(url);check(!safe.contains("SECRET"));check(!safe.contains("?"));check(!safe.contains("#"));trace.add(IdentityDiagnostics.Event.GET,url,0);}
        check(!trace.report().contains("SECRET"));
        check(IdentityDiagnostics.route(urls[0]).equals("https://authserver.jhun.edu.cn/authserver/login"));
        check(IdentityDiagnostics.route("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do?token=SECRET").equals("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do"));
        check(IdentityDiagnostics.route("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do?data={SECRET}").equals("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do"));
        check(IdentityDiagnostics.route("https://intermediate.17wanxiao.com:18443/SECRET?token=SECRET").equals("https://intermediate.17wanxiao.com:18443/[其他路径]"));
        check(!IdentityPolicy.allowed("https://intermediate.17wanxiao.com:18443/"));
        check(IdentityDiagnostics.route("intent://SECRET/path#SECRET").equals("[外部应用协议：intent]"));
        check(IdentityDiagnostics.route("https://mclient.alipay.com/h5pay/h5RouteAppSenior/index.html?cookieToken=SECRET").equals("https://mclient.alipay.com/h5pay/h5RouteAppSenior/index.html"));
        trace.add(IdentityDiagnostics.Event.ALIPAY_OPEN,"alipays://platformapi/startapp?order=SECRET",0);check(!trace.report().contains("SECRET"));
        trace.add(IdentityDiagnostics.Event.BLOCKED_MAIN,"https://intermediate.17wanxiao.com:18443/SECRET?data={SECRET}",0);
        check(trace.report().contains("BLOCKED_MAIN https://intermediate.17wanxiao.com:18443/[其他路径]"));check(!trace.report().contains("SECRET"));
        for(int i=0;i<50;i++)trace.add(IdentityDiagnostics.Event.ERROR,IdentityPolicy.LOGIN,i);
        check(trace.report().split("\n").length==40);check(trace.report().contains("[49]"));check(!trace.report().contains("[0]"));
        System.out.println("Identity diagnostics: "+checks+" checks passed");
    }
}
