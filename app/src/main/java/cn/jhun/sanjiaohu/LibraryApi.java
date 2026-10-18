package cn.jhun.sanjiaohu;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * 汇文 libsys 短信版 OPAC 抓取库（移植自 library-query/lib.mjs）。
 *
 * 目标：江汉大学图书馆 &lt;http://mob.lib.jhun.edu.cn&gt; schoolid=1604。
 * 无鉴权、只读。仅覆盖检索/详情/馆藏查询，不涉及借阅类写操作。
 *
 * 链路：
 *   1. GET  /search/searchList?kw=&amp;xc=&lt;xc&gt;&amp;schoolid=&lt;id&gt;&amp;searchtype=&amp;pageIndex=
 *   2. POST /search/bookDetail   body: detailParam + xc + schoolid
 *
 * 注意：两个接口都返回「XML 声明 + &lt;!doctype html&gt;」的畸形头，且不是 JSON API，
 *      只能解析 HTML。全部用正则解析，刻意不引第三方 HTML 库。
 */
final class LibraryApi {
    private LibraryApi(){}

    static final String ORIGIN="http://mob.lib.jhun.edu.cn";
    static final String SCHOOL_ID="1604";
    static final String XC="3";

    /** 站点每页条数固定 20，没有可用的 pageSize 参数（实测被忽略）。 */
    static final int PAGE_SIZE=20;

    /** 响应体积上限，防止异常大响应拖垮内存。 */
    private static final int MAX_BYTES=1024*1024;

    /** 检索途径。key 对应页面上的 searchtype 值。 */
    static final LinkedHashMap<String,String> SEARCH_TYPES=new LinkedHashMap<>();
    static {
        SEARCH_TYPES.put("title","题名");
        SEARCH_TYPES.put("keyword","主题词");
        SEARCH_TYPES.put("author","著者");
        SEARCH_TYPES.put("isbn","标准编码");
    }

    // 站点没有可用的桌面 UA 支持，移动 UA 是实测可用的那个
    private static final String UA="Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) "
        +"AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1";

    private static final int CONNECT_TIMEOUT=12000;
    private static final int READ_TIMEOUT=18000;
    private static final int RETRIES=2;

    /** 查询被主动取消（用户离开页面）。 */
    static final class Cancelled extends IOException {Cancelled(){super("查询已取消");}}

    /* ------------------------------------------------------------------ */
    /* HTML 小工具                                                        */
    /* ------------------------------------------------------------------ */

    private static final Map<String,String> ENTITIES=new HashMap<>();
    static {
        ENTITIES.put("&amp;","&");ENTITIES.put("&lt;","<");ENTITIES.put("&gt;",">");
        ENTITIES.put("&quot;","\"");ENTITIES.put("&#39;","'");ENTITIES.put("&apos;","'");ENTITIES.put("&nbsp;"," ");
    }

    /** 还原 HTML 实体 + 去标签 + 压空白。顺序要紧：先去标签再还原实体。 */
    static String clean(String html){
        if(html==null)return "";
        String text=html.replaceAll("<[^>]*>","");
        // 数字实体（十进制与十六进制）
        Matcher numeric=Pattern.compile("&#(\\d+);").matcher(text);
        StringBuffer buffer=new StringBuffer();
        while(numeric.find()){
            int code=Integer.parseInt(numeric.group(1));
            numeric.appendReplacement(buffer,Matcher.quoteReplacement(code>0&&code<=0x10ffff?new String(Character.toChars(code)):""));
        }
        numeric.appendTail(buffer);
        text=buffer.toString();
        Matcher hex=Pattern.compile("&#[xX]([0-9a-fA-F]+);").matcher(text);
        buffer=new StringBuffer();
        while(hex.find()){
            int code=Integer.parseInt(hex.group(1),16);
            hex.appendReplacement(buffer,Matcher.quoteReplacement(code>0&&code<=0x10ffff?new String(Character.toChars(code)):""));
        }
        hex.appendTail(buffer);
        text=buffer.toString();
        for(Map.Entry<String,String> entry:ENTITIES.entrySet())text=text.replace(entry.getKey(),entry.getValue());
        return text.replaceAll("\\s+"," ").trim();
    }

    /** 取 hidden input 的 value。汇文用单引号包属性值。 */
    static String hiddenValue(String block,String name){
        // 常规形态：name 在前。属性值可能含双引号（detailParam 是 JSON），
        // 所以单引号形式必须优先，且不能把值里的双引号当成结束符。
        Matcher quoted=Pattern.compile("name=\""+Pattern.quote(name)+"\"[^>]*?\\svalue='([^']*)'",Pattern.CASE_INSENSITIVE).matcher(block);
        if(quoted.find())return quoted.group(1);
        Matcher doubleQuoted=Pattern.compile("name=\""+Pattern.quote(name)+"\"[^>]*?\\svalue=\"([^\"]*)\"",Pattern.CASE_INSENSITIVE).matcher(block);
        if(doubleQuoted.find())return doubleQuoted.group(1);
        // 兜底：属性顺序颠倒（value 在 name 之前）。用单个 <input> 标签做范围，避免跨标签误配。
        Matcher inputs=Pattern.compile("<input\\b[^>]*>",Pattern.CASE_INSENSITIVE).matcher(block);
        while(inputs.find()){
            String tag=inputs.group();
            if(!Pattern.compile("name=\""+Pattern.quote(name)+"\"",Pattern.CASE_INSENSITIVE).matcher(tag).find())continue;
            Matcher value=Pattern.compile("value=(?:'([^']*)'|\"([^\"]*)\")",Pattern.CASE_INSENSITIVE).matcher(tag);
            if(value.find())return value.group(1)!=null?value.group(1):(value.group(2)!=null?value.group(2):"");
        }
        return "";
    }

    /**
     * JSON 字符串取值。站点把 detailParam 作为 JSON 串返回，这里只取需要的 ASCII 字段。
     * 刻意不解析整个 JSON——只需 marc_no，且引号/转义形式实测固定。
     */
    static String jsonString(String json,String key){
        if(json==null)return "";
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find()?m.group(1):"";
    }

    /** 从 &lt;p&gt;标签：值&lt;/p&gt; 里抽取 key-value 对。 */
    private static LinkedHashMap<String,String> parsePairs(String html,String keyPattern){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        Matcher m=Pattern.compile("<p>([\\s\\S]*?)</p>").matcher(html);
        Pattern key=Pattern.compile(keyPattern);
        while(m.find()){
            String text=clean(m.group(1));
            if(text.isEmpty())continue;
            Matcher mm=key.matcher(text);
            if(!mm.find())continue;
            String name=mm.group(1).trim();
            String value=text.substring(mm.end()).replaceAll("^[：:]\\s*","").trim();
            if(!value.isEmpty())out.put(name,value);
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* 网络                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 带超时与重试的请求，一律按 UTF-8 读文本。
     * 站点声明 text/html;charset=utf-8 与实际不符，所以不信任 Content-Type，统一自行解码。
     */
    private static String request(String url,String method,String body)throws IOException{
        IOException last=null;
        for(int attempt=0;attempt<=RETRIES;attempt++){
            if(attempt>0)sleep(300L*attempt);
            HttpURLConnection connection=null;
            try{
                if(Thread.currentThread().isInterrupted())throw new Cancelled();
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent",UA);
                connection.setRequestProperty("Accept","text/html,application/xhtml+xml,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9");
                if(body!=null){
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                    byte[] payload=body.getBytes("UTF-8");
                    connection.setFixedLengthStreamingMode(payload.length);
                    try(OutputStream out=connection.getOutputStream()){out.write(payload);}
                }
                int status=connection.getResponseCode();
                if(status!=200)throw new IOException("图书馆服务暂时不可用（"+status+"）");
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];int count;
                    while((count=in.read(chunk))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new Cancelled();
                        out.write(chunk,0,count);
                        if(out.size()>MAX_BYTES)throw new IOException("图书馆返回内容过大");
                    }
                    return out.toString("UTF-8");
                }
            }catch(Cancelled cancelled){
                throw cancelled; // 主动取消不重试
            }catch(IOException error){
                last=error;
            }finally{
                if(connection!=null)connection.disconnect();
            }
        }
        throw new IOException("无法连接图书馆服务，请检查网络后重试",last);
    }

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new RuntimeException(new Cancelled());}
    }

    /* ------------------------------------------------------------------ */
    /* 列表页                                                             */
    /* ------------------------------------------------------------------ */

    /** 由总条数算总页数。 */
    static int pageCount(int total){
        return Math.max(1,(int)Math.ceil(Math.max(0,total)/(double)PAGE_SIZE));
    }

    /** 命中书刊状态的语义分级。「可借」类之外一律视为不可借。 */
    static final class Status {
        final String level,label;final boolean lendable;
        Status(String level,String label,boolean lendable){this.level=level;this.label=label;this.lendable=lendable;}
    }

    /**
     * 归一化书刊状态。
     * 库中取值未穷举，未知值原样透传而不是猜成「可借」——宁可漏判也不误报。
     */
    static Status classifyStatus(String raw){
        String s=trim(raw);
        if(s.isEmpty())return new Status("unknown","",false);
        // 「——」是站点用来占位的空值，不是状态
        if(s.equals("——")||s.equals("--")||s.equals("-"))return new Status("unknown","",false);
        if(s.equals("可借")||s.equals("在架"))return new Status("available",s,true);
        if(s.equals("在编")||s.equals("编目中"))return new Status("processing",s,false);
        if(s.equals("借出")||s.equals("已借出"))return new Status("out",s,false);
        if(s.equals("阅览")||s.equals("不外借"))return new Status("reading",s,false);
        if(s.equals("遗失")||s.equals("注销"))return new Status("lost",s,false);
        // 桌面版独有的状态取值（实测抽样得到，移动版见不到）：
        if(s.equals("非可借"))return new Status("reading",s,false);
        if(s.equals("赠送出"))return new Status("given",s,false);
        if(s.equals("声明丢失"))return new Status("lost",s,false);
        if(s.equals("剔旧报废"))return new Status("lost",s,false);
        // 否定状态必须先匹配，避免「不可借」被「可借」子串抢先命中。
        if(s.contains("非可借")||s.contains("不可借"))return new Status("reading",s,false);
        if(s.contains("在编"))return new Status("processing",s,false);
        // 下架/剔旧/赠送等一律不可借
        if(s.contains("赠送")||s.contains("剔旧")||s.contains("报废")||s.contains("下架"))return new Status("lost",s,false);
        if(s.contains("丢失")||s.contains("遗失"))return new Status("lost",s,false);
        if(s.contains("可借"))return new Status("available",s,true);
        // 「借出-应还日期：2027-04-10」这类带日期的写法
        if(s.contains("借出"))return new Status("out",s,false);
        return new Status("unknown",s,false);
    }

    /** 列表页的一条结果。 */
    static final class Item {
        String title="",author="",callNo="",materialType="",publisher="",language="",marcNo="",detailParam="";
        int available=-1;    // -1 表示列表页未给出
        int totalCopies=-1;  // 注意：这是「总藏册数」汇总，与详情的 holdings 明细**同名不同义**
        boolean detailLoaded;
        String detailError="";

        /** 服务端只用 marc_no 查库；详情失败或未加载时用它作为稳定标识。 */
        String key(){return !marcNo.isEmpty()?marcNo:title+"|"+author;}
        /**
         * 「可借 / 总藏」的显示文本。
         * 数字还没拿到时提示正在读取——桌面版列表不带这两个数字，
         * 要靠后台并发补全，所以短暂出现「正在读取馆藏…」是正常的。
         */
        String availabilityText(){
            if(available<0||totalCopies<0)return "正在读取馆藏…";
            return available+" / "+totalCopies;
        }
    }

    /** 单页检索结果。 */
    static final class Page {
        int total;String kw="",type="title",route="";List<Item> items=new ArrayList<>();int page=1;int pages=1;boolean hasNext,truncated,pageReset;
    }

    /**
     * 检索图书（单页）。
     * @param kw   检索词
     * @param type {@link #SEARCH_TYPES} 的 key
     * @param page 页码，从 1 开始
     */
    static Page search(String kw,String type,int page)throws IOException{
        String word=trim(kw);
        if(word.isEmpty())throw new IOException("请输入检索词");
        if(type==null||!SEARCH_TYPES.containsKey(type))throw new IOException("检索途径无法识别");

        int current=Math.max(1,page);
        String url=ORIGIN+"/search/searchList?kw="+encode(word)
            +"&xc="+XC+"&schoolid="+SCHOOL_ID+"&searchtype="+encode(type)
            +"&pageIndex="+current;

        String html=request(url,"GET",null);

        Page result=new Page();
        Matcher total=Pattern.compile("共\\s*(\\d+)\\s*条搜索结果").matcher(html);
        result.total=total.find()?Integer.parseInt(total.group(1)):0;
        result.kw=word;
        result.type=type;
        result.page=current;
        result.items=parseListItems(html);

        int pages=0;
        // 页面底部 option 文本形如 "18/28"（当前/总页）
        Matcher pager=Pattern.compile(">\\s*(\\d+)\\s*/\\s*(\\d+)\\s*</option>").matcher(html);
        if(pager.find())pages=Integer.parseInt(pager.group(2));
        result.pages=pages>0?pages:pageCount(result.total);
        result.hasNext=current<result.pages;
        return result;
    }

    /** 解析列表页的 &lt;li&gt; 块。 */
    static List<Item> parseListItems(String html){
        List<Item> items=new ArrayList<>();
        // <li> 之间不嵌套，用非贪婪配对即可
        Matcher blocks=Pattern.compile("<li>([\\s\\S]*?)</li>").matcher(html);
        while(blocks.find()){
            String block=blocks.group(1);
            // 实测页面里 class="result" 挂在内层 <a> 上，不在 <li> 上；
            // 判据改用 hidden input：真正的结果项一定有 detailParam。
            String detailParam=clean(hiddenValue(block,"detailParam"));
            if(detailParam.isEmpty())continue;

            Item item=new Item();
            item.detailParam=detailParam;
            item.marcNo=jsonString(item.detailParam,"marc_no");

            item.title=clean(hiddenValue(block,"title"));
            if(item.title.isEmpty()){
                Matcher fallback=Pattern.compile("<div class=\"title\">([\\s\\S]*?)</div>").matcher(block);
                if(fallback.find())item.title=clean(fallback.group(1));
            }
            item.author=clean(hiddenValue(block,"author"));

            LinkedHashMap<String,String> pairs=new LinkedHashMap<>();
            Matcher paragraphs=Pattern.compile("<p>([\\s\\S]*?)</p>").matcher(block);
            while(paragraphs.find()){
                String text=clean(paragraphs.group(1));
                Matcher kv=Pattern.compile("^([^：:]{2,8})[：:](.*)$").matcher(text);
                if(kv.find())pairs.put(kv.group(1),kv.group(2).trim());
            }

            item.callNo=pairs.containsKey("索书号")?pairs.get("索书号"):"";
            item.materialType=pairs.containsKey("流通类型")?pairs.get("流通类型"):"";
            item.publisher=clean(hiddenValue(block,"publisher"));
            if(item.publisher.isEmpty()&&pairs.containsKey("出版"))item.publisher=pairs.get("出版");
            item.language=clean(hiddenValue(block,"language"));

            String ratio=pairs.containsKey("可借/总藏")?pairs.get("可借/总藏"):"";
            Matcher counts=Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(ratio);
            if(counts.find()){
                item.available=Integer.parseInt(counts.group(1));
                item.totalCopies=Integer.parseInt(counts.group(2));
            }
            items.add(item);
        }
        return items;
    }

    /* ------------------------------------------------------------------ */
    /* 详情页                                                             */
    /* ------------------------------------------------------------------ */

    /** 详情页里的一册馆藏。 */
    static final class Holding {
        String volume="",barcode="",location="",returnLocation="",callNo="",status="",exposure="";
        String statusLevel="unknown";boolean lendable;
        LibraryLocation.Place place;
        String placeText(){
            if(place==null)place=LibraryLocation.parseLocation(location);
            if(place.vague)return "馆藏地未标注具体库室";
            String detail=place.describe();
            return detail.isEmpty()?place.name:detail;
        }
    }

    /** 一本书的详情。 */
    static final class Detail {
        String title="",author="",publisher="",isbn="",callNoClass="",subject="",summary="",route="";
        int price=-1;
        List<Holding> holdings=new ArrayList<>();
        int available;
        LinkedHashMap<String,String> bib=new LinkedHashMap<>();
        LibraryLocation.Summary area;

        int totalCopies(){return holdings.size();}
        List<String> locations(){
            List<String> out=new ArrayList<>();
            for(Holding holding:holdings)if(!holding.location.isEmpty()&&!out.contains(holding.location))out.add(holding.location);
            return out;
        }
    }

    /**
     * 取单本书详情 + 馆藏明细。
     * @param target 列表项，或直接的 marc_no
     */
    static Detail detail(Object target)throws IOException{
        String marcNo=null;
        if(target instanceof String)marcNo=trim((String)target);
        else if(target instanceof Item){
            Item item=(Item)target;
            marcNo=item.marcNo;
            if(marcNo.isEmpty())marcNo=jsonString(item.detailParam,"marc_no");
        }
        if(marcNo==null||marcNo.isEmpty())throw new IOException("缺少图书标识，无法读取馆藏");

        // 站点 bug：详情页服务端以单字节字符集解码 detailParam，中文会被碾成 "?"。
        // 只回传 ASCII 的 marc_no 即可，title 一律留空——服务端只用它做回显，不参与检索。
        String detailParam="{\"marc_no\":\""+marcNo.replace("\\","\\\\").replace("\"","\\\"")+"\",\"title\":\"\"}";
        // 站点要整个 JSON 串原样回传，不要拆字段
        String body="detailParam="+encode(detailParam)+"&xc="+XC+"&schoolid="+SCHOOL_ID;
        String html=request(ORIGIN+"/search/bookDetail","POST",body);
        return parseDetail(html);
    }

    private static final String BIB_KEYS=
        "^(出版发行项|个人责任者|团体责任者|个人次要责任者|ISBN及定价|载体形态项|学科主题|中图法分类号|丛编项|题名/责任者|其它题名|提要文摘附注|使用对象附注|豆瓣简介|责任者附注|并列正题名|一般附注|内容附注|摘要附注)$";

    static Detail parseDetail(String html){
        Detail detail=new Detail();

        Matcher title=Pattern.compile("<div class=\"tit[^\"]*\">\\s*<h1>([\\s\\S]*?)</h1>").matcher(html);
        if(title.find())detail.title=clean(title.group(1));

        Matcher catalog=Pattern.compile("<div class=\"catalog\">([\\s\\S]*?)</div>").matcher(html);
        String catalogBlock=catalog.find()?catalog.group(1):"";
        detail.bib=parsePairs(catalogBlock,"^([^：:]{2,12})[：:]");

        Matcher libs=Pattern.compile("<div class=\"tableLib\">([\\s\\S]*?)(?=<div class=\"tableLib\">|</article>|$)").matcher(html);
        while(libs.find()){
            String block=libs.group(1);
            Matcher table=Pattern.compile("<div class=\"tableCon\"[^>]*>([\\s\\S]*?)</table>").matcher(block);
            if(!table.find())continue;
            LinkedHashMap<String,String> row=new LinkedHashMap<>();
            Matcher cells=Pattern.compile("<th>([\\s\\S]*?)</th>\\s*<td>([\\s\\S]*?)</td>").matcher(table.group(1));
            while(cells.find())row.put(clean(cells.group(1)),clean(cells.group(2)));
            if(row.isEmpty())continue;

            Holding holding=new Holding();
            holding.volume=value(row,"年卷期");
            holding.barcode=value(row,"条码号");
            holding.location=value(row,"馆藏地");
            holding.returnLocation=value(row,"还书位置");
            holding.callNo=value(row,"索书号");
            holding.status=value(row,"书刊状态");
            // exposure 实测在「可借」和「在编」上都是 false，不是可借标志，仅原样保留
            holding.exposure=value(row,"exposure");
            Status status=classifyStatus(holding.status);
            holding.statusLevel=status.level;
            holding.lendable=status.lendable;
            holding.place=LibraryLocation.parseLocation(holding.location);
            detail.holdings.add(holding);
        }

        for(Holding holding:detail.holdings)if(holding.lendable)detail.available++;

        detail.author=value(detail.bib,"个人责任者");
        if(detail.author.isEmpty())detail.author=value(detail.bib,"团体责任者");
        detail.publisher=value(detail.bib,"出版发行项");
        detail.callNoClass=value(detail.bib,"中图法分类号");
        detail.subject=value(detail.bib,"学科主题");
        detail.summary=value(detail.bib,"提要文摘附注");

        String isbnAndPrice=value(detail.bib,"ISBN及定价");
        Matcher isbn=Pattern.compile("([\\d-]{10,20}X?)").matcher(isbnAndPrice);
        if(isbn.find())detail.isbn=isbn.group(1);
        Matcher price=Pattern.compile("CNY\\s*([\\d.]+)").matcher(isbnAndPrice);
        if(price.find()){
            try{detail.price=(int)Math.round(Double.parseDouble(price.group(1)));}catch(NumberFormatException ignored){}
        }

        // 区位：按库室归并，含楼层/室号/分类段；笼统值用中图法兜底推测
        List<String> locations=new ArrayList<>();
        boolean[] lendable=new boolean[detail.holdings.size()];
        for(int i=0;i<detail.holdings.size();i++){
            locations.add(detail.holdings.get(i).location);
            lendable[i]=detail.holdings.get(i).lendable;
        }
        detail.area=LibraryLocation.summarizeLocation(locations,lendable,detail.callNoClass);
        return detail;
    }

    private static String value(Map<String,String> map,String key){
        String found=map.get(key);
        return found==null?"":found;
    }

    /* ------------------------------------------------------------------ */
    /* 便捷封装                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * 逐页抓取全部结果。
     *
     * 站点行为：pageIndex 超出范围不报错，而是重复返回最后一页——所以不能靠
     * 「还有下一页」无限翻，必须用总页数或「本页条数 &lt; 每页条数」来停。
     *
     * @param maxPages 0 表示不限
     * @param maxItems 0 表示不限
     */
    static Page searchAll(String kw,String type,int maxPages,int maxItems,Progress progress)throws IOException{
        Page meta=null;
        List<Item> collected=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        int page=1;
        boolean truncated=false;

        while(maxPages<=0||page<=maxPages){
            Page current=search(kw,type,page);
            if(meta==null){meta=new Page();meta.total=current.total;meta.kw=current.kw;meta.type=current.type;meta.pages=current.pages;}

            for(Item item:current.items){
                if(!seen.add(item.key()))continue;
                collected.add(item);
                if(maxItems>0&&collected.size()>=maxItems){truncated=true;break;}
            }
            if(progress!=null)progress.onPage(page,current.pages,collected.size(),current.total);

            if(truncated)break;
            // 本页不足一页 -> 已是最后一页
            if(current.items.size()<PAGE_SIZE)break;
            // 站点在越界时重复返回末页，理论上走不到这，但留个闸
            if(page>=current.pages)break;
            page++;
            sleep(250);
        }

        if(meta==null){meta=new Page();meta.kw=trim(kw);meta.type=type;}
        meta.items=collected;
        meta.truncated=truncated;
        return meta;
    }

    /** 分页与进度回调。 */
    interface Progress {
        void onPage(int page,int pages,int fetched,int total);
    }

    static String encode(String value)throws IOException{return URLEncoder.encode(value,"UTF-8");}
    static String trim(String value){return value==null?"":value.trim();}

    /** 由 marc_no 拼详情的字段说明，供界面提示用。 */
    static String targetDescription(){
        return "江汉大学图书馆 · schoolid="+SCHOOL_ID;
    }
}
