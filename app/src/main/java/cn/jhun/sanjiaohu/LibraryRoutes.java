package cn.jhun.sanjiaohu;

import java.io.IOException;

/**
 * 图书馆线路选择：**优先桌面版 OPAC，不可用时回退移动版**。
 *
 * 两条线路的差别（实测）：
 *
 * |            | 桌面版 OPAC            | 移动版            |
 * |------------|----------------------|------------------|
 * | 红楼梦结果数 | 754                  | 550              |
 * | 每页        | 可调 5~200            | 固定 20          |
 * | 越界        | 返回 0 条（干净）        | 重复返回末页       |
 * | 详情中文bug  | 无                    | 有               |
 *
 * 上层（{@link LibraryActivity}）只调这里的 search/detail，不关心走的是哪条线路。
 *
 * ## 回退策略
 *
 * **每一次检索都会真的去请求一次桌面版**，成功就用它、失败才回退移动版。
 * 不做「失败后一段时间内不再尝试」的负缓存——那样一旦网络抖动误判，
 * 就会长时间锁死在移动版上，用户再也回不到桌面版。
 *
 * 「探测」只用来决定**要不要省掉这一次尝试**：
 * 它是一道额外的轻量请求，仅当上次尝试失败过才启用，并且失败后隔一小段时间就重新尝试，
 * 保证网络恢复后能自动切回桌面版。
 */
final class LibraryRoutes {
    private LibraryRoutes(){}

    /** 线路名，用于界面提示。 */
    static final String OPAC="馆藏系统";
    static final String MOB="移动版";

    /**
     * 上次桌面版尝试失败的时间。0 表示没有失败记录。
     */
    private static long lastFailureAt;

    /**
     * 失败后的「免打扰」窗口。窗口内不再尝试桌面版，直接走移动版，
     * 避免每次检索都白等一次注定失败的网络请求。
     *
     * 实测：流量下 opac:8080 不可达，每次连接固定约 2.2 秒才失败。
     * 若每次检索都重试，用户每点一次搜索就要多等 2 秒以上，且结论不会改变
     * （该端口只对校园网开放，换网络才会变）。
     *
     * 但窗口**必须有上限且会被网络变化打断**——见 {@link #networkChanged()}，
     * 否则从流量切回校园网后就再也回不到桌面版（这正是上一版的 bug）。
     */
    static final long QUIET_PERIOD=90*1000L;

    /** 记录失败时的网络标识，用于判断「换网了」。 */
    private static String lastNetwork;

    private static String lastRoute=OPAC;

    /** 测试用：固定线路。null = 自动选择。 */
    private static String forced;

    /** 最近一次实际使用的线路，供界面显示。 */
    static String lastRoute(){return lastRoute;}

    /**
     * 本次是否要走桌面版。
     *
     * 满足任一条件就尝试：
     *   1. 没有失败记录
     *   2. 免打扰窗口已过（兜底，保证最终会重试）
     *   3. 网络发生了变化（从流量切回校园网，或反之）
     */
    static boolean shouldTryOpac(){
        if(forced!=null)return OPAC.equals(forced);
        if(lastFailureAt==0)return true;
        if(networkChanged())return true;
        return System.currentTimeMillis()-lastFailureAt>QUIET_PERIOD;
    }

    /** 记录当前网络标识，供下次比较。 */
    private static void rememberNetwork(){
        lastNetwork=LibraryNetwork.signature();
    }

    /** 网络是否与上次失败时不同——换网了就立刻重试桌面版。 */
    private static boolean networkChanged(){
        if(lastNetwork==null)return false;
        String now=LibraryNetwork.signature();
        return now!=null&&!now.equals(lastNetwork);
    }

    /** 记录一次桌面版失败。 */
    private static void noteFailure(){
        lastFailureAt=System.currentTimeMillis();
        rememberNetwork();
    }

    /** 记录一次桌面版成功——立即清除失败状态。 */
    private static void noteSuccess(){
        lastFailureAt=0;
        rememberNetwork();
    }

    /** 复位线路状态（测试用）。会清掉强制设置。 */
    static void reset(){
        lastFailureAt=0;
        lastNetwork=null;
        lastRoute=OPAC;
        forced=null;
    }

    /** 只清失败状态，保留强制设置。 */
    private static void clearFailure(){
        lastFailureAt=0;
        lastNetwork=null;
    }

    /** 当前是否处于「桌面版暂不可用、已回退移动版」的状态——用于界面提示。 */
    static boolean degraded(){
        return lastFailureAt!=0&&!shouldTryOpac();
    }

    /**
     * 在一台机器上测试用：指定固定线路。传 null 恢复自动选择。
     * 只清失败状态，**不会**清掉刚设置的强制值。
     */
    static void force(String route){
        forced=route;
        clearFailure();
        lastRoute=route==null?OPAC:route;
    }

    /**
     * 检索。优先桌面版；本次桌面版失败才回退移动版。
     * 每次调用都会重新尝试桌面版，网络恢复后能自动切回。
     */
    static LibraryApi.Page search(String keyword,String type,int page)throws IOException{
        if(MOB.equals(forced))return useMob(keyword,type,page);

        if(OPAC.equals(forced)||shouldTryOpac()){
            try{
                LibraryApi.Page result=LibraryOpacApi.search(keyword,type,page);
                noteSuccess();
                lastRoute=OPAC;
                return result;
            }catch(LibraryApi.Cancelled cancelled){
                throw cancelled;
            }catch(IOException opacError){
                // 桌面版这次失败：记下时间，本次立即回退移动版。
                // 不做长期负缓存——下次检索仍会重试桌面版。
                noteFailure();
            }
        }
        return useMob(keyword,type,page);
    }

    private static LibraryApi.Page useMob(String keyword,String type,int page)throws IOException{
        LibraryApi.Page result=LibraryApi.search(keyword,type,page);
        lastRoute=MOB;
        return result;
    }

    /* ------------------------------------------------------------------ */
    /* 可借数补全                                                          */
    /* ------------------------------------------------------------------ */

    /** 并发上限。实测站点能承受，且 5 路已能把 20 本压到 1 秒内。 */
    private static final int FILL_THREADS=5;
    /** 一次补全最多处理的册数，防止超大结果集把线程池压满。 */
    private static final int FILL_LIMIT=60;

    /** 单个结果的可借数回调。 */
    interface CountListener {
        /** @param available 可借册数；total 总藏册数 */
        void onCounts(LibraryApi.Item item,int available,int total);
        /** 当前是否已被取消（用户换了关键词或离开了页面）。 */
        boolean cancelled();
    }

    /**
     * 后台并发补全「可借 / 总藏」。
     *
     * 桌面版的列表不带这两个数字，只能逐本查（约 340ms/本）。
     * 这里用固定线程池并发拉取，每拿到一本就回调一次，界面逐个填上，
     * 而不是等全部完成才显示。任何一本失败都不影响其它本。
     *
     * 移动版列表**自带**这两个数字，所以只在桌面版结果上调用。
     */
    static void fillCounts(final java.util.List<LibraryApi.Item> items,final CountListener listener){
        if(items==null||items.isEmpty())return;
        final java.util.concurrent.ExecutorService pool=
            java.util.concurrent.Executors.newFixedThreadPool(FILL_THREADS);
        int queued=0;
        for(final LibraryApi.Item item:items){
            if(queued>=FILL_LIMIT)break;
            if(item.marcNo==null||item.marcNo.isEmpty())continue;
            if(item.available>=0)continue;      // 已有数字（移动版）不重复查
            queued++;
            pool.execute(()->{
                try{
                    if(listener.cancelled())return;
                    LibraryOpacApi.Counts counts=LibraryOpacApi.counts(item.marcNo);
                    if(counts==null||listener.cancelled())return;
                    listener.onCounts(item,counts.available,counts.total);
                }catch(LibraryApi.Cancelled ignored){
                    // 取消不是错误
                }catch(IOException ignored){
                    // 单本失败保持「馆藏待确认」，不影响其它本
                }
            });
        }
        pool.shutdown();
    }

    /** 取馆藏明细。与检索走同一条线路，避免同一次操作里两条线路混用。
     *  当前线路的详情失败时，回退另一条线路再试一次。 */
    static LibraryApi.Detail detail(Object target)throws IOException{
        boolean preferOpac=OPAC.equals(lastRoute)&&(OPAC.equals(forced)||shouldTryOpac());
        if(preferOpac){
            try{
                LibraryApi.Detail detail=LibraryOpacApi.detail(target);
                if(!detail.holdings.isEmpty()){
                    noteSuccess();
                    return detail;
                }
                // 桌面版没解析出馆藏：该 marc_no 可能只在移动版有明细，换一条再试
            }catch(LibraryApi.Cancelled cancelled){
                throw cancelled;
            }catch(IOException ignored){
                noteFailure();
            }
            try{
                return LibraryApi.detail(target);
            }catch(IOException mobError){
                throw new IOException("馆藏明细读取失败，请稍后重试");
            }
        }
        return LibraryApi.detail(target);
    }
}
