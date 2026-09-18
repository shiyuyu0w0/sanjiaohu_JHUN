package cn.jhun.sanjiaohu;

public final class PhysicsLabPolicyTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("check "+checks);}
    static final String SITE="http://wlxpk.jhun.edu.cn:6603/Page/Android/html/index.htm";
    public static void main(String[] args) throws Exception{
        check(PhysicsLabPolicy.allowed(PhysicsLabPolicy.REPORT));
        check(PhysicsLabPolicy.reportSite(PhysicsLabPolicy.REPORT));
        check(PhysicsLabPolicy.allowed(SITE));
        check(PhysicsLabPolicy.allowed("https://wlxpk.jhun.edu.cn:6603/Page/Android/html/index.htm"));
        check(PhysicsLabPolicy.allowed("http://wlxpk.jhun.edu.cn/Page/Android/html/index.htm"));
        check(!PhysicsLabPolicy.allowed("http://wlxpk.jhun.edu.cn:6604/Page/Android/html/index.htm"));
        check(!PhysicsLabPolicy.allowed("http://wlxpk.jhun.edu.cn.evil.invalid:6603/Page/Android/html/index.htm"));
        check(!PhysicsLabPolicy.allowed("http://wlxpk.jhun.edu.cn:6603.evil.invalid/"));
        check(!PhysicsLabPolicy.allowed("http://evil.invalid/Page/Android/html/index.htm"));
        check(!PhysicsLabPolicy.allowed("ftp://wlxpk.jhun.edu.cn:6603/Page/Android/html/index.htm"));
        check(!PhysicsLabPolicy.allowed("javascript:alert(1)"));
        check(!PhysicsLabPolicy.allowed("intent://scan/#Intent;scheme=zxing;end"));
        check(!PhysicsLabPolicy.allowed("http://user:pass@wlxpk.jhun.edu.cn:6603/"));
        check(!PhysicsLabPolicy.allowed("http://wlxpk.jhun.edu.cn:6603@evil.invalid/"));
        // School HTTPS stays open for single sign-on and redirects; cleartext stays exact.
        check(PhysicsLabPolicy.allowed("https://authserver.jhun.edu.cn/authserver/login?service=http%3A%2F%2Fwlxpk.jhun.edu.cn%3A6603%2F"));
        check(PhysicsLabPolicy.allowed("https://jwxt.jhun.edu.cn/frame/homes.action"));
        check(PhysicsLabPolicy.allowed("https://jhun.edu.cn/"));
        check(PhysicsLabPolicy.allowed("http://authserver.jhun.edu.cn/authserver/login"));
        check(!PhysicsLabPolicy.allowed("http://jwxt.jhun.edu.cn/frame/homes.action"));
        check(!PhysicsLabPolicy.allowed("https://jwxt.jhun.edu.cn:8443/frame/homes.action"));
        check(!PhysicsLabPolicy.allowed("https://hub.17wanxiao.com/bsacs/light.action"));
        check(!PhysicsLabPolicy.allowed("https://h5cloud.17wanxiao.com:18443/CloudPayment/bill/type.do"));
        check(!PhysicsLabPolicy.allowed(null));
        check(!PhysicsLabPolicy.allowed(""));
        // WebView delivers raw braces in query and fragment; the origin is what counts.
        check(PhysicsLabPolicy.allowed(SITE+"?data={a:b}&x=1#/home"));
        check(PhysicsLabPolicy.allowed(SITE+"?{a:b}"));
        check(SITE.equals(PhysicsLabPolicy.navigationUri(SITE+"?data={a:b}#/home").toString()));
        check(!PhysicsLabPolicy.allowed(SITE.replace("/Page","/{a:b}/Page")));
        // File picker keeps the form's accept types only when they are real MIME types.
        check(PhysicsLabPolicy.pickerType(null)==null);
        check(PhysicsLabPolicy.pickerType(new String[0])==null);
        check(PhysicsLabPolicy.pickerType(new String[]{"image/*"})==null);
        check(PhysicsLabPolicy.pickerType(new String[]{"application/pdf","image/*"})==null);
        check("*/*".equals(PhysicsLabPolicy.pickerType(new String[]{".docx",".pdf"})));
        check("*/*".equals(PhysicsLabPolicy.pickerType(new String[]{"docx"})));
        check("*/*".equals(PhysicsLabPolicy.pickerType(new String[]{""})));
        check("*/*".equals(PhysicsLabPolicy.pickerType(new String[]{null})));
        check("*/*".equals(PhysicsLabPolicy.pickerType(new String[]{"image/*",".docx"})));
        System.out.println(checks+" 项大物实验报告入口检查通过");
    }
}
