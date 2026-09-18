package cn.jhun.sanjiaohu;

public final class IdentityNavigationTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("check "+checks);}
    public static void main(String[] args) throws Exception{
        IdentityNavigation n=new IdentityNavigation();n.begin(0);
        check(n.visit(IdentityPolicy.LOGIN,0));
        check(n.visit("http://ehall.jhun.edu.cn/login?ticket=ST-synthetic",1));
        check(n.visit("http://ehall.jhun.edu.cn/new/index.html",2));
        check(n.finishOnce());check(!n.finishOnce());
        n.userGesture(3);check(!n.finishOnce());
        n.begin(4);check(n.finishOnce());
        n.begin(0);
        for(int i=0;i<4;i++)check(n.visit(IdentityPolicy.LOGIN+"&nonce="+i,i));
        check(!n.visit(IdentityPolicy.LOGIN+"&nonce=5",5));
        check(!n.visit(IdentityPolicy.REPAIR,6));check(!n.finishOnce());
        n.userGesture(7);check(n.stopped);
        n.begin(8);check(n.visit(IdentityPolicy.LOGIN,8));
        n.begin(0);check(n.visit(IdentityPolicy.LOGIN,0));n.stop();
        check(!n.visit(IdentityPolicy.REPAIR,1));check(!n.finishOnce());
        n.begin(0);for(int i=0;i<24;i++)check(n.visit("http://ehall.jhun.edu.cn/redirect/"+i,i));
        check(!n.visit("http://ehall.jhun.edu.cn/redirect/25",25));
        check(IdentityPolicy.allowed(IdentityPolicy.REPAIR_ENTRY));
        check(IdentityPolicy.desktopRepair("http://hqfw.jhun.edu.cn/wsbx/html/pc/index.html#/bxsq/wybx"));
        check(!IdentityPolicy.desktopRepair(IdentityPolicy.REPAIR));
        check(!IdentityPolicy.desktopRepair("http://hqfw.jhun.edu.cn.evil.invalid/wsbx/html/pc/index.html"));
        n.begin(0);
        String hall="https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fehall.jhun.edu.cn%2Fnew%2Findex.html";
        String repair="https://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode(IdentityPolicy.REPAIR_CALLBACK,"UTF-8");
        check(!IdentityNavigation.key(hall).equals(IdentityNavigation.key(repair)));
        check(!IdentityNavigation.key(hall+"&execution=e1s1").equals(IdentityNavigation.key(hall+"&execution=e1s2")));
        check(IdentityNavigation.key(hall+"&nonce=1").equals(IdentityNavigation.key(hall+"&nonce=2")));
        check(IdentityNavigation.key(hall+"&renew=true&execution=e1s1").equals(IdentityNavigation.key(hall+"&execution=e1s1&renew=true")));
        // Actual phone trace: HTTPS -> school HTTP redirect -> CAS callback -> mobile page.
        n.begin(0);
        String httpLogin=IdentityPolicy.LOGIN.replace("https:","http:");
        check(n.visit(IdentityPolicy.LOGIN,1));check(IdentityPolicy.allowed(httpLogin));
        check(n.visit(httpLogin,2));check(!n.stopped);
        check(n.visit("http://hqfw.jhun.edu.cn/wsbx/login/cas?ticket=ST-synthetic",3));
        check(n.visit(IdentityPolicy.REPAIR,4));check(n.finishOnce());
        n.begin(0);
        check(n.visit(IdentityPolicy.ELECTRICITY_LOGIN,1));
        check(!IdentityNavigation.key(IdentityPolicy.ELECTRICITY_LOGIN).equals(IdentityNavigation.key(IdentityPolicy.LOGIN)));
        check(n.visit(IdentityPolicy.ELECTRICITY_SERVICE+"&ticket=ST-synthetic",2));
        check(n.visit(IdentityPolicy.ELECTRICITY_SERVICE,3));check(n.finishOnce());check(!n.finishOnce());
        n.stop();check(!n.visit(IdentityPolicy.ELECTRICITY_SERVICE,4));
        n.begin(5);check(n.visit(IdentityPolicy.ELECTRICITY_LOGIN,6));check(!n.completed);
        String cloud="https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do";
        n.begin(0);
        for(String url:new String[]{IdentityPolicy.ELECTRICITY_LOGIN,IdentityPolicy.ELECTRICITY_SERVICE+"&ticket=ST-synthetic","https://open.17wanxiao.com/?state=synthetic",cloud}){
            check(IdentityPolicy.allowed(url));check(n.visit(url,1));
        }
        check(IdentityPolicy.electricity(cloud));check(n.finishOnce());check(!n.finishOnce());
        check(!IdentityNavigation.key(cloud).equals(IdentityNavigation.key(cloud.replace(":18443",":443"))));
        check(IdentityNavigation.key(cloud+"?ticket=ST-synthetic").equals(IdentityNavigation.key(cloud)));
        String raw=cloud+"?data={test}";
        check(IdentityNavigation.key(raw).startsWith(cloud));
        check(!IdentityNavigation.key(raw).equals(IdentityNavigation.key(raw.replace("type.do","next.do"))));
        check(!IdentityNavigation.key(raw).equals(IdentityNavigation.key(raw.replace("{test}","{other}"))));
        check(IdentityNavigation.key(raw+"&ticket=ST-one").equals(IdentityNavigation.key(raw+"&ticket=ST-two")));
        n.begin(0);for(int i=0;i<4;i++)check(n.visit(raw+"&ticket=ST-"+i,i));check(!n.visit(raw+"&ticket=ST-again",5));
        System.out.println("Identity navigation: "+checks+" checks passed");
    }
}
