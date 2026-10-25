package cn.jhun.sanjiaohu;

import java.util.Locale;
import java.util.regex.Pattern;

/** Only an explicit authentication rejection may disable future automatic login. */
final class TeachingLoginPolicy {
    enum Failure {
        TEMPORARY(false,false),CREDENTIALS(true,true),LOCKED(true,true),CAPTCHA(true,false),SESSION(true,false),LOCAL(true,false);
        final boolean manual,warning;
        Failure(boolean manual,boolean warning){this.manual=manual;this.warning=warning;}
    }
    private static final Pattern SERVICE=Pattern.compile("服务器|服务(?:异常|错误|不可用|繁忙)|系统(?:异常|错误|维护|繁忙)|网络|连接|超时|timeout|timed out|unavailable|internal server error|bad gateway|gateway timeout|network|service error|server error|\\b50[0234]\\b");
    private static final Pattern PASSWORD=Pattern.compile("(?:密码|口令|password).{0,16}(?:错误|不正确|有误|不匹配|无效|incorrect|invalid|wrong)|(?:incorrect|invalid|wrong).{0,16}password|账号不存在|帐号不存在|用户名不存在|用户不存在|无此用户|invalid credentials|bad credentials");
    static Failure classify(String message){
        String text=message==null?"":message.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        if(text.matches(".*(?:账号|帐号|账户|用户).{0,12}(?:锁定|已锁|冻结|停用|禁用).*"))return Failure.LOCKED;
        if(SERVICE.matcher(text).find())return Failure.TEMPORARY;
        if(text.contains("验证码")||text.contains("captcha"))return Failure.CAPTCHA;
        if(PASSWORD.matcher(text).find())return Failure.CREDENTIALS;
        return Failure.TEMPORARY;
    }
    static boolean hasManualReason(String reason){
        try{return Failure.valueOf(reason).manual;}catch(Exception ignored){return false;}
    }
}
