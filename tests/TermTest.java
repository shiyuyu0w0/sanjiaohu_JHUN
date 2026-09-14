package cn.jhun.sanjiaohu;
public final class TermTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(Term.key("2025-2026学年第二学期").equals("2025-2"));
        check(Term.same("2025－2026 学年 第2学期","2025-2026学年第二学期"));
        check(!Term.same("2025-2026学年第一学期","2025-2026学年第二学期"));
        check(!Term.same("2025-2026学年第一学期","2026-2027学年第一学期"));
        check(Term.label("2025 — 2026 学年 第一学期").equals("2025-2026学年第一学期"));
        for(String s:new String[]{"","../schedule","2025-2028学年第一学期","自定义学期","2025-2026学年第三学期"}){try{Term.key(s);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        System.out.println(checks+" semester normalization checks passed");
    }
}
