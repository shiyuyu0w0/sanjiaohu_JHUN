package cn.jhun.sanjiaohu;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/**
 * 汇文桌面版 OPAC 客户端（优先线路）。
 *
 * 站点: http://opac.lib.jhun.edu.cn:8080/opac
 * 相比移动版（{@link LibraryApi}）的优势：
 *   - 结果更全（「红楼梦」754 条 vs 550 条）
 *   - 每页可调 5~200（移动版固定 20）
 *   - 越界返回 0 条（移动版会重复返回末页）
 *   - 支持排序
 *   - 详情页没有中文编码 bug
 *
 * 链路:
 *   1. POST /opac/ajax_search_adv.php   body: JSON    -> 检索结果 JSON
 *   2. GET  /opac/ajax_item.php?marc_no=           -> 馆藏明细 HTML 表格
 *   3. GET  /opac/ajax_adv_info.php                -> 字段/馆藏地配置 JSON
 *
 * 注意: 请求体必须是 **UTF-8 编码的 JSON 字节**，Content-Type 带 charset=UTF-8。
 *      站点对中文检索词按 UTF-8 解码，用别的编码发送会得到 0 条结果。
 */
final class LibraryOpacApi {
    private LibraryOpacApi(){}

    static final String ORIGIN="http://opac.lib.jhun.edu.cn:8080";
    static final String BASE=ORIGIN+"/opac";
    static final String NAME="江汉大学图书馆";

    /** 每页条数。桌面版可调，这里用一个较大的值减少翻页次数。 */
    static final int PAGE_SIZE=20;
    /** 站点接受的最大每页条数（实测 200 有效）。 */
    static final int MAX_PAGE_SIZE=200;

    private static final int MAX_BYTES=4*1024*1024;
    /** 连接失败后线性退避的上限，配合重试避免一次抖动就放弃。 */
    private static final int CONNECT_TIMEOUT=6000;
    private static final int READ_TIMEOUT=12000;
    /**
     * 重试次数。**只对「连上了但读失败」重试**——连接类错误立即放弃。
     * 实测 flow 流量下 opac:8080 不可达时每次连接固定约 2.2 秒才失败，
     * 若再重试就会把白等时间翻倍，而结论并不会改变（该端口只对校园网开放）。
     */
    private static final int RETRIES=1;

    private static final String UA="Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 "
        +"(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    /** 桌面版检索字段码 -> 中文名。与移动版的 searchtype 取值不同。 */
    static final LinkedHashMap<String,String> FIELDS=new LinkedHashMap<>();
    static {
        FIELDS.put("02","题名");
        FIELDS.put("03","著者");
        FIELDS.put("04","主题词");
        FIELDS.put("05","标准编码");
        FIELDS.put("09","出版社");
        FIELDS.put("any","任意词");
    }

    /**
     * 把界面上的检索途径（与移动版共用 key）映射到桌面版的字段码。
     * 两边字段体系不同，这里是唯一的转换点。
     */
    static String fieldCode(String sharedKey){
        if("title".equals(sharedKey))return "02";
        if("author".equals(sharedKey))return "03";
        if("keyword".equals(sharedKey))return "04";
        if("isbn".equals(sharedKey))return "05";
        return "02";
    }

    /** 线路不可用（网络不通 / 接口异常）——此时应回退到移动版。 */
    static final class Unavailable extends IOException {
        Unavailable(String message){super(message);}
    }

    /* ------------------------------------------------------------------ */
    /* 网络                                                               */
    /* ------------------------------------------------------------------ */

    private static String request(String url,String method,String body,boolean json)throws IOException{
        IOException last=null;
        for(int attempt=0;attempt<=RETRIES;attempt++){
            if(attempt>0)sleep(250L*attempt);
            HttpURLConnection connection=null;
            try{
                if(Thread.currentThread().isInterrupted())throw new LibraryApi.Cancelled();
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent",UA);
                connection.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9");
                connection.setRequestProperty(json?"Accept":"Accept","application/json,text/javascript,*/*;q=0.8");
                if(json)connection.setRequestProperty("X-Requested-With","XMLHttpRequest");
                if(body!=null){
                    connection.setDoOutput(true);
                    // 关键：显式 UTF-8。中文检索词按站点期望的编码发送，否则查不到任何结果。
                    connection.setRequestProperty("Content-Type",
                        json?"application/json; charset=UTF-8":"application/x-www-form-urlencoded; charset=UTF-8");
                    byte[] payload=body.getBytes("UTF-8");
                    connection.setFixedLengthStreamingMode(payload.length);
                    try(OutputStream out=connection.getOutputStream()){out.write(payload);}
                }
                int status=connection.getResponseCode();
                if(status!=200)throw new Unavailable("馆藏服务返回 "+status);
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];int count;
                    while((count=in.read(chunk))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new LibraryApi.Cancelled();
                        out.write(chunk,0,count);
                        if(out.size()>MAX_BYTES)throw new IOException("馆藏返回内容过大");
                    }
                    return out.toString("UTF-8");
                }
            }catch(LibraryApi.Cancelled cancelled){
                throw cancelled;
            }catch(IOException error){
                last=error;
                // 连接类错误（主机不可达 / 拒绝 / DNS 失败 / 超时）重试也不会成功，
                // 立即放弃，避免把失败耗时翻倍。只有读超时这类偶发问题才重试。
                if(isConnectFailure(error))break;
            }finally{
                if(connection!=null)connection.disconnect();
            }
        }
        // 连不上/超时都归类为「线路不可用」，交由上层回退移动版
        throw new Unavailable("无法连接馆藏服务："+(last==null?"未知错误":last.getMessage()));
    }

    /**
     * 是否为「连不上」类错误。
     * 这类错误重试无意义（目标端口根本没开或网络不可达），且实测固定要 2.2 秒才失败。
     */
    private static boolean isConnectFailure(IOException error){
        if(error instanceof ConnectException)return true;      // 连接被拒 / 不可达
        if(error instanceof UnknownHostException)return true;   // DNS 解析失败
        if(error instanceof java.net.NoRouteToHostException)return true;
        if(error instanceof SocketTimeoutException){
            // 连接超时与读超时都是 SocketTimeoutException；连接阶段超时同样不必重试
            String message=error.getMessage();
            return message!=null&&(message.contains("connect")||message.contains("Connect"));
        }
        return false;
    }

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new RuntimeException(new LibraryApi.Cancelled());
        }
    }

    /** 探测线路是否可用。用于决定走桌面版还是移动版。 */
    static boolean probe(){
        if(Thread.currentThread().isInterrupted())return false;
        try{
            String body=searchBody("a","02",1,1);
            String text=request(BASE+"/ajax_search_adv.php","POST",body,true);
            // 只要是合法 JSON（含 total 字段）就算可用
            return text.contains("\"total\"");
        }catch(IOException e){
            return false;
        }catch(RuntimeException e){
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 检索                                                               */
    /* ------------------------------------------------------------------ */

    /** 组装检索请求体。字段名与结构照站点前端 adv-search.js 的原始实现。 */
    static String searchBody(String keyword,String fieldCode,int page,int pageSize){
        StringBuilder json=new StringBuilder();
        json.append("{\"searchWords\":[{\"fieldList\":[{\"fieldCode\":\"").append(esc(fieldCode))
            .append("\",\"fieldValue\":\"").append(esc(keyword)).append("\"}]}],")
            .append("\"filters\":[],\"limiter\":[],")
            .append("\"sortField\":\"relevance\",\"sortType\":\"desc\",")
            .append("\"pageSize\":").append(pageSize).append(',')
            .append("\"pageCount\":").append(Math.max(1,page)).append(',')
            .append("\"locale\":\"zh_CN\",\"first\":").append(page<=1).append('}');
        return json.toString();
    }

    private static String esc(String value){
        if(value==null)return "";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<0x20)out.append(String.format("\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    /** 检索。返回结构与 {@link LibraryApi.Page} 一致，便于两条线路互换。 */
    static LibraryApi.Page search(String keyword,String sharedType,int page)throws IOException{
        String word=LibraryApi.trim(keyword);
        if(word.isEmpty())throw new IOException("请输入检索词");

        String text=request(BASE+"/ajax_search_adv.php","POST",
            searchBody(word,fieldCode(sharedType),page,PAGE_SIZE),true);

        LibraryApi.Page result=new LibraryApi.Page();
        result.kw=word;
        result.type=sharedType==null?"title":sharedType;
        result.page=Math.max(1,page);
        result.total=number(text,"total");
        result.items=parseItems(text);

        // 桌面版没有「n/m」分页文本，由总数推算
        result.pages=Math.max(1,(int)Math.ceil(result.total/(double)PAGE_SIZE));
        // 越界时站点返回 0 条（干净的终止信号）
        result.hasNext=result.page<result.pages;
        return result;
    }

    /** 取 JSON 顶层的数字字段。 */
    private static int number(String json,String key){
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }

    /**
     * 解析 content 数组。
     * 只取列表需要的字段；字段值可能含转义，交给 {@link #unescape}。
     */
    static List<LibraryApi.Item> parseItems(String json){
        List<LibraryApi.Item> items=new ArrayList<>();
        Matcher array=Pattern.compile("\"content\"\\s*:\\s*\\[(.*?)\\]\\s*,\\s*\"facetsList\"",Pattern.DOTALL).matcher(json);
        if(!array.find()){
            // 兜底：没有 facetsList 时截到 content 数组结尾
            array=Pattern.compile("\"content\"\\s*:\\s*\\[(.*)\\]",Pattern.DOTALL).matcher(json);
            if(!array.find())return items;
        }
        String body=array.group(1);
        Matcher objects=Pattern.compile("\\{(.*?)\\}",Pattern.DOTALL).matcher(body);
        while(objects.find()){
            String object=objects.group(1);
            String title=string(object,"title");
            if(title.isEmpty()&&string(object,"marcRecNo").isEmpty())continue;

            LibraryApi.Item item=new LibraryApi.Item();
            item.title=title;
            item.author=string(object,"author");
            item.publisher=string(object,"publisher");
            item.callNo=string(object,"callNo");
            item.materialType=string(object,"docTypeName");
            item.marcNo=string(object,"marcRecNo");
            // 桌面版列表不带馆藏/可借数，需另取详情；保持 -1 表示未知
            item.available=-1;
            item.totalCopies=-1;
            items.add(item);
        }
        return items;
    }

    private static String string(String object,String key){
        Matcher m=Pattern.compile("\""+Pattern.quote(key)+"\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(object);
        return m.find()?unescape(m.group(1)):"";
    }

    /** 还原 JSON 字符串转义（含 \\uXXXX）。 */
    static String unescape(String value){
        if(value==null||value.indexOf('\\')<0)return value==null?"":value;
        StringBuilder out=new StringBuilder(value.length());
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c!='\\'||i+1>=value.length()){out.append(c);continue;}
            char next=value.charAt(++i);
            switch(next){
                case 'n':out.append('\n');break;
                case 'r':out.append('\r');break;
                case 't':out.append('\t');break;
                case 'b':out.append('\b');break;
                case 'f':out.append('\f');break;
                case '"':out.append('"');break;
                case '\\':out.append('\\');break;
                case '/':out.append('/');break;
                case 'u':
                    if(i+4<value.length()){
                        try{
                            out.append((char)Integer.parseInt(value.substring(i+1,i+5),16));
                            i+=4;
                        }catch(NumberFormatException ignored){out.append("\\u");}
                    }else out.append("\\u");
                    break;
                default:out.append(next);
            }
        }
        return out.toString();
    }

    /* ------------------------------------------------------------------ */
    /* 可借 / 总藏                                                        */
    /* ------------------------------------------------------------------ */

    /** 一本的复本数。 */
    static final class Counts {
        final int available,total;
        Counts(int available,int total){this.available=available;this.total=total;}
    }

    /**
     * 取单本的「馆藏复本 / 可借复本」数。
     *
     * 桌面版的**检索结果本身不带**这两个数字，只能逐本另发一次请求
     * （ajax_isbn_marc_no.php）。实测约 340ms/本，所以调用方必须并发+后台补全，
     * 不能在一次检索里串行等待。
     *
     * @return 解析出的数量；无法解析时返回 null（调用方保留「馆藏待确认」）
     */
    static Counts counts(String marcNo)throws IOException{
        String id=LibraryApi.trim(marcNo);
        if(id.isEmpty())return null;
        String text=request(BASE+"/ajax_isbn_marc_no.php?marc_no="+URLEncoder.encode(id,"UTF-8"),"GET",null,true);
        return parseCounts(text);
    }

    /**
     * 从 ajax_isbn_marc_no.php 的响应里解析数量。
     * 返回形如 {"image":"...","lendAvl":"<b>馆藏复本：</b>3<br><b>可借复本：</b>1"}，
     * 数字夹在 HTML 标签之间，所以要允许标签存在。
     */
    static Counts parseCounts(String json){
        if(json==null)return null;
        String text=unescape(json);
        java.util.regex.Matcher total=Pattern.compile("馆藏复本：\\s*</b>\\s*(\\d+)").matcher(text);
        java.util.regex.Matcher available=Pattern.compile("可借复本：\\s*</b>\\s*(\\d+)").matcher(text);
        boolean hasTotal=total.find(),hasAvailable=available.find();
        if(!hasTotal&&!hasAvailable)return null;
        return new Counts(
            hasAvailable?Integer.parseInt(available.group(1)):0,
            hasTotal?Integer.parseInt(total.group(1)):0);
    }

    /* ------------------------------------------------------------------ */
    /* 详情 / 馆藏                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 取馆藏明细。桌面版的 marc_no 与移动版格式相同，可直接复用。
     * 与移动版不同：这里**没有**中文编码 bug，title 不受影响。
     */
    static LibraryApi.Detail detail(Object target)throws IOException{
        String marcNo=target instanceof String?LibraryApi.trim((String)target)
            :target instanceof LibraryApi.Item?((LibraryApi.Item)target).marcNo:"";
        if(marcNo.isEmpty())throw new IOException("缺少图书标识，无法读取馆藏");

        String html=request(BASE+"/ajax_item.php?marc_no="+URLEncoder.encode(marcNo,"UTF-8"),"GET",null,false);
        LibraryApi.Detail detail=parseItem(html);
        // 详情页不改 bookDetail 的题名来源：调用方会保留列表页题名
        return detail;
    }

    /**
     * 解析 ajax_item.php 的馆藏表格。
     * 结构：<tr> 内 5 个 <td>：索书号 / 条码号 / 年卷期 / 馆藏地 / 书刊状态。
     * 馆藏地同时出现在 <td title="..."> 和单元格文本里，取 title 更可靠（不含图标标签）。
     */
    static LibraryApi.Detail parseItem(String html){
        LibraryApi.Detail detail=new LibraryApi.Detail();
        List<String> locations=new ArrayList<>();
        List<Boolean> lendable=new ArrayList<>();

        Matcher rows=Pattern.compile("<tr[^>]*>(.*?)</tr>",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(html);
        while(rows.find()){
            String row=rows.group(1);
            List<String> cells=new ArrayList<>();
            Matcher tds=Pattern.compile("<td[^>]*>(.*?)</td>",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(row);
            while(tds.find())cells.add(tds.group(1));
            if(cells.size()<5)continue;
            // 跳过表头行
            if(LibraryApi.clean(cells.get(0)).contains("索书号")&&cells.size()>0
                &&LibraryApi.clean(cells.get(3)).contains("馆藏地"))continue;

            LibraryApi.Holding holding=new LibraryApi.Holding();
            holding.callNo=LibraryApi.clean(cells.get(0));
            holding.barcode=LibraryApi.clean(cells.get(1));
            holding.volume=LibraryApi.clean(cells.get(2));
            // 馆藏地优先取 title 属性（干净），退回单元格文本
            Matcher title=Pattern.compile("title=\"([^\"]*)\"").matcher(cells.get(3));
            holding.location=title.find()?title.group(1).trim():LibraryApi.clean(cells.get(3));
            holding.status=LibraryApi.clean(cells.get(4));

            LibraryApi.Status status=LibraryApi.classifyStatus(holding.status);
            holding.statusLevel=status.level;
            holding.lendable=status.lendable;
            holding.place=LibraryLocation.parseLocation(holding.location);
            detail.holdings.add(holding);
            locations.add(holding.location);
            lendable.add(holding.lendable);
        }

        for(LibraryApi.Holding holding:detail.holdings)if(holding.lendable)detail.available++;

        boolean[] flags=new boolean[lendable.size()];
        for(int i=0;i<flags.length;i++)flags[i]=lendable.get(i);
        detail.area=LibraryLocation.summarizeLocation(locations,flags,"");
        return detail;
    }
}
