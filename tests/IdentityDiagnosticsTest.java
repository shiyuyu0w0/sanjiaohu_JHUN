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
            "https://authserver.jhun.edu.cn/authserver/SECRET?secret=SECRET",
            "https://SECRET.example/SECRET?SECRET=SECRET",
            "https://SECRET:SECRET@authserver.jhun.edu.cn/authserver/login",
            "data:text/html,SECRET",null
        };
        IdentityDiagnostics trace=new IdentityDiagnostics();
        for(String url:urls){String safe=IdentityDiagnostics.route(url);check(!safe.contains("SECRET"));check(!safe.contains("?"));check(!safe.contains("#"));trace.add(IdentityDiagnostics.Event.GET,url,0);}
        check(!trace.report().contains("SECRET"));
        check(IdentityDiagnostics.route(urls[0]).equals("https://authserver.jhun.edu.cn/authserver/login"));
        for(int i=0;i<50;i++)trace.add(IdentityDiagnostics.Event.ERROR,IdentityPolicy.LOGIN,i);
        check(trace.report().split("\n").length==40);check(trace.report().contains("[49]"));check(!trace.report().contains("[0]"));
        System.out.println("Identity diagnostics: "+checks+" checks passed");
    }
}
