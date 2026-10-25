package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图书查询：检索江汉大学图书馆馆藏，并给出每本书的楼层与库室。
 *
 * 列表默认不拉馆藏——先给题名/作者/索书号/可借总藏，以及**按索书号推测的位置**；
 * 需要精确的条码/状态/库室时再单本展开（一次请求约 0.15 秒）。
 */
public final class LibraryActivity extends Activity {
    ThemePalette theme;
    EditText keyword;
    LinearLayout results,typeRow;
    TextView status,emptyView,prevButton,nextButton,pageLabel,typePicker,subtitle,searchButton;
    ProgressBar progress;
    ScrollView scroller;
    String searchType="title";
    final ExecutorService work=Executors.newSingleThreadExecutor();
    volatile int generation;
    boolean busy;
    final List<LibraryApi.Item> items=new ArrayList<>();

    /** 翻页状态。站点每页固定 20 条，页码从 1 开始。 */
    String lastKeyword="",lastType="",lastRoute="";
    int page=1,pages=1,total=0;

    // Match the spacing, touch targets and rounded cards used by the other native pages.
    static final int CARD_RADIUS=20,CARD_PAD_H=18,CARD_PAD_V=16;
    static final int VIEW_HEIGHT=48,SEARCH_HEIGHT=52,PICKER_HEIGHT=44,CONTROL_GAP=12;
    static final int HOLDING_PAD_H=12,HOLDING_PAD_V=10,HOLDING_GAP=8,HOLDING_RADIUS=14;
    static final int PAGER_HEIGHT=44,TRIGGER_HEIGHT=44,TRIGGER_GAP=12;

    static final String[][] TYPES={{"title","题名"},{"keyword","主题词"},{"author","著者"},{"isbn","标准编码"}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        // 线路选择需要感知网络变化（校园网 <-> 流量），先把 Context 交给它
        LibraryNetwork.attach(this);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        AppTheme.applySystemBars(this,theme);

        LinearLayout root=column();root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});

        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(10),dp(18),dp(10));
        header.addView(action("‹","返回",()->finish()),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=column();titles.setPadding(dp(14),0,dp(8),0);
        TextView title=text("图书查询",21,theme.text,true);titles.addView(title);
        // Subtitle names the service and, since routes differ in result count and
        // reachability, which line the results came from.
        subtitle=text("",12,theme.muted,false);
        titles.addView(subtitle);
        updateRouteHint();
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);

        // 检索条：输入框 + 搜索按钮
        LinearLayout search=row();search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(18),dp(2),dp(18),0);
        keyword=new EditText(this);keyword.setHint("书名、作者或 ISBN");keyword.setSingleLine(true);
        keyword.setTextSize(16);keyword.setTextColor(theme.text);keyword.setHintTextColor(theme.muted);
        keyword.setInputType(InputType.TYPE_CLASS_TEXT);keyword.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        keyword.setBackground(shape(theme.controlSurface,15));
        // Align input text with the adjacent search button.
        keyword.setGravity(Gravity.CENTER_VERTICAL);
        keyword.setIncludeFontPadding(false);
        keyword.setPadding(dp(14),0,dp(14),0);
        keyword.setContentDescription("检索词");
        keyword.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){runSearch(1);return true;}return false;});
        search.addView(keyword,new LinearLayout.LayoutParams(0,dp(SEARCH_HEIGHT),1));
        LinearLayout.LayoutParams searchSize=new LinearLayout.LayoutParams(dp(76),dp(SEARCH_HEIGHT));searchSize.leftMargin=dp(CONTROL_GAP);
        searchButton=themedButton("搜索",()->runSearch(1),true);search.addView(searchButton,searchSize);
        root.addView(search);

        // Keep the field picker compact, like the term picker elsewhere in the app.
        root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(CONTROL_GAP)));
        typeRow=row();typeRow.setPadding(dp(18),0,dp(18),dp(CONTROL_GAP));
        typePicker=themedButton("",()->chooseSearchType(),false);
        typePicker.setTextSize(14);
        // 文字靠左、垂直居中；右侧留出下拉箭头的宽度，避免文字压到箭头上
        typePicker.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        typeRow.addView(typePicker,new LinearLayout.LayoutParams(-1,dp(PICKER_HEIGHT)));
        root.addView(typeRow);
        updateTypePicker();

        status=text("输入关键词查询馆藏",13,theme.muted,false);
        status.setPadding(dp(18),0,dp(18),dp(CONTROL_GAP));status.setLineSpacing(dp(3),1);
        root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(theme.primary));progress.setVisibility(View.INVISIBLE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));

        scroller=new ScrollView(this);scroller.setVerticalScrollBarEnabled(false);scroller.setClipToPadding(false);
        LinearLayout content=column();content.setPadding(dp(18),dp(CONTROL_GAP),dp(18),dp(20));
        results=column();content.addView(results);
        emptyView=text("",14,theme.muted,false);emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(dp(6),1);emptyView.setPadding(dp(20),dp(60),dp(20),dp(60));
        emptyView.setVisibility(View.GONE);content.addView(emptyView);
        scroller.addView(content);root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));

        // 翻页条固定在底部，不随结果滚动
        LinearLayout pager=row();pager.setGravity(Gravity.CENTER_VERTICAL);pager.setPadding(dp(18),dp(CONTROL_GAP),dp(18),dp(CONTROL_GAP));
        prevButton=themedButton("上一页",()->runSearch(page-1),false);
        prevButton.setTextSize(14);
        prevButton.setContentDescription("上一页");
        pager.addView(prevButton,new LinearLayout.LayoutParams(0,dp(PAGER_HEIGHT),1));
        pageLabel=text("",13,theme.muted,false);pageLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelSize=new LinearLayout.LayoutParams(dp(84),dp(PAGER_HEIGHT));
        labelSize.leftMargin=dp(CONTROL_GAP);labelSize.rightMargin=dp(CONTROL_GAP);
        pager.addView(pageLabel,labelSize);
        nextButton=themedButton("下一页",()->runSearch(page+1),false);
        nextButton.setTextSize(14);
        nextButton.setContentDescription("下一页");
        pager.addView(nextButton,new LinearLayout.LayoutParams(0,dp(PAGER_HEIGHT),1));
        root.addView(pager);

        setContentView(root);
        updatePager(false);
        showEmpty("找一本想读的书\n\n输入书名、作者或 ISBN\n查看可借册数与馆藏位置");
    }

    /** 翻页条的状态。没有结果时整条隐藏，避免占位。 */
    void updatePager(boolean visible){
        View pager=pageLabel.getParent() instanceof View?(View)pageLabel.getParent():null;
        if(pager!=null)pager.setVisibility(visible?View.VISIBLE:View.GONE);
        if(!visible)return;
        pageLabel.setText("第 "+page+" / "+pages+" 页");
        prevButton.setEnabled(!busy&&canPrev(page));
        prevButton.setAlpha(!busy&&canPrev(page)?1f:.45f);
        nextButton.setEnabled(!busy&&canNext(page,pages));
        nextButton.setAlpha(!busy&&canNext(page,pages)?1f:.45f);
    }
    /** 第 1 页没有上一页。纯函数，便于无设备时回归。 */
    static boolean canPrev(int page){return page>1;}
    /** 末页没有下一页。纯函数，便于无设备时回归。 */
    static boolean canNext(int page,int pages){return page<pages;}
    /** 结果在全部结果中的序号，翻到第 2 页时接着 21 数下去。 */
    static int serialOf(int page,int indexInPage){return (page-1)*LibraryApi.PAGE_SIZE+indexInPage;}

    /** 选择器上显示当前检索途径，右侧带一个下拉箭头。 */
    void updateTypePicker(){
        if(typePicker==null)return;
        typePicker.setText("检索途径："+typeLabel(searchType));
        typePicker.setContentDescription("检索途径："+typeLabel(searchType)+"，点按更换");
        AppIcons.trailing(typePicker,R.drawable.ic_ui_down);
    }

    /** 检索途径 key -> 中文名。供选择器与对话标题共用。 */
    static String typeLabel(String key){
        for(String[] entry:TYPES)if(entry[0].equals(key))return entry[1];
        return TYPES[0][1];
    }

    /** 用与应用其它页面一致的方式列出检索途径，选中项打勾。 */
    void chooseSearchType(){
        if(busy)return;
        final Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout outside=new FrameLayout(this);outside.setOnClickListener(v->dialog.dismiss());
        LinearLayout card=column();card.setPadding(dp(20),dp(18),dp(20),dp(14));
        card.setBackground(shape(theme.sheetSurface,26));card.setClipToOutline(true);card.setOnClickListener(v->{});
        card.addView(text("选择检索途径",20,theme.text,true));
        TextView caption=text("决定关键词匹配图书的哪个字段",12,theme.muted,false);
        caption.setPadding(0,dp(5),0,dp(6));card.addView(caption);
        for(final String[] entry:TYPES){
            boolean chosen=entry[0].equals(searchType);
            TextView choice=themedButton(entry[1]+(chosen?"  ✓":""),()->{
                dialog.dismiss();
                if(entry[0].equals(searchType))return;
                searchType=entry[0];updateTypePicker();
                generation++;items.clear();countRows.clear();lastKeyword="";lastType="";lastRoute="";updateRouteHint();
                page=pages=1;total=0;updatePager(false);
                showEmpty("输入关键词，按"+entry[1]+"查询馆藏");
                if(!keyword.getText().toString().trim().isEmpty())runSearch(1);
            },chosen);
            choice.setTextSize(16);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(VIEW_HEIGHT));
            size.topMargin=dp(CONTROL_GAP);card.addView(choice,size);
        }
        ScrollView choices=new ScrollView(this){@Override protected void onMeasure(int w,int h){
            super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((int)(getResources().getDisplayMetrics().heightPixels*.8f),View.MeasureSpec.AT_MOST));
        }};
        choices.setVerticalScrollBarEnabled(false);choices.addView(card);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        position.setMargins(dp(12),0,dp(12),dp(12));outside.addView(choices,position);
        dialog.setContentView(outside);
        Window window=dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.24f);
        window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(R.style.SheetAnimation);
        dialog.show();
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(560)),ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 检索并显示指定页。
     * @param targetPage 页码，从 1 开始；越界时会被夹到 [1, pages]
     */
    void runSearch(int targetPage){
        final String word=keyword.getText().toString().trim();
        if(word.isEmpty()){toast("请先输入检索词");return;}
        if(busy){toast("正在查询，请稍候");return;}
        final String type=searchType,previousRoute=lastRoute;
        final int requested=LibraryPaging.requested(word,type,lastKeyword,lastType,targetPage);
        keyword.clearFocus();hideKeyboard();
        final int token=generation;
        setBusy(true);emptyView.setVisibility(View.GONE);
        status.setText(requested>1?"正在载入第 "+requested+" 页…":"正在检索「"+word+"」…");
        work.execute(()->{
            try{
                final LibraryApi.Page result=LibraryRoutes.search(word,type,requested,previousRoute);
                runOnUiThread(()->{
                    if(token!=generation||isDestroyed())return;
                    generation++;setBusy(false);showPage(word,result);
                    if(result.pageReset)toast("查询结果已变化，已回到第一页");
                });
            }catch(IOException error){
                runOnUiThread(()->{
                    if(token!=generation||isDestroyed())return;
                    setBusy(false);
                    status.setText(items.isEmpty()?"查询失败":"加载失败 · 仍显示「"+lastKeyword+"」第 "+page+" 页");
                    updatePager(!items.isEmpty());
                    // 请求失败时保留当前列表的来源。
                    updateRouteHint();
                    if(items.isEmpty())showEmpty(failureHint(error));else toast(failureHint(error));
                });
            }
        });
    }

    void setBusy(boolean value){
        busy=value;searchButton.setEnabled(!value);typePicker.setEnabled(!value);
        searchButton.setAlpha(value?.5f:1f);typePicker.setAlpha(value?.5f:1f);
        progress.setVisibility(value?View.VISIBLE:View.INVISIBLE);updatePager(!items.isEmpty());
    }

    /** 显示一页结果，并同步翻页状态。 */
    void showPage(String word,LibraryApi.Page page){
        lastKeyword=word;lastType=page.type;lastRoute=page.route;
        this.page=page.page;
        this.pages=Math.max(1,page.pages);
        this.total=page.total;

        items.clear();items.addAll(page.items);
        updateRouteHint();
        if(items.isEmpty()){
            status.setText("共 0 条结果");
            updatePager(false);
            showEmpty("没有找到「"+word+"」\n\n可以换用主题词或著者再试一次。");
            return;
        }
        renderPage(page);
        updatePager(true);
        if(scroller!=null)scroller.scrollTo(0,0);
    }

    void renderPage(LibraryApi.Page page){
        results.removeAllViews();
        if(page.items.isEmpty()){
            status.setText("共 0 条结果");
            showEmpty("没有找到「"+page.kw+"」\n\n可以换用主题词或著者再试一次。");
            return;
        }
        status.setText("共 "+page.total+" 条 · 第 "+page.page+"/"+page.pages+" 页 · 点开可看馆藏位置");
        countRows.clear();
        for(int i=0;i<page.items.size();i++)addCard(page.items.get(i),(page.page-1)*LibraryApi.PAGE_SIZE+i+1);
        startCountFill(page.items);
    }

    /**
     * 后台并发补全可借数（仅桌面版需要）。
     *
     * 桌面版列表不带这个数字，逐本查约 340ms；并发之后 20 本大约 1 秒，
     * 每拿到一本就就地刷新一行，不阻塞列表显示。
     * 移动版自带数字，这里是空转（都被 available>=0 过滤掉）。
     */
    void startCountFill(final java.util.List<LibraryApi.Item> pageItems){
        final int token=generation;
        LibraryRoutes.fillCounts(pageItems,new LibraryRoutes.CountListener(){
            @Override public void onCounts(final LibraryApi.Item item,final int available,final int total){
                if(token!=generation||isDestroyed())return;
                runOnUiThread(()->{
                    if(token!=generation||isDestroyed())return;
                    item.available=available;
                    item.totalCopies=total;
                    TextView row=countRows.get(item);
                    if(row!=null){
                        row.setText(availabilityLabel(item));
                        row.setTextColor(available>0?theme.deepAccent:theme.muted);
                    }
                });
            }
            @Override public boolean cancelled(){return token!=generation||isDestroyed();}
        });
    }

    /**
     * 顶部只表示当前搜索结果的来源；单本详情的回退来源在详情中标注。
     */
    void updateRouteHint(){
        if(subtitle==null)return;
        subtitle.setText(routeSubtitle(lastRoute.isEmpty()?"":LibraryRoutes.routeIndicator(lastRoute)));
    }

    /** 副标题的完整文本。纯函数，便于无设备时回归。 */
    static String routeSubtitle(String indicator){
        return indicator.isEmpty()?"江汉大学图书馆 · 馆藏与位置":"江汉大学图书馆 · 搜索："+indicator;
    }

    String failureHint(IOException error){
        String message=error.getMessage()==null?"查询失败":error.getMessage();
        return message+"\n请检查网络后重试，或连接校园网再试。";
    }

    /** 结果卡片里「可借 N / M 册」那一行的引用，供后台补全时就地刷新。 */
    final java.util.Map<LibraryApi.Item,TextView> countRows=new java.util.WeakHashMap<>();

    /** 「可借」那一行的文本。数字未到时只提示正在读取，不要拼成「可借 正在读取馆藏… 册」。 */
    static String availabilityLabel(LibraryApi.Item item){
        if(item.available<0||item.totalCopies<0)return "正在读取可借数…";
        return "可借 "+item.available+" / "+item.totalCopies+" 册";
    }

    /** 一张结果卡片：默认只有基本信息 + 推测位置，展开时才拉馆藏。 */
    void addCard(LibraryApi.Item item,int index){
        LinearLayout card=column();card.setPadding(dp(CARD_PAD_H),dp(CARD_PAD_V),dp(CARD_PAD_H),dp(CARD_PAD_V));
        card.setBackground(shape(theme.entrySurface,CARD_RADIUS));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=dp(12);
        results.addView(card,size);

        TextView title=text((index)+". "+(item.title.isEmpty()?"（无题名）":item.title),16,theme.text,true);
        title.setLineSpacing(dp(4),1);card.addView(title);
        if(!item.author.isEmpty())card.addView(line(item.author,13,theme.muted));
        if(!item.publisher.isEmpty())card.addView(line(item.publisher,12,theme.muted));

        // 可借 / 总藏。展开后会就地刷新这一行，所以留一个引用。
        final TextView counts=line(availabilityLabel(item),13,
            item.available>0?theme.deepAccent:theme.muted);counts.setTypeface(Typeface.create("sans-serif-medium",0));
        card.addView(counts);
        // 桌面版列表不带这个数字，稍后由后台并发补全时就地刷新这一行
        if(item.available<0)countRows.put(item,counts);
        if(!item.callNo.isEmpty())card.addView(line("索书号 "+item.callNo,13,theme.text));

        // 推测位置：有具体馆藏地时绝不覆盖，这里只是列表页的初判
        LibraryLocation.Guess guess=LibraryLocation.guessRoom(item.callNo);
        if(guess==null){
            card.addView(line("位置：点开馆藏明细确认",12,theme.muted));
        }else{
            String where=guess.floor>0?guess.floor+"楼 "+guess.room:guess.room;
            card.addView(line("推测位置 "+where+"（"+guess.note+"，点开馆藏明细确认）",12,theme.deepAccent));
        }

        LinearLayout detailBox=column();detailBox.setVisibility(View.GONE);card.addView(detailBox);

        final TextView trigger=themedButton("查看馆藏明细",null,false);
        trigger.setTextSize(14);
        trigger.setOnClickListener(v->{
            if(item.detailLoaded){
                boolean show=detailBox.getVisibility()!=View.VISIBLE;
                detailBox.setVisibility(show?View.VISIBLE:View.GONE);
                trigger.setText(show?"收起馆藏明细":"查看馆藏明细");
                return;
            }
            loadDetail(item,counts,detailBox,trigger);
        });
        LinearLayout.LayoutParams openSize=new LinearLayout.LayoutParams(-1,dp(TRIGGER_HEIGHT));
        openSize.topMargin=dp(TRIGGER_GAP);
        card.addView(trigger,openSize);
    }

    /** 按需加载单本详情：一次请求，成功后填充并展开。 */
    void loadDetail(final LibraryApi.Item item,final TextView counts,final LinearLayout detailBox,final TextView trigger){
        if(busy){toast("正在查询，请稍候");return;}
        final int token=generation;
        final String searchRoute=lastRoute;
        trigger.setText("正在读取馆藏…");trigger.setEnabled(false);
        work.execute(()->{
            try{
                final LibraryApi.Detail detail=LibraryRoutes.detail(item,searchRoute);
                if(token!=generation)return;
                runOnUiThread(()->{
                    if(token!=generation||isDestroyed())return;
                    item.detailLoaded=true;item.detailError="";
                    item.available=detail.available;item.totalCopies=detail.totalCopies();
                    if(item.title.isEmpty())item.title=detail.title;
                    fillDetail(detailBox,detail);
                    detailBox.setVisibility(View.VISIBLE);
                    trigger.setText("收起馆藏明细");trigger.setEnabled(true);
                    counts.setText("可借 "+detail.available+" / "+detail.totalCopies()+" 册");
                });
            }catch(IOException error){
                if(token!=generation)return;
                final String message=error.getMessage()==null?"读取失败":error.getMessage();
                runOnUiThread(()->{
                    if(token!=generation||isDestroyed())return;
                    trigger.setText("查看馆藏明细");trigger.setEnabled(true);
                    item.detailError=message;
                    toast("馆藏读取失败："+message);
                });
            }
        });
    }

    /** 把详情写进卡片：区位归并结果 + 每册明细。 */
    void fillDetail(LinearLayout box,LibraryApi.Detail detail){
        box.removeAllViews();
        box.addView(line("",1,theme.muted));
        if(!detail.route.isEmpty()&&!detail.route.equals(lastRoute)){
            box.addView(line("馆藏详情来源："+LibraryRoutes.routeIndicator(detail.route),12,theme.muted));
        }

        LibraryLocation.Summary area=detail.area;
        // 实际馆藏地优先——有具体库室时不再使用推测
        if(area!=null&&area.hasSpecific()){
            for(LibraryLocation.Bucket bucket:area.rooms){
                String where=bucket.place.floor>0?bucket.place.floor+"楼 "+bucket.place.room:bucket.place.room;
                if(where.trim().isEmpty())where=bucket.place.name;
                String note=bucket.place.name+"　"+where+"　可借 "+bucket.available+"/"+bucket.total;
                box.addView(line(note,13,theme.deepAccent));
            }
        }
        if(area!=null&&area.vagueCount>0){
            box.addView(line("有 "+area.vagueCount+" 册馆藏地未标注具体库室，需到馆查询。",12,theme.muted));
        }
        // 偶见的代码值（如 "304--113"）：说明无法据此推断，而不是让用户以为工具出错
        if(area!=null&&area.unusableLocations!=null&&!area.unusableLocations.isEmpty()){
            box.addView(line("另有馆藏地写作「"+join(area.unusableLocations,"、")+"」，无法判断具体库室。",12,theme.muted));
        }
        if(area!=null&&!area.hasSpecific()&&area.guess!=null){
            LibraryLocation.Guess guess=area.guess;
            String where=guess.floor>0?guess.floor+"楼 "+guess.room:guess.room;
            box.addView(line("馆藏地未标注具体库室。按中图法推测可能在 "+where+"（"+guess.note+"）",12,theme.deepAccent));
        }
        if(detail.callNoClass.isEmpty()==false)box.addView(line("中图法分类号 "+detail.callNoClass,12,theme.muted));
        if(!detail.isbn.isEmpty())box.addView(line("ISBN "+detail.isbn,12,theme.muted));

        if(detail.holdings.isEmpty()){
            box.addView(line("这本书暂时没有可显示的馆藏记录。",12,theme.muted));
            return;
        }
        box.addView(line("",1,theme.muted));
        box.addView(line("馆藏明细（"+detail.holdings.size()+" 册）",13,theme.text,true));
        for(LibraryApi.Holding holding:detail.holdings){
            LinearLayout row=column();
            row.setPadding(dp(HOLDING_PAD_H),dp(HOLDING_PAD_V),dp(HOLDING_PAD_H),dp(HOLDING_PAD_V));
            row.setBackground(shape(theme.controlSurface,HOLDING_RADIUS));
            LinearLayout.LayoutParams rowSize=new LinearLayout.LayoutParams(-1,-2);
            rowSize.topMargin=dp(HOLDING_GAP);
            box.addView(row,rowSize);

            String state=holding.status.isEmpty()?"状态未知":holding.status;
            TextView head=line(state+"　"+holding.placeText(),13,holding.lendable?theme.deepAccent:theme.text,true);
            head.setPadding(0,0,0,dp(3));
            row.addView(head);
            // 明细行改用紧凑行距：一册最多 4 行，用常规 line() 的上下留白会撑高整个框
            if(!holding.barcode.isEmpty())row.addView(dense("条码号 "+holding.barcode,12,theme.muted));
            if(!holding.callNo.isEmpty())row.addView(dense("索书号 "+holding.callNo,12,theme.muted));
            if(!holding.returnLocation.isEmpty())row.addView(dense("还书位置 "+holding.returnLocation,12,theme.muted));
            if(!holding.location.isEmpty())row.addView(dense("馆藏地 "+holding.location,12,theme.muted));
        }
    }

    /** 馆藏明细里用的紧凑文本行：不加 line() 的上下留白，靠框内边距留出呼吸。 */
    TextView dense(String value,int size,int color){
        TextView t=text(value,size,color,false);t.setLineSpacing(dp(1),1);return t;
    }

    /** 把若干条文本用分隔符连起来（列表最多几条，不必引 String.join）。 */
    static String join(List<String> values,String separator){
        StringBuilder text=new StringBuilder();
        for(String value:values){
            if(value==null||value.trim().isEmpty())continue;
            if(text.length()>0)text.append(separator);
            text.append(value.trim());
        }
        return text.toString();
    }

    void showEmpty(String message){
        results.removeAllViews();
        emptyView.setText(message);emptyView.setVisibility(View.VISIBLE);
    }
    void hideKeyboard(){
        android.view.inputmethod.InputMethodManager manager=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(manager!=null)manager.hideSoftInputFromWindow(keyword.getWindowToken(),0);
    }
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    @Override protected void onDestroy(){
        generation++;         // 让在途结果失效
        work.shutdownNow();   // 并尝试中断阻塞的读
        super.onDestroy();
    }

    TextView line(String value,int size,int color){return line(value,size,color,false);}
    TextView line(String value,int size,int color,boolean bold){
        TextView t=text(value,size,color,bold);t.setLineSpacing(dp(4),1);t.setPadding(0,dp(3),0,dp(3));return t;
    }
    TextView text(String value,int size,int color,boolean bold){
        TextView t=new AppIcons.Label(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    TextView action(String label,String description,Runnable run){
        TextView button=text(label,label.length()>1?14:24,theme.deepAccent,false);button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);button.setFocusable(true);
        GradientDrawable fill=shape(theme.entrySurface,15);GradientDrawable mask=shape(theme.rippleMask,15);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),fill,mask));
        button.setOnClickListener(v->run.run());return button;
    }
    TextView themedButton(String value,Runnable run,boolean filled){
        TextView button=text(value,14,filled?theme.onPrimary:theme.deepAccent,false);button.setGravity(Gravity.CENTER);
        button.setContentDescription(value);button.setFocusable(true);button.setIncludeFontPadding(false);button.setPadding(dp(14),0,dp(14),0);
        GradientDrawable fill=shape(filled?theme.primary:theme.controlSurface,14);
        if(!filled)fill.setStroke(dp(1),theme.outline);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(((filled?theme.onPrimary:theme.primary)&0xffffff)|0x22000000),fill,shape(theme.rippleMask,14)));
        // run 为 null 时只做外观，点击行为由调用方稍后绑定（需要自引用时用）
        if(run!=null)button.setOnClickListener(v->run.run());
        return button;
    }
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
