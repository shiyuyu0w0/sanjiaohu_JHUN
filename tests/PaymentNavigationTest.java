package cn.jhun.sanjiaohu;

public final class PaymentNavigationTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("case "+checks);}
    public static void main(String[] args) throws Exception {
        String h5="https://mclient.alipay.com/h5pay/h5RouteAppSenior/index.html";
        for(String url:new String[]{h5,h5.replace(".com/",".com:443/"),h5+"?contextId=synthetic&data={test}#state"}){
            check(PaymentNavigation.alipayWeb(url));check(!IdentityPolicy.auth(url));check(!IdentityPolicy.allowed(url));
        }
        for(String url:new String[]{null,"",h5.replace("https:","http:"),h5.replace(".com/",".com:18443/"),h5.replace(".com/",".com.evil.invalid/"),h5.replace("mclient.","user@mclient."),"https://evil.invalid/?next="+h5})check(!PaymentNavigation.alipayWeb(url));
        check(!IdentityPolicy.auth("https://authserver.jhun.edu.cn/authserver/login?service="+java.net.URLEncoder.encode(h5,"UTF-8")));
        String link="alipays://platformapi/startapp?appId=20000067&url=https%3A%2F%2Fexample.invalid%2Fsynthetic";
        check(link.equals(PaymentNavigation.alipayLink(link)));
        check(link.replace("alipays:","alipay:").equals(PaymentNavigation.alipayLink(link.replace("alipays:","alipay:"))));
        String wrapper=link.replace("alipays:","intent:")+"#Intent;scheme=alipays;package="+PaymentNavigation.ALIPAY_PACKAGE+";end";
        check(link.equals(PaymentNavigation.alipayLink(wrapper)));
        String extras=wrapper.replace(";end",";action=android.intent.action.SEND;component=evil/.Activity;launchFlags=0xffffffff;S.browser_fallback_url=https%3A%2F%2Fevil.invalid;S.secret=synthetic;end");
        check(link.equals(PaymentNavigation.alipayLink(extras)));
        for(String url:new String[]{null,"",h5,"javascript:alert(1)","file:///private","content://private","weixin://pay","alipays://evil/startapp?test=1","alipays://platformapi:443/startapp","alipays://user@platformapi/startapp","alipays://platformapi/other",wrapper.replace(PaymentNavigation.ALIPAY_PACKAGE,"evil.package"),wrapper.replace("scheme=alipays","scheme=https"),wrapper.replace(";end",";SEL;scheme=alipays;end"),wrapper.replace(";end",";scheme=alipays;end"),wrapper.replace(";end","")})check(PaymentNavigation.alipayLink(url)==null);
        for(String source:new String[]{IdentityPolicy.ELECTRICITY_SERVICE,"https://wapnew.17wanxiao.com/",h5}){
            check(PaymentNavigation.sourceAllowed(true,source,true,"GET"));
            check(!PaymentNavigation.sourceAllowed(false,source,true,"GET"));
            check(PaymentNavigation.sourceAllowed(true,source,false,"GET")==PaymentNavigation.alipayWeb(source));
            check(!PaymentNavigation.sourceAllowed(true,source,true,"POST"));
        }
        for(String source:new String[]{null,"",IdentityPolicy.LOGIN,IdentityPolicy.REPAIR,"https://evil.invalid/","https://mclient.alipay.com.evil.invalid/"})check(!PaymentNavigation.sourceAllowed(true,source,true,"GET"));
        IdentityNavigation n=new IdentityNavigation();n.begin(0);check(n.visit("https://wapnew.17wanxiao.com/",1));check(n.visit(h5,2));check(n.finishOnce());
        n.userGesture(3);check(n.visit("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do",4));check(!n.finishOnce());
        System.out.println("Payment navigation: "+checks+" checks passed (synthetic payment data)");
    }
}
