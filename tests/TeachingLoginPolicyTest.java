package cn.jhun.sanjiaohu;

public final class TeachingLoginPolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void kind(String text,TeachingLoginPolicy.Failure expected){
        TeachingLoginPolicy.Failure result=TeachingLoginPolicy.classify(text);
        check(result==expected,"unexpected classification: "+text+" -> "+result);
    }
    public static void main(String[] args){
        for(String text:new String[]{"连接失败","教务系统维护","服务器异常，账号密码暂时无法验证","密码验证服务异常","网络超时","HTTP 500","502 Bad Gateway","503 Service Unavailable","504 Gateway Timeout","Internal Server Error","登录未完成","学校尚未返回登录结果","学校登录页面变化","请稍后重试","账号密码验证失败，请稍后重试","",null}){
            TeachingLoginPolicy.Failure failure=TeachingLoginPolicy.classify(text);
            check(failure==TeachingLoginPolicy.Failure.TEMPORARY,"service failure must remain retryable: "+text);
            check(!failure.manual&&!failure.warning,"service failure must not block login or show password warning: "+text);
        }
        for(String text:new String[]{"用户名或密码错误","密码不正确","账号或密码有误，请重试","用户名与密码不匹配","账号不存在","用户不存在","Invalid credentials","Incorrect password","Password is invalid"}){
            kind(text,TeachingLoginPolicy.Failure.CREDENTIALS);
        }
        kind("账号已锁定，请24小时后重试",TeachingLoginPolicy.Failure.LOCKED);
        kind("账户被冻结，请联系管理员",TeachingLoginPolicy.Failure.LOCKED);
        kind("请输入验证码",TeachingLoginPolicy.Failure.CAPTCHA);
        kind("验证码错误，请重试",TeachingLoginPolicy.Failure.CAPTCHA);
        check(TeachingLoginPolicy.Failure.CREDENTIALS.manual&&TeachingLoginPolicy.Failure.CREDENTIALS.warning,"explicit bad password stops repeated submissions");
        check(TeachingLoginPolicy.Failure.LOCKED.manual&&TeachingLoginPolicy.Failure.LOCKED.warning,"locked account stops submissions");
        check(TeachingLoginPolicy.Failure.CAPTCHA.manual&&!TeachingLoginPolicy.Failure.CAPTCHA.warning,"captcha asks for interaction without blaming password");
        check(!TeachingLoginPolicy.hasManualReason(""),"old unclassified blocks are cleared on upgrade");
        check(!TeachingLoginPolicy.hasManualReason("TEMPORARY"),"temporary failures cannot persist as blocks");
        check(!TeachingLoginPolicy.hasManualReason("unknown"),"unknown old failure cannot disable future login");
        for(String reason:new String[]{"CREDENTIALS","LOCKED","CAPTCHA","SESSION","LOCAL"})check(TeachingLoginPolicy.hasManualReason(reason),"keep a confirmed manual intervention: "+reason);
        System.out.println("PASS: "+checks+" teaching login failure classification and legacy-block recovery checks");
    }
}
