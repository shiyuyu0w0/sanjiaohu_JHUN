package cn.jhun.sanjiaohu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图书馆查询的解析与区位检查。
 *
 * 重点是两处历史上出过错的地方：
 *   1. 「社科借阅」是「社科借阅1/2/3」的前缀，必须最长匹配，否则 301-302 室会被错认成 405 室；
 *   2. J 类（艺术）单列 405 室，不能并进 I/K 的 304-305 室。
 * 另外用 26 个字母逐一钉住 guessRoom，防止区间悄悄漂移。
 */
public final class LibraryQueryTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("check "+checks);}
    static void check(int actual,int expected){
        checks++;
        if(actual!=expected)throw new AssertionError("check "+checks+"：期望 ["+expected+"]，实际 ["+actual+"]");
    }
    static void check(String actual,String expected){
        checks++;
        if(!expected.equals(actual))throw new AssertionError("check "+checks+"：期望 ["+expected+"]，实际 ["+actual+"]");
    }

    public static void main(String[] args){
        locationParsing();
        longestMatchRegression();
        guessRoomByClass();
        vagueAndSummary();
        inRoomFilter();
        statusMapping();
        listParsing();
        detailParsing();
        paging();
        desktopOpacParsing();
        System.out.println(checks+" 项图书馆查询检查通过");
    }

    /**
     * 桌面版 OPAC 的解析与字段映射。
     * 两条线路的字段体系不同，转换点必须正确，否则选了「著者」却按题名查。
     */
    static void desktopOpacParsing(){
        // 界面检索途径（与移动版共用）-> 桌面版字段码
        check(LibraryOpacApi.fieldCode("title"),"02");
        check(LibraryOpacApi.fieldCode("author"),"03");
        check(LibraryOpacApi.fieldCode("keyword"),"04");
        check(LibraryOpacApi.fieldCode("isbn"),"05");
        check(LibraryOpacApi.fieldCode("unknown"),"02");   // 未知退回题名

        // 每个界面途径都必须能映射到桌面版认识的字段码
        for(String[] entry:LibraryActivity.TYPES){
            String code=LibraryOpacApi.fieldCode(entry[0]);
            check(LibraryOpacApi.FIELDS.containsKey(code));
        }

        // 检索请求体：字段名与站点前端一致，且必须是合法 JSON 结构
        String body=LibraryOpacApi.searchBody("红楼梦","02",1,20);
        check(body.contains("\"searchWords\""));
        check(body.contains("\"fieldList\""));
        check(body.contains("\"fieldCode\":\"02\""));
        check(body.contains("\"fieldValue\":\"红楼梦\""));
        check(body.contains("\"pageSize\":20"));
        check(body.contains("\"pageCount\":1"));
        check(body.contains("\"first\":true"));
        // 中文必须原样传入（由 HttpURLConnection 按 UTF-8 编码发送）
        check(body.indexOf('红')>0);
        // 页码 2 时 first 应为 false
        check(LibraryOpacApi.searchBody("x","02",2,20).contains("\"first\":false"));

        // JSON 字符串转义
        check(LibraryOpacApi.unescape("a\\\"b"),"a\"b");
        check(LibraryOpacApi.unescape("a\\\\b"),"a\\b");
        check(LibraryOpacApi.unescape("\\u7ea2\\u697c\\u68a6"),"红楼梦");
        check(LibraryOpacApi.unescape("\\n"),"\n");
        check(LibraryOpacApi.unescape("plain"),"plain");
        check(LibraryOpacApi.unescape(null),"");

        // 解析 content 数组
        String json="{\"total\":754,\"cost\":0.05,\"content\":["
            +"{\"author\":\"谢铁骊,谢逢松著\",\"callNo\":\"I235/33\",\"docTypeName\":\"中文图书\","
            +"\"isbn\":\"7-5378-0122-3\",\"marcRecNo\":\"72722b2b4e72\",\"num\":1,\"pubYear\":\"1989.4\","
            +"\"publisher\":\"北岳文艺出版社\",\"sourceMarcRecNo\":\"0000016662\",\"title\":\"红楼梦:电影文学\"},"
            +"{\"author\":\"(清)曹雪芹著\",\"callNo\":\"I242.4/580\",\"marcRecNo\":\"343550734d38\","
            +"\"title\":\"红楼梦\",\"publisher\":\"漓江\"}"
            +"],\"facetsList\":[],\"filters\":[]}";
        java.util.List<LibraryApi.Item> items=LibraryOpacApi.parseItems(json);
        check(items.size(),2);
        check(items.get(0).title,"红楼梦:电影文学");
        check(items.get(0).author,"谢铁骊,谢逢松著");
        check(items.get(0).callNo,"I235/33");
        check(items.get(0).publisher,"北岳文艺出版社");
        check(items.get(0).materialType,"中文图书");
        check(items.get(0).marcNo,"72722b2b4e72");
        // 桌面版列表不带可借数，用 -1 表示未知（不是 0）
        check(items.get(0).available,-1);
        check(items.get(0).totalCopies,-1);
        check(items.get(1).title,"红楼梦");

        // 解析馆藏表格：5 列，馆藏地取 title 属性
        String itemHtml="<table id=\"item\"><tr class=\"greytext1\">"
            +"<td>索书号</td><td>条码号</td><td>年卷期</td><td>馆藏地</td><td>书刊状态</td></tr>"
            +"<tr class=\"whitetext\"><td width=\"10%\">I235/33</td><td width=\"15%\">A0080088</td>"
            +"<td width=\"15%\">&nbsp;</td>"
            +"<td width=\"25%\" title=\"文理学院图书馆\"><img src=\"x.gif\"/>文理学院图书馆</span></td>"
            +"<td width=\"25%\">赠送出</td></tr>"
            +"<tr class=\"whitetext\"><td width=\"10%\">I235/33</td><td width=\"15%\">A0080089</td>"
            +"<td width=\"15%\">&nbsp;</td>"
            +"<td width=\"25%\" title=\"社科借阅3(IK)(304-305室)\"><img src=\"x.gif\"/>社科借阅3(IK)(304-305室)</span></td>"
            +"<td width=\"25%\"><font color=green>可借</font></td></tr>"
            +"</table>";
        LibraryApi.Detail detail=LibraryOpacApi.parseItem(itemHtml);
        check(detail.holdings.size(),2);
        check(detail.available,1);                                 // 只有 1 册可借
        check(detail.holdings.get(0).barcode,"A0080088");
        check(detail.holdings.get(0).location,"文理学院图书馆");      // title 属性优先，不含 img 标签
        check(detail.holdings.get(0).status,"赠送出");
        check(detail.holdings.get(0).lendable==false);
        check(detail.holdings.get(1).location,"社科借阅3(IK)(304-305室)");
        check(detail.holdings.get(1).lendable);
        check(detail.holdings.get(1).placeText(),"3楼 304-305室");
        check(detail.area.rooms.size(),2);

        // 桌面版独有的状态词
        check(LibraryApi.classifyStatus("赠送出").level,"given");
        check(LibraryApi.classifyStatus("赠送出").lendable==false);
        check(LibraryApi.classifyStatus("非可借").level,"reading");
        check(LibraryApi.classifyStatus("声明丢失").level,"lost");
        check(LibraryApi.classifyStatus("剔旧报废").level,"lost");
        check(LibraryApi.classifyStatus("——").label,"");           // 占位符不是状态
        // 「借出-应还日期：...」应判为借出
        check(LibraryApi.classifyStatus("借出-应还日期：2027-04-10").level,"out");
        check(LibraryApi.classifyStatus("借出-应还日期：2027-04-10").lendable==false);

        // 线路常量
        check(LibraryRoutes.OPAC.equals(LibraryRoutes.OPAC));
        check(!LibraryRoutes.OPAC.equals(LibraryRoutes.MOB));

        // 「可借 / 总藏」解析：数字夹在 HTML 标签之间
        LibraryOpacApi.Counts counts=LibraryOpacApi.parseCounts(
            "{\"image\":\"..\\/tpl\\/images\\/nobook.jpg\",\"lendAvl\":\"<b>馆藏复本：<\\/b>3<br><b>可借复本：<\\/b>1\"}");
        check(counts!=null);
        check(counts.total,3);
        check(counts.available,1);
        // 全是 0 也算有效结果（不是 null），界面应显示 0 而不是「正在读取」
        LibraryOpacApi.Counts zero=LibraryOpacApi.parseCounts("{\"lendAvl\":\"<b>馆藏复本：</b>0<br><b>可借复本：</b>0\"}");
        check(zero!=null);
        check(zero.total,0);
        check(zero.available,0);
        // 没有这两个字段时返回 null，界面保持「正在读取」
        check(LibraryOpacApi.parseCounts("{\"image\":\"x.jpg\"}")==null);
        check(LibraryOpacApi.parseCounts(null)==null);
        check(LibraryOpacApi.parseCounts("")==null);

        // 可借数未取到时不要拼成「可借 正在读取… 册」
        LibraryApi.Item pending=new LibraryApi.Item();
        check(LibraryActivity.availabilityLabel(pending),"正在读取可借数…");
        pending.available=2;pending.totalCopies=6;
        check(LibraryActivity.availabilityLabel(pending),"可借 2 / 6 册");
        pending.available=0;pending.totalCopies=3;
        check(LibraryActivity.availabilityLabel(pending),"可借 0 / 3 册");

        routeState();
    }

    /**
     * 线路状态机。回归点：早先的实现「桌面版失败后负缓存 5 分钟」，
     * 导致回退到移动版后长时间回不去。现在必须**没有失败记录时就尝试桌面版**。
     */
    static void routeState(){
        LibraryRoutes.reset();
        // 无失败记录：应当尝试桌面版，且不算降级
        check(LibraryRoutes.shouldTryOpac());
        check(LibraryRoutes.degraded()==false);
        check(LibraryRoutes.OPAC.equals(LibraryRoutes.lastRoute()));

        // force() 必须真的生效（曾经被内部的 reset() 清掉，是个真 bug）
        LibraryRoutes.force(LibraryRoutes.MOB);
        check(LibraryRoutes.MOB.equals(LibraryRoutes.lastRoute()));
        check(LibraryRoutes.shouldTryOpac()==false);          // 强制移动版时不该试桌面版
        LibraryRoutes.force(LibraryRoutes.OPAC);
        check(LibraryRoutes.OPAC.equals(LibraryRoutes.lastRoute()));
        check(LibraryRoutes.shouldTryOpac());
        // 传 null 恢复自动
        LibraryRoutes.force(null);
        check(LibraryRoutes.OPAC.equals(LibraryRoutes.lastRoute()));
        check(LibraryRoutes.shouldTryOpac());

        // 两条线路名不能相同，界面靠它区分
        check(!LibraryRoutes.OPAC.equals(LibraryRoutes.MOB));
        check(LibraryRoutes.OPAC.length()>0&&LibraryRoutes.MOB.length()>0);

        // 免打扰窗口必须存在且有限：太短会每次检索都白等，太长会切不回桌面版
        check(LibraryRoutes.QUIET_PERIOD>=30*1000L);          // 至少 30 秒不再打扰
        check(LibraryRoutes.QUIET_PERIOD<=10*60*1000L);       // 但最多 10 分钟必定重试

        // 网络签名：未注入 Context 时应返回 null（表示「未知」），
        // 此时不得据此判定网络变化——否则会退化成每次检索都重试桌面版
        check(LibraryNetwork.signature()==null);
        check(LibraryNetwork.onWifi()==false);

        LibraryRoutes.reset();
    }

    /* ---------------------------------------------------------------- */

    static void locationParsing(){
        LibraryLocation.Place a=LibraryLocation.parseLocation("自科借阅1(N-S/U-Z)(104室)");
        check(a.name,"自科借阅1");
        check(a.room,"104室");
        check(a.floor,1);
        check(a.classes,"N-S/U-Z");
        check(!a.vague);

        // 全角括号必须和半角同样处理
        LibraryLocation.Place b=LibraryLocation.parseLocation("中文过刊阅览室（403室）");
        check(b.name,"中文过刊阅览室");
        check(b.room,"403室");
        check(b.floor,4);

        // 楼而非室号
        LibraryLocation.Place c=LibraryLocation.parseLocation("自科典藏库（5楼）");
        check(c.floor,5);

        // 室号不带「室」字，靠兜底的前缀匹配
        LibraryLocation.Place d=LibraryLocation.parseLocation("江大文库201");
        check(d.name,"江大文库");
        check(d.room,"201室");
        check(d.floor,2);

        // 笼统值：只写馆名，没有库室信息
        LibraryLocation.Place e=LibraryLocation.parseLocation("江汉大学图书馆");
        check(e.vague);
        check(!e.known);
        check(e.floor,0);

        LibraryLocation.Place f=LibraryLocation.parseLocation("");
        check(f.vague);

        // 「3楼 304-305室」这样的展示串
        check(LibraryLocation.parseLocation("社科借阅3(IK)(304-305室)").describe(),"3楼 304-305室");

        officialRoomList();
        unusableLocations();
    }

    /**
     * 官方馆藏地清单（桌面版 ajax_adv_info.php 的 locationList，32 项）必须全部可解析。
     * 库室表此前只覆盖 12 项——采样归纳漏了一大半，这个清单是权威来源。
     */
    static void officialRoomList(){
        String[] official={
            "新书借阅室","社科期刊阅览室","社科借阅1(A-C/E-G)(301-302室)","社科借阅2(DH)(303室)",
            "社科借阅3(IK)(304-305室)","自科借阅1(N-S/U-Z)(104室)","自科借阅2(T)(113室)",
            "中文过刊阅览室（403室）","港台版图书","文理学院图书馆","社科期刊阅览室","自科报刊阅览室",
            "外文图书阅览室（401室）","特藏阅览室","N-TM自科样本","社科典藏库（5楼）","自科典藏库（5楼）",
            "艺术学院图书馆","外文图书样本库（401室）","设计学院资料室","图书馆咨询台",
            "城市研究中心(图书馆713室）","图书馆网络部","剔旧图书临时书库（909）","社科借阅(J类)(405室)",
            "外文报刊阅览室","武汉政协智库信息中心图书资料室","武汉政协智库信息中心期刊室",
            "招生就业处（师活中心324）","外事处智慧书屋","社会合作与校友工作处","江汉大学实验中学"
        };
        check(official.length,32);
        for(String raw:official)check(LibraryLocation.parseLocation(raw).known);

        // 左右括号混用 + 室号带前缀：楼层要能取到 7
        LibraryLocation.Place city=LibraryLocation.parseLocation("城市研究中心(图书馆713室）");
        check(city.name,"城市研究中心");
        check(city.floor,7);
        // 官方的 J 类写法归到 405 室
        check(LibraryLocation.parseLocation("社科借阅(J类)(405室)").name,"社科借阅");
        check(LibraryLocation.parseLocation("社科借阅(J类)(405室)").floor,4);
        // 典藏库两种（社科/自科）都在 5 楼
        check(LibraryLocation.parseLocation("社科典藏库（5楼）").floor,5);
        // 剔旧书库 909 -> 9 楼
        check(LibraryLocation.parseLocation("剔旧图书临时书库（909）").floor,9);
        // 库室表项数应与官方清单一致（含 J 类别名）
        check(LibraryLocation.ROOMS.size()>=33);
        for(String key:LibraryLocation.ROOMS.keySet())check(!key.trim().isEmpty());

        // 官方清单**没有**、但实测馆藏地里真实出现的库室。
        // 抽样 8 个学科 × 20 本，这些值出现次数并不低（样本密集书库 143 次），
        // 说明官方清单是「配置项」而非「实际取值全集」，两张表都要维护。
        String[] observedOnly={"样本密集书库","过刊密集库","架空层仓库","武汉语言文化研究中心","汉口校区分馆"};
        for(String name:observedOnly)check(LibraryLocation.parseLocation(name).known);

        // 实测抽样到的全部 18 种馆藏地取值（8 学科 × 各 20 本），必须零漏判：
        // 要么定位到库室，要么被正确识别为不可用的代码值。
        String[] observed={
            "文理学院图书馆","社科借阅3(IK)(304-305室)","样本密集书库","剔旧图书临时书库（909）",
            "社科借阅2(DH)(303室)","过刊密集库","102","304--113","社科典藏库（5楼）",
            "中文过刊阅览室（403室）","不定馆藏地","社科借阅1(A-C/E-G)(301-302室)","自科借阅2(T)(113室)",
            "社科借阅(J类)(405室)","架空层仓库","武汉语言文化研究中心","汉口校区分馆","自科典藏库（5楼）"
        };
        check(observed.length,18);
        int located=0,code=0;
        for(String raw:observed){
            LibraryLocation.Place p=LibraryLocation.parseLocation(raw);
            if(p.known)located++;
            else if(p.unusable)code++;
            else throw new AssertionError("实测取值未识别: "+raw);
        }
        check(located,15);
        check(code,3);   // 102 / 304--113 / 不定馆藏地
    }

    /** 不可用的馆藏地值：不能用来推断位置，且要与「只写馆名」的正常笼统值区分。 */
    static void unusableLocations(){
        check(LibraryLocation.isUsableLocation("自科借阅1(N-S/U-Z)(104室)"));
        check(!LibraryLocation.isUsableLocation("江汉大学图书馆"));   // 笼统但正常
        check(!LibraryLocation.isUsableLocation("图书馆"));
        check(!LibraryLocation.isUsableLocation("不定馆藏地"));       // 占位文字
        check(!LibraryLocation.isUsableLocation("不详"));
        check(!LibraryLocation.isUsableLocation("304--113"));        // 内部代码
        check(!LibraryLocation.isUsableLocation("102"));             // 纯数字
        check(!LibraryLocation.isUsableLocation(""));
        check(!LibraryLocation.isUsableLocation(null));

        // 只写馆名：vague 但不算异常
        LibraryLocation.Place vague=LibraryLocation.parseLocation("江汉大学图书馆");
        check(vague.vague);
        check(!vague.unusable);
        // 代码值：vague 且标记为异常，便于界面区分提示
        LibraryLocation.Place code=LibraryLocation.parseLocation("304--113");
        check(code.vague);
        check(code.unusable);
        check(code.raw,"304--113");
        check(LibraryLocation.parseLocation("102").unusable);

        // 汇总时把代码值原文收集起来
        List<String> locations=new ArrayList<>(Arrays.asList(
            "304--113","江汉大学图书馆","102","自科借阅1(N-S/U-Z)(104室)"));
        LibraryLocation.Summary summary=LibraryLocation.summarizeLocation(
            locations,new boolean[]{false,false,false,true},"O13/1");
        check(summary.unusableLocations.size(),2);
        check(summary.unusableLocations.contains("304--113"));
        check(summary.unusableLocations.contains("102"));
        check(!summary.unusableLocations.contains("江汉大学图书馆"));  // 笼统值不算异常
        check(summary.vagueCount,3);
        check(summary.rooms.size(),1);
    }

    /** 前缀包含关系：短匹配会把 301-302 室误判成 405 室。 */
    static void longestMatchRegression(){
        check(LibraryLocation.matchRoomByName("社科借阅1"),"社科借阅1");
        check(LibraryLocation.matchRoomByName("社科借阅2"),"社科借阅2");
        check(LibraryLocation.matchRoomByName("社科借阅3"),"社科借阅3");
        check(LibraryLocation.matchRoomByName("社科借阅"),"社科借阅");

        LibraryLocation.Place a=LibraryLocation.parseLocation("社科借阅1(A-C/E-G)(301-302室)");
        check(a.name,"社科借阅1");
        check(a.room,"301-302室");
        check(a.floor,3);

        // J 类所在的 405 室确实叫「社科借阅」，不能被 1/2/3 抢走
        LibraryLocation.Place b=LibraryLocation.parseLocation("社科借阅(J)(405室)");
        check(b.name,"社科借阅");
        check(b.floor,4);
    }

    /** 逐字母核对中图法映射。J 必须单列。 */
    static void guessRoomByClass(){
        String[] letters="ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
        for(String letter:letters){
            LibraryLocation.Guess guess=LibraryLocation.guessRoom(letter+"123/1");
            String expected;
            switch(letter.charAt(0)){
                case 'A':case 'B':case 'C':case 'E':case 'F':case 'G':expected="社科借阅1 301-302室 3";break;
                case 'D':case 'H':expected="社科借阅2 303室 3";break;
                case 'J':expected="社科借阅 405室 4";break;   // 艺术单列
                case 'I':case 'K':expected="社科借阅3 304-305室 3";break;
                case 'T':expected="自科借阅2 113室 1";break;
                case 'N':case 'O':case 'P':case 'Q':case 'R':case 'S':
                case 'U':case 'V':case 'W':case 'X':case 'Y':case 'Z':expected="自科借阅1 104室 1";break;
                default:expected="";  // L / M 无对应库室
            }
            String actual=guess==null?"":guess.name+" "+guess.room+" "+guess.floor;
            check(actual,expected);
        }

        // 实测样本
        check(LibraryLocation.guessRoom("O13/1349").name,"自科借阅1");
        check(LibraryLocation.guessRoom("I207.411/557").name,"社科借阅3");
        check(LibraryLocation.guessRoom("H310.42/123").name,"社科借阅2");
        check(LibraryLocation.guessRoom("J218.2/45").name,"社科借阅");
        check(LibraryLocation.guessRoom("J218.2/45").room,"405室"); // 曾与 I/K 合并，此处钉死
        check(LibraryLocation.guessRoom("F0/1").name,"社科借阅1");
        check(LibraryLocation.guessRoom("")==null);                // 空索书号不猜
        check(LibraryLocation.guessRoom("O13/1349").note,"中图法 N-S / U-Z");
    }

    /** 笼统馆藏地的兜底推测：只在没有具体库室时启用。 */
    static void vagueAndSummary(){
        List<String> allVague=new ArrayList<>(Arrays.asList("江汉大学图书馆","江汉大学图书馆"));
        LibraryLocation.Summary vague=LibraryLocation.summarizeLocation(allVague,new boolean[]{false,false},"I242.47");
        check(!vague.hasSpecific());
        check(vague.vagueCount,2);
        check(vague.guess!=null);
        check(vague.guess.name,"社科借阅3");

        // 有具体库室时绝不使用推测
        List<String> mixed=new ArrayList<>(Arrays.asList("江汉大学图书馆","自科借阅1(N-S/U-Z)(104室)"));
        LibraryLocation.Summary summary=LibraryLocation.summarizeLocation(mixed,new boolean[]{false,true},"O13/1");
        check(summary.hasSpecific());
        check(summary.vagueCount,1);
        check(summary.guess==null);
        check(summary.rooms.size(),1);
        check(summary.rooms.get(0).place.name,"自科借阅1");
        check(summary.rooms.get(0).total,1);
        check(summary.rooms.get(0).available,1);

        // 同一库室的多种写法应归并到一起，并按楼层排序
        List<String> several=new ArrayList<>(Arrays.asList(
            "社科借阅3(IK)(304-305室)","社科借阅3（IK）（304-305室）","自科借阅1(N-S/U-Z)(104室)"));
        LibraryLocation.Summary sorted=LibraryLocation.summarizeLocation(several,new boolean[]{true,false,true},"");
        check(sorted.rooms.size(),2);
        check(sorted.rooms.get(0).place.name,"自科借阅1"); // 1 楼在前
        check(sorted.rooms.get(1).place.name,"社科借阅3");
        check(sorted.rooms.get(1).total,2);
        check(sorted.rooms.get(1).available,1);
    }

    static void inRoomFilter(){
        List<String> holdings=new ArrayList<>(Arrays.asList(
            "自科借阅1(N-S/U-Z)(104室)","江汉大学图书馆","中文过刊阅览室（403室）"));
        check(LibraryLocation.inRoom(holdings,"104"));
        check(LibraryLocation.inRoom(holdings,"自科"));
        check(LibraryLocation.inRoom(holdings,"403"));   // 全角原值也要能查
        check(LibraryLocation.inRoom(holdings,"社科借阅3")==false);
        check(LibraryLocation.inRoom(holdings,"")==false); // 空条件不匹配任何东西
        check(LibraryLocation.inRoom(new ArrayList<>(Arrays.asList("江汉大学图书馆")),"104")==false);
    }

    static void statusMapping(){
        check(LibraryApi.classifyStatus("可借").level,"available");
        check(LibraryApi.classifyStatus("可借").lendable);
        check(LibraryApi.classifyStatus("在架").lendable);
        check(LibraryApi.classifyStatus("可借(在架)").lendable);
        check(LibraryApi.classifyStatus("在编").level,"processing");
        check(LibraryApi.classifyStatus("在编").lendable==false);
        check(LibraryApi.classifyStatus("借出").level,"out");
        check(LibraryApi.classifyStatus("阅览").level,"reading");
        check(LibraryApi.classifyStatus("遗失").level,"lost");
        // 未知值原样透传且判为不可借——宁可漏判也不误报
        check(LibraryApi.classifyStatus("调拨中").level,"unknown");
        check(LibraryApi.classifyStatus("调拨中").label,"调拨中");
        check(LibraryApi.classifyStatus("调拨中").lendable==false);
        check(LibraryApi.classifyStatus("").level,"unknown");
        check(LibraryApi.classifyStatus(null).lendable==false);
    }

    /** 用实测抓取的列表页片段（结构与真实页面逐字一致）。 */
    static void listParsing(){
        String html="<div class=\"tit\">共550条搜索结果</div>"
            +"<div class=\"list\"><ul>"
            // 实测：class="result" 在内层 <a> 上，<li> 本身没有 class
            +"<li>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"detailParam\" value='{\"marc_no\":\"6949424748766a6e4453427668704d306a4f543752513d3d\",\"title\":\"红楼梦\"}'/>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"mainKey\" value=''/>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"title\" value='红楼梦'/>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"author\" value='曹雪芹，高鹗著 '/>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"publisher\" value=''/>"
            +"<input type=\"hidden\" disabled=\"disabled\" name=\"language\" value=''/>"
            +"<a href=\"javascript:void(0)\" class=\"result\"><div class=\"title\"><span>红楼梦</span></div>"
            +"<div class=\"detail\">"
            +"<p>作者：曹雪芹，高鹗著 </p>"
            +"<p>索书号：I242.47/188/1, I242.47/188/2</p>"
            +"<p>流通类型：中文图书</p>"
            +"<p>可借/总藏：0/6</p>"
            +"<p>出版：</p>"
            +"</div></a></li>"
            // 没有 detailParam 的 <li> 不是结果项，必须跳过
            +"<li><a href=\"#\" class=\"result\">空项</a></li>"
            +"</ul></div>"
            +"<select id=\"pagenum\"><option value=\"x\">1/28</option></select>";

        List<LibraryApi.Item> items=LibraryApi.parseListItems(html);
        check(items.size(),1);
        LibraryApi.Item item=items.get(0);
        check(item.title,"红楼梦");
        check(item.author,"曹雪芹，高鹗著");
        check(item.callNo,"I242.47/188/1, I242.47/188/2");
        check(item.materialType,"中文图书");
        check(item.available,0);
        check(item.totalCopies,6);
        check(item.marcNo,"6949424748766a6e4453427668704d306a4f543752513d3d");
        check(item.availabilityText(),"0 / 6");
        check(item.key(),"6949424748766a6e4453427668704d306a4f543752513d3d");

        // 列表页字段名与详情明细必须分开：totalCopies 是汇总数字而非明细数组
        check(item.detailLoaded==false);

        // HTML 实体与标签清理
        check(LibraryApi.clean("<b>A&amp;B</b> &lt;x&gt;"),"A&B <x>");
        check(LibraryApi.clean("  多   空白  "),"多 空白");
        check(LibraryApi.clean(null),"");

        // hidden input 属性顺序颠倒也要能取到
        check(LibraryApi.hiddenValue("<input name='a' value='1'>","a"),"");
        check(LibraryApi.hiddenValue("<input type=\"hidden\" name=\"title\" value='红楼梦'>","title"),"红楼梦");
        check(LibraryApi.hiddenValue("<input value='v' name=\"title\">","title"),"v");
        // 实测的 detailParam 是单引号包裹、内含双引号的 JSON
        check(LibraryApi.hiddenValue("<input name=\"detailParam\" value='{\"marc_no\":\"abc\"}'/>","detailParam"),
            "{\"marc_no\":\"abc\"}");
        check(LibraryApi.jsonString("{\"marc_no\":\"abc\",\"title\":\"x\"}","marc_no"),"abc");
    }

    /** 用实测抓取的详情页片段：这批馆藏地全是笼统值且状态为在编。 */
    static void detailParsing(){
        String html="<div class=\"tit\"><h1>红楼梦</h1></div>"
            +"<div class=\"catalog\">"
            +"<p>出版发行项：北京:中华书局,2009</p>"
            +"<p>个人责任者：曹雪芹, 约1715-1763 高鹗 著</p>"
            +"<p>ISBN及定价：978-7-101-06730-9/CNY76.00（全二册）</p>"
            +"<p>学科主题：古典小说-中国-清代</p>"
            +"<p>中图法分类号：I242.47</p>"
            +"</div>"
            +"<article>"
            +tableLib("694942474876","江汉大学图书馆","在编","I242.47/557")
            +tableLib("694942474877","江汉大学图书馆","在编","I242.47/557")
            +"</article>";

        LibraryApi.Detail detail=LibraryApi.parseDetail(html);
        check(detail.title,"红楼梦");
        check(detail.author,"曹雪芹, 约1715-1763 高鹗 著");
        check(detail.publisher,"北京:中华书局,2009");
        check(detail.isbn,"978-7-101-06730-9");
        check(detail.price,76);
        check(detail.callNoClass,"I242.47");
        check(detail.holdings.size(),2);
        check(detail.available,0);        // 在编 -> 不可借
        check(detail.totalCopies(),2);
        check(detail.holdings.get(0).barcode,"694942474876");
        check(detail.holdings.get(0).lendable==false);
        check(detail.holdings.get(0).statusLevel,"processing");
        check(detail.holdings.get(0).placeText(),"馆藏地未标注具体库室");

        // 笼统值 + 有中图法分类号 -> 给出兜底推测
        check(detail.area.vagueCount,2);
        check(detail.area.hasSpecific()==false);
        check(detail.area.guess!=null);
        check(detail.area.guess.name,"社科借阅3");
        check(detail.locations().size(),1);

        // 有具体馆藏地时用实际库室，并统计可借
        String mixed="<div class=\"tit\"><h1>高等数学</h1></div>"
            +"<div class=\"catalog\"><p>中图法分类号：O13</p></div><article>"
            +tableLib("1001","自科借阅1(N-S/U-Z)(104室)","可借","O13/1349")
            +tableLib("1002","自科借阅1(N-S/U-Z)(104室)","借出","O13/1349")
            +"</article>";
        LibraryApi.Detail math=LibraryApi.parseDetail(mixed);
        check(math.available,1);
        check(math.totalCopies(),2);
        check(math.area.hasSpecific());
        check(math.area.guess==null);      // 有实际位置就不推测
        check(math.area.rooms.get(0).place.room,"104室");
        check(math.area.rooms.get(0).available,1);
        check(math.holdings.get(0).placeText(),"1楼 104室");

        searchTypePicker();
    }

    /**
     * 检索途径选择器：每个 key 都要能在选择器和对话里显示成中文，
     * 且与站点认识的 searchtype 取值一一对应。
     */
    static void searchTypePicker(){
        check(LibraryActivity.typeLabel("title"),"题名");
        check(LibraryActivity.typeLabel("keyword"),"主题词");
        check(LibraryActivity.typeLabel("author"),"著者");
        check(LibraryActivity.typeLabel("isbn"),"标准编码");
        // 未知 key 退回默认，界面不能显示空白
        check(LibraryActivity.typeLabel("nonsense"),"题名");
        check(LibraryActivity.typeLabel("").isEmpty()==false);

        // 四个途径都必须同时存在于 UI 表和站点表里，否则选了也搜不对
        for(String[] entry:LibraryActivity.TYPES){
            check(LibraryApi.SEARCH_TYPES.containsKey(entry[0]));
            check(entry[1].isEmpty()==false);
            check(LibraryActivity.typeLabel(entry[0]).equals(entry[1]));
        }
        check(LibraryActivity.TYPES.length,LibraryApi.SEARCH_TYPES.size());

        // 选项文字必须放得进整行的选择器（原来四等分时「标准编码」放不下）
        for(String[] entry:LibraryActivity.TYPES){
            String onPicker="检索途径："+entry[1];
            check(onPicker.length()<=14);   // 324dp 下 16sp 足够
        }

        // 控件高度要够点按，但不能像 112dp 那样占掉半个屏
        check(LibraryActivity.VIEW_HEIGHT>=48);
        check(LibraryActivity.VIEW_HEIGHT<=64);

        // 控件尺寸放大，但字号**不**放大：font() 必须原样返回
        check(LibraryActivity.SCALE>1.0);                      // 尺寸确实放大了
        check(LibraryActivity.SCALE<=1.3);                     // 但不过头
        for(int base:new int[]{12,13,14,16,20,21}){
            check(LibraryActivity.font(base)==base);           // 字号不得被缩放
            check(LibraryActivity.scaled(base)>base);          // 尺寸确实放大
        }
        // 尺寸换算的实际值
        check(LibraryActivity.scaled(12),14);
        check(LibraryActivity.scaled(13),15);
        check(LibraryActivity.scaled(14),16);
        check(LibraryActivity.scaled(16),18);
        check(LibraryActivity.scaled(20),23);
        check(LibraryActivity.scaled(21),24);
        check(LibraryActivity.VIEW_HEIGHT,LibraryActivity.scaled(54));
        // 控件高度够点按，又不占掉半个屏
        check(LibraryActivity.VIEW_HEIGHT>=48&&LibraryActivity.VIEW_HEIGHT<=72);
        check(LibraryActivity.scaled(42)>=46);                 // 展开按钮高度
        check(LibraryActivity.scaled(44)>=48);                 // 返回键点击区
        // 放大的是尺寸而不是文字：二者必须解耦
        check(LibraryActivity.font(16)!=LibraryActivity.scaled(16));

        // 检索条与下拉菜单：按 1.5 倍放大，且两行之间留出明显间距
        check(LibraryActivity.SEARCH_HEIGHT>LibraryActivity.VIEW_HEIGHT);
        check(LibraryActivity.PICKER_HEIGHT>LibraryActivity.VIEW_HEIGHT);
        check(LibraryActivity.SEARCH_HEIGHT,LibraryActivity.scaled(81));
        check(LibraryActivity.PICKER_HEIGHT,LibraryActivity.scaled(81));
        // 相对标准控件确实是 1.5 倍（基准 54 -> 81）
        check(LibraryActivity.scaled(81),LibraryActivity.scaled(54)*3/2);
        check(LibraryActivity.ROW_GAP,LibraryActivity.scaled(26));
        // 间距要足够明显：加上检索条自身下边距后，必须达标准间距的三倍以上
        check(LibraryActivity.ROW_GAP+LibraryActivity.scaled(10)
            >=LibraryActivity.scaled(10)*3);
        // 下拉菜单点开后的选项高度与菜单本身一致，观感统一
        check(LibraryActivity.PICKER_HEIGHT<=LibraryActivity.VIEW_HEIGHT*2);
        // 放大后顶部两行加起来仍不能吃掉整个结果区
        int topUsed=LibraryActivity.SEARCH_HEIGHT+LibraryActivity.ROW_GAP+LibraryActivity.PICKER_HEIGHT;
        check(topUsed<300);
        // 两行控件加间距后不应超过 640dp 屏的一半
        check(topUsed<640/2);

        // 馆藏明细的框：小巧紧凑（一册只有几行短文字，不必占用太多高度）
        check(LibraryActivity.HOLDING_PAD_H>0);
        check(LibraryActivity.HOLDING_PAD_V>0);
        check(LibraryActivity.HOLDING_PAD_H,LibraryActivity.scaled(12));
        check(LibraryActivity.HOLDING_PAD_V,LibraryActivity.scaled(9));
        check(LibraryActivity.HOLDING_GAP,LibraryActivity.scaled(6));
        // 内边距不能小到文字贴边
        check(LibraryActivity.HOLDING_PAD_V>=8);
        check(LibraryActivity.HOLDING_PAD_H>=10);
        // 且必须比结果卡片更紧凑（卡片是整本书的信息块，明细行只是一册）
        check(LibraryActivity.HOLDING_PAD_H<LibraryActivity.CARD_PAD_H);
        check(LibraryActivity.HOLDING_PAD_V<LibraryActivity.CARD_PAD_V);
        // 圆角不能超过框高的一半，否则变成胶囊形
        check(LibraryActivity.HOLDING_RADIUS<=LibraryActivity.HOLDING_PAD_V*2);

        // 字号一律走 font()，不得混入 scaled()
        for(int base:new int[]{12,13,14,16})check(LibraryActivity.font(base)==base);

        // 底部翻页按钮也做大，好点按；但不能超过检索控件，免得底部压过顶部
        check(LibraryActivity.PAGER_HEIGHT>LibraryActivity.VIEW_HEIGHT);
        check(LibraryActivity.PAGER_HEIGHT,LibraryActivity.scaled(70));
        check(LibraryActivity.PAGER_HEIGHT<=LibraryActivity.SEARCH_HEIGHT);
        check(LibraryActivity.PAGER_HEIGHT>=48);              // 最小触控高度
        check(LibraryActivity.PAGER_GAP>0);
        // 顶部 + 底部固定区不能把结果区挤没
        int fixed=LibraryActivity.SEARCH_HEIGHT+LibraryActivity.ROW_GAP+LibraryActivity.PICKER_HEIGHT
            +LibraryActivity.PAGER_HEIGHT;
        check(fixed<640-200);                                 // 结果区至少留 200dp

        // 「查看馆藏明细」按钮：比标准控件大，但不能大过搜索框
        check(LibraryActivity.TRIGGER_HEIGHT>LibraryActivity.VIEW_HEIGHT);
        check(LibraryActivity.TRIGGER_HEIGHT,LibraryActivity.scaled(58));
        check(LibraryActivity.TRIGGER_HEIGHT<=LibraryActivity.SEARCH_HEIGHT);
        check(LibraryActivity.TRIGGER_HEIGHT>=48);             // 最小触控高度
        check(LibraryActivity.TRIGGER_GAP>0);
        // 按钮只有结果卡片内宽那么宽，文字必须放得下
        int cardInner=360-12*2-LibraryActivity.CARD_PAD_H*2;
        check(LibraryActivity.font(16)*6<=cardInner);          // 「查看馆藏明细」6 字 @16sp
    }

    static String tableLib(String barcode,String location,String status,String callNo){
        return "<div class=\"tableLib\"><div class=\"tableCon\"><table>"
            +row("年卷期","")+row("exposure","false")+row("条码号",barcode)
            +row("定位信息","导航")+row("馆藏地",location)+row("书刊状态",status)
            +row("索书号",callNo)+row("还书位置","")
            +"</table></div></div>";
    }
    static String row(String key,String value){return "<tr><th>"+key+"</th><td>"+value+"</td></tr>";}

    /** 分页与终止条件。 */
    static void paging(){
        check(LibraryApi.pageCount(0),1);
        check(LibraryApi.pageCount(20),1);
        check(LibraryApi.pageCount(21),2);
        check(LibraryApi.pageCount(550),28);   // 实测红楼梦 550 条 / 28 页
        check(LibraryApi.PAGE_SIZE,20);

        // 检索途径是站点识别的固定取值
        check(LibraryApi.SEARCH_TYPES.size(),4);
        check(LibraryApi.SEARCH_TYPES.get("title"),"题名");
        check(LibraryApi.SEARCH_TYPES.containsKey("keyword"));
        check(LibraryApi.SEARCH_TYPES.containsKey("author"));
        check(LibraryApi.SEARCH_TYPES.containsKey("isbn"));

        check(LibraryApi.ORIGIN,"http://mob.lib.jhun.edu.cn");
        check(LibraryApi.SCHOOL_ID,"1604");
        check(LibraryApi.XC,"3");

        pagingBounds();
    }

    /**
     * 翻页按钮的可用性判定。没有设备时这段逻辑靠这里的边界值覆盖：
     * 第 1 页不能再上一页，末页不能再下一页，中间页两边都可点。
     */
    static void pagingBounds(){
        // 首页：只能往后
        check(canPrev(1)==false);
        check(canNext(1,28));

        // 中间页：两边都能点
        check(canPrev(2));
        check(canNext(2,28));
        check(canPrev(14));
        check(canNext(14,28));

        // 末页：只能往前
        check(canPrev(28));
        check(canNext(28,28)==false);

        // 只有一页时两个按钮都不可用
        check(canPrev(1)==false);
        check(canNext(1,1)==false);

        // 页码越界时按钮状态不得失灵
        check(canPrev(0)==false);
        check(canNext(0,28));
        check(canNext(29,28)==false);

        // 第 2 页之后，结果序号应接着第 1 页数下去（1..20, 21..40）
        check(serialOf(1,1),1);
        check(serialOf(1,20),20);
        check(serialOf(2,1),21);
        check(serialOf(2,20),40);
        check(serialOf(28,1),541);   // 实测末页 10 条, 541..550
        check(serialOf(28,10),550);

        // 末尾不足一页时, 序号不得超出总数
        check(serialOf(28,10)<=550);
    }

    /** 与 LibraryActivity.updatePager 使用同一套判定，避免测试与实现各写一份。 */
    static boolean canPrev(int page){return LibraryActivity.canPrev(page);}
    static boolean canNext(int page,int pages){return LibraryActivity.canNext(page,pages);}
    /** 与 LibraryActivity.renderPage 使用同一公式。 */
    static int serialOf(int page,int indexInPage){return LibraryActivity.serialOf(page,indexInPage);}
}
