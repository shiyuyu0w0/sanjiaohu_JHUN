package cn.jhun.sanjiaohu;

/** Identifies only the bundled 1.0 seed, preserving actual successful syncs. */
public final class CachePolicy {
    private CachePolicy(){}
    public static boolean usable(long savedAt,String source){
        return "school-sync".equals(source) || (savedAt>0 && savedAt!=1789135487474L);
    }
}
