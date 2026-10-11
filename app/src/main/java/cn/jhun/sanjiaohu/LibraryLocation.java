package cn.jhun.sanjiaohu;

import java.util.*;

/**
 * 馆藏区域解析（移植自 library-query/location.mjs）。
 *
 * 站点不提供结构化区位数据：详情页的「定位信息」恒为「导航」二字（按钮文字），
 * dw() 导航链接指向大连外国语大学的域名，对本校无效。
 * 因此区域只能从「馆藏地」文本解析，必要时用索书号（中图法）推断。
 *
 * 实测取样的馆藏地取值（格式不统一，半角/全角括号混用）：
 *   自科借阅1(N-S/U-Z)(104室)
 *   自科借阅2(T)(113室)
 *   社科借阅1(A-C/E-G)(301-302室)
 *   社科借阅2(DH)(303室)
 *   社科借阅3(IK)(304-305室)
 *   中文过刊阅览室（403室）
 *   自科典藏库（5楼）
 *   江大文库201
 *   江汉大学图书馆        <- 笼统值，无区域信息（实测占相当比例）
 */
final class LibraryLocation {
    private LibraryLocation(){}

    /** 一个库室的静态信息。 */
    static final class Room {
        final String name,room,classes,kind;final int floor;
        Room(String name,String room,int floor,String classes,String kind){this.name=name;this.room=room;this.floor=floor;this.classes=classes;this.kind=kind;}
    }

    /**
     * 已知库室表。key 用于归并同一库室的多种写法，classes 是该室收藏的中图法类目段。
     * 顺序无关紧要——匹配一律取最长，见 {@link #matchRoomByName}。
     *
     * 表的来源是**桌面版 OPAC 的官方配置接口**（`/opac/ajax_adv_info.php` 的
     * `locationList`，共 32 项），不是从检索结果里采样归纳——采样早期只覆盖到 9 项，漏了一大半。
     */
    static final LinkedHashMap<String,Room> ROOMS=new LinkedHashMap<>();
    static {
        // ---- 主要借阅区 ----
        ROOMS.put("社科借阅1",new Room("社科借阅1","301-302室",3,"A-C / E-G","借阅"));
        ROOMS.put("社科借阅2",new Room("社科借阅2","303室",3,"D-H","借阅"));
        ROOMS.put("社科借阅3",new Room("社科借阅3","304-305室",3,"I / K","借阅"));
        // J 类（艺术）实测单列一室，与 304-305 的 I/K 不同。见 guessRoom 的注释。
        ROOMS.put("社科借阅",new Room("社科借阅","405室",4,"J","借阅"));
        ROOMS.put("自科借阅1",new Room("自科借阅1","104室",1,"N-S / U-Z","借阅"));
        ROOMS.put("自科借阅2",new Room("自科借阅2","113室",1,"T","借阅"));
        ROOMS.put("新书借阅室",new Room("新书借阅室","",0,"","借阅"));

        // ---- 阅览室 ----
        ROOMS.put("中文过刊阅览室",new Room("中文过刊阅览室","403室",4,"期刊","阅览"));
        ROOMS.put("外文图书阅览室",new Room("外文图书阅览室","401室",4,"","阅览"));
        ROOMS.put("外文图书样本库",new Room("外文图书样本库","401室",4,"","阅览"));
        ROOMS.put("外文报刊阅览室",new Room("外文报刊阅览室","",0,"","阅览"));
        ROOMS.put("社科期刊阅览室",new Room("社科期刊阅览室","",0,"","阅览"));
        ROOMS.put("自科报刊阅览室",new Room("自科报刊阅览室","",0,"","阅览"));
        ROOMS.put("特藏阅览室",new Room("特藏阅览室","",0,"","阅览"));

        // ---- 典藏库 ----
        ROOMS.put("社科典藏库",new Room("社科典藏库","5楼",5,"","典藏"));
        ROOMS.put("自科典藏库",new Room("自科典藏库","5楼",5,"","典藏"));
        ROOMS.put("N-TM自科样本",new Room("N-TM自科样本","",0,"","典藏"));
        ROOMS.put("剔旧图书临时书库",new Room("剔旧图书临时书库","909",9,"","典藏"));

        // ---- 其他 ----
        ROOMS.put("港台版图书",new Room("港台版图书","",0,"","特藏"));
        ROOMS.put("江大文库",new Room("江大文库","201室",2,"","文库"));
        ROOMS.put("城市研究中心",new Room("城市研究中心","713室",7,"","资料室"));
        ROOMS.put("文理学院图书馆",new Room("文理学院图书馆","",0,"","分馆"));
        ROOMS.put("艺术学院图书馆",new Room("艺术学院图书馆","",0,"","分馆"));
        ROOMS.put("设计学院资料室",new Room("设计学院资料室","",0,"","资料室"));
        ROOMS.put("江汉大学实验中学",new Room("江汉大学实验中学","",0,"","分馆"));
        ROOMS.put("图书馆咨询台",new Room("图书馆咨询台","",0,"","服务台"));
        ROOMS.put("图书馆网络部",new Room("图书馆网络部","",0,"","行政"));
        ROOMS.put("招生就业处",new Room("招生就业处","师活中心324",3,"","院系"));
        ROOMS.put("外事处智慧书屋",new Room("外事处智慧书屋","",0,"","院系"));
        ROOMS.put("社会合作与校友工作处",new Room("社会合作与校友工作处","",0,"","院系"));
        ROOMS.put("武汉政协智库信息中心图书资料室",new Room("武汉政协智库信息中心图书资料室","",0,"","院系"));
        ROOMS.put("武汉政协智库信息中心期刊室",new Room("武汉政协智库信息中心期刊室","",0,"","院系"));
        // ---- 官方 32 项清单**没有**、但实测馆藏地里确实出现的库室 ----
        // 实测抽样（8 个学科 × 各 20 本）中出现频率还不低：
        //   样本密集书库 143 次、过刊密集库 81 次、架空层仓库 6 次、
        //   武汉语言文化研究中心 6 次、汉口校区分馆 5 次
        // 说明「官方清单」是配置项而非实际取值全集，两者都要以实测为准。
        ROOMS.put("样本密集书库",new Room("样本密集书库","",0,"","典藏"));
        ROOMS.put("过刊密集库",new Room("过刊密集库","",0,"","典藏"));
        ROOMS.put("架空层仓库",new Room("架空层仓库","",0,"","典藏"));
        ROOMS.put("武汉语言文化研究中心",new Room("武汉语言文化研究中心","",0,"","资料室"));
        ROOMS.put("汉口校区分馆",new Room("汉口校区分馆","",0,"","分馆"));
        // 桌面版 locationFacet（实际在用的取值）比官方配置清单更全，以下也只在 facet 里出现
        ROOMS.put("流通密集库",new Room("流通密集库","",0,"","典藏"));
        ROOMS.put("图书馆流通部",new Room("图书馆流通部","",0,"","行政"));
        ROOMS.put("捐赠图书201",new Room("捐赠图书201","201室",2,"","文库"));
        ROOMS.put("教参阅览室",new Room("教参阅览室","",0,"","阅览"));
        ROOMS.put("江大园离退休",new Room("江大园离退休","",0,"","院系"));
        ROOMS.put("外语学院资料室",new Room("外语学院资料室","",0,"","资料室"));
        ROOMS.put("法学院资料室",new Room("法学院资料室","",0,"","资料室"));
        ROOMS.put("武汉城市文化文献709",new Room("武汉城市文化文献709","709室",7,"","资料室"));
        // 官方清单里写作「社科借阅(J类)(405室)」，归到同一个 405 室
        ROOMS.put("社科借阅(J类)",new Room("社科借阅","405室",4,"J","借阅"));
    }

    /** 馆藏地只写馆名、不含库室信息时的取值（实测确实存在，且不在少数）。 */
    static final String VAGUE="江汉大学图书馆";

    /** 只写了馆名、没有任何区位线索的值。 */
    private static final Set<String> VAGUE_NAMES=new HashSet<>(Arrays.asList(
        "江汉大学图书馆","图书馆","本校图书馆"));

    /**
     * 无法定位的占位值。实测出现在**桌面版**的馆藏地字段里
     * （同一本书移动版给的是正确库室名，桌面版给这些）：
     *   "304--113"   69 册  疑似内部代码
     *   "102"        44 册  纯数字
     *   "不定馆藏地"  19 册  占位文字
     * 这类值**不可用来推断位置**——猜错比不猜更糟，一律当「无区位」处理。
     */
    private static final Set<String> NON_LOCATION=new HashSet<>(Arrays.asList(
        "不定馆藏地","不详","未知","无"));

    /**
     * 判断一个馆藏地值是否是可用的区位信息。
     * 只写馆名的笼统值算「可用文本但无区位」，由调用方按 vague 处理。
     */
    static boolean isUsableLocation(String raw){
        String s=trim(raw);
        if(s.isEmpty())return false;
        if(VAGUE_NAMES.contains(s))return false;
        if(NON_LOCATION.contains(s))return false;
        // 纯数字/横线/点组成的，是代码而不是名称
        return !s.matches("^[\\d\\-–—.\\s]+$");
    }

    /** 单条馆藏地的解析结果。 */
    static final class Place {
        final String raw,name,room,classes,kind;final int floor;final boolean known,vague;
        /**
         * 标注了内容但无法当成区位用（桌面版偶见的 "304--113" / "102" 这类代码值）。
         * 与「只写馆名」的正常笼统值区分开，便于上层给出不同提示。
         */
        final boolean unusable;
        Place(String raw,String name,String room,int floor,String classes,String kind,boolean known,boolean vague){
            this(raw,name,room,floor,classes,kind,known,vague,false);
        }
        Place(String raw,String name,String room,int floor,String classes,String kind,boolean known,boolean vague,boolean unusable){
            this.raw=raw;this.name=name;this.room=room;this.floor=floor;this.classes=classes;this.kind=kind;this.known=known;this.vague=vague;this.unusable=unusable;
        }
        /** 「3楼 304-305室」这样的一行位置说明；没有楼层或室号时返回空串。 */
        String describe(){
            StringBuilder text=new StringBuilder();
            if(floor>0)text.append(floor).append("楼");
            if(!room.isEmpty()){if(text.length()>0)text.append(' ');text.append(room);}
            return text.toString();
        }
    }

    static String trim(String value){return value==null?"":value.trim();}

    /**
     * 解析一条馆藏地文本。
     * 全角/半角括号混用是实测的常态，先统一再匹配。
     */
    static Place parseLocation(String raw){
        String s=trim(raw);

        // 不可用的值：当「无区位」处理，但保留原文以便展示。
        //   vague 且 !unusable -> 只写了馆名 / 空值（正常数据）
        //   vague 且  unusable -> 写了但无法解析（桌面版的代码值，数据异常）
        if(!isUsableLocation(s)){
            boolean unusable=!s.isEmpty()&&!VAGUE_NAMES.contains(s);
            return new Place(s,s.isEmpty()?"未知":s,"",0,"","",false,true,unusable);
        }

        // 统一全角括号，便于后续匹配
        String norm=s.replace('（','(').replace('）',')');

        // 名称：取括号前的部分
        int cut=norm.indexOf('(');
        String name=(cut>=0?norm.substring(0,cut):norm).trim();
        if(name.isEmpty())name=norm;

        // 室号：优先取括号内含「室/楼」的片段
        String room="";
        java.util.regex.Matcher roomMatch=java.util.regex.Pattern.compile("\\(([^)]*(?:室|楼)[^)]*)\\)").matcher(norm);
        if(roomMatch.find())room=roomMatch.group(1);
        else{
            // 无括号时，名称末尾的数字可能就是室号，如「江大文库201」
            java.util.regex.Matcher tail=java.util.regex.Pattern.compile("(\\d{3,4})\\s*室?$").matcher(norm);
            if(tail.find())room=tail.group(1)+"室";
        }

        // 楼层：从室号推断。三种写法实测都有：
        //   104室 / 304-305室  -> 首位数字即楼层
        //   5楼                -> 数字即楼层
        //   图书馆713室         -> 前面有非数字前缀，取「室」前的数字再取首位
        int floor=0;
        java.util.regex.Matcher lead=java.util.regex.Pattern.compile("^(\\d)").matcher(room);
        if(lead.find())floor=Integer.parseInt(lead.group(1));
        else{
            java.util.regex.Matcher stored=java.util.regex.Pattern.compile("(\\d+)\\s*楼").matcher(room);
            if(stored.find())floor=Integer.parseInt(stored.group(1));
            else{
                java.util.regex.Matcher prefixed=java.util.regex.Pattern.compile("(\\d{3,4})\\s*室").matcher(room);
                if(prefixed.find())floor=prefixed.group(1).charAt(0)-'0';
            }
        }

        // 分类段：名称后紧跟的纯类目括号，如 (N-S/U-Z) (IK) (A-C/E-G)
        String classes="";
        java.util.regex.Matcher classMatch=java.util.regex.Pattern.compile("\\(([A-Z][A-Z\\s/\\-]*)\\)(?!\\s*室)").matcher(norm);
        if(classMatch.find())classes=classMatch.group(1).trim();

        String hit=ROOMS.containsKey(name)?name:matchRoomByName(name);
        Room meta=hit==null?null:ROOMS.get(hit);
        return new Place(
            s,
            hit!=null?hit:name,
            !room.isEmpty()?room:(meta!=null?meta.room:""),
            floor>0?floor:(meta!=null?meta.floor:0),
            !classes.isEmpty()?classes:(meta!=null?meta.classes:""),
            meta!=null?meta.kind:"",
            meta!=null||!room.isEmpty(),
            false);
    }

    /**
     * 名称里含室号等后缀时退化为前缀匹配，如「江大文库201」->「江大文库」。返回 key。
     *
     * 必须取最长匹配：「社科借阅1」同时以「社科借阅」开头，短匹配会把它错认成 405 室。
     * （参考实现曾踩过这个坑。）
     */
    static String matchRoomByName(String name){
        String best=null;
        for(String key:ROOMS.keySet()){
            if(name.startsWith(key)&&(best==null||key.length()>best.length()))best=key;
        }
        return best;
    }

    /** 索书号/中图法分类号的首字母（大类）。 */
    static String classLetter(String callNo){
        String s=trim(callNo);
        if(s.isEmpty())return "";
        char c=s.charAt(0);
        return c>='A'&&c<='Z'?String.valueOf(c):"";
    }

    /** 由中图法分类号推断应去的库室。 */
    static final class Guess {
        final String name,room,note;final int floor;
        Guess(String name,String room,int floor,String note){this.name=name;this.room=room;this.floor=floor;this.note=note;}
    }

    /**
     * 由中图法分类号推断应去的库室。**仅在馆藏地是笼统值时的兜底推测**，不一定准：
     * 期刊（中文过刊阅览室）、文库（江大文库）等特藏不走中图法分类，会误判。
     *
     * 依据：实测索书号首字母与馆藏地分类段严格对应
     *   O13  -> 自科借阅1(N-S/U-Z)
     *   I207 -> 社科借阅3(IK)      注意：J 类单列 405 室，不并进 I/K
     *   H310 -> 社科借阅2(DH)
     *   F0   -> 社科借阅1(A-C/E-G)
     *   J218 -> 社科借阅(405室)
     */
    static Guess guessRoom(String callNo){
        String letter=classLetter(callNo);
        if(letter.isEmpty())return null;
        char L=letter.charAt(0);

        if(L=='A'||(L>='B'&&L<='C')||(L>='E'&&L<='G'))return pick("社科借阅1","中图法 A-C / E-G");
        if(L>='D'&&L<='H')return pick("社科借阅2","中图法 D-H");
        // J 类（艺术）实测在独立的 405 室，不并进 304-305 的 I/K
        if(L=='J')return pick("社科借阅","中图法 J");
        if(L=='I'||L=='K')return pick("社科借阅3","中图法 I / K");
        if(L=='T')return pick("自科借阅2","中图法 T");
        if((L>='N'&&L<='S')||(L>='U'&&L<='Z'))return pick("自科借阅1","中图法 N-S / U-Z");
        return null;
    }

    private static Guess pick(String name,String note){
        Room meta=ROOMS.get(name);
        return new Guess(name,meta.room,meta.floor,note);
    }

    /** 一册馆藏的区位归并结果。 */
    static final class Summary {
        final List<Bucket> rooms;final int vagueCount;final Guess guess;
        /** 标注了但无法解析的馆藏地原文（桌面版偶见 "304--113" 这类代码值）。 */
        final List<String> unusableLocations;
        Summary(List<Bucket> rooms,int vagueCount,Guess guess,List<String> unusableLocations){
            this.rooms=rooms;this.vagueCount=vagueCount;this.guess=guess;this.unusableLocations=unusableLocations;
        }
        boolean hasSpecific(){return !rooms.isEmpty();}
    }

    /** 同一库室的归并计数。 */
    static final class Bucket {
        final Place place;int total;int available;
        Bucket(Place place){this.place=place;}
    }

    /**
     * 汇总一批馆藏的区位。
     * @param locations 各册的馆藏地原文
     * @param lendable  与 locations 等长，标记该册是否可借
     * @param callNoClass 中图法分类号，用于笼统值兜底
     */
    static Summary summarizeLocation(List<String> locations,boolean[] lendable,String callNoClass){
        LinkedHashMap<String,Bucket> byRoom=new LinkedHashMap<>();
        int vagueCount=0;
        Set<String> unusable=new LinkedHashSet<>();
        for(int i=0;i<locations.size();i++){
            Place place=parseLocation(locations.get(i));
            if(place.vague){
                vagueCount++;
                // 标注了但不可用（如 "304--113" / "102"）——记下原文便于提示。
                // 只写馆名的笼统值不算异常，不收进来。
                if(place.unusable&&!place.raw.isEmpty())unusable.add(place.raw);
                continue;
            }
            String key=place.name+"|"+place.room;
            Bucket bucket=byRoom.get(key);
            if(bucket==null){bucket=new Bucket(place);byRoom.put(key,bucket);}
            bucket.total++;
            if(lendable!=null&&i<lendable.length&&lendable[i])bucket.available++;
        }

        List<Bucket> rooms=new ArrayList<>(byRoom.values());
        // 楼层升序；解析不出楼层的排到最后（与参考实现的 ?? 99 一致）
        Collections.sort(rooms,(a,b)->Integer.compare(a.place.floor>0?a.place.floor:99,b.place.floor>0?b.place.floor:99));

        // 笼统值兜底：没有具体库室时，用中图法推断大概该去哪
        Guess guess=null;
        if(vagueCount>0&&rooms.isEmpty()&&!trim(callNoClass).isEmpty())guess=guessRoom(callNoClass);

        return new Summary(rooms,vagueCount,guess,new ArrayList<>(unusable));
    }

    /**
     * 判断一批馆藏里是否有落在指定库室的。
     * 支持室号（104 / 304 / 405）、名称（自科 / 社科 / 文库）以及全角括号。
     */
    static boolean inRoom(List<String> locations,String query){
        String q=trim(query).replace('（','(').replace('）',')');
        if(q.isEmpty())return false;
        for(String location:locations){
            Place place=parseLocation(location);
            if(place.vague)continue;
            if((place.name+place.room+trim(location)).contains(q))return true;
        }
        return false;
    }
}
