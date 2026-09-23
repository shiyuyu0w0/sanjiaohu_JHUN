package cn.jhun.sanjiaohu;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.util.AtomicFile;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final String HOME="https://jwxt.jhun.edu.cn/frame/homes.action";
    static final String SOURCE="https://jwxt.jhun.edu.cn/frame/desk/showLessonSchedule4User.action";
    int BG,INK,MUTED,PRIMARY,ACCENT_TEXT,ON_PRIMARY;
    ThemePalette palette;
    android.graphics.Bitmap wallpaper;
    boolean imageBusy=false;
    float wallpaperAspect=.65f;
    final Schedule localSchedule=Schedule.local();
    CustomCourseStore customStore;
    List<Course> localCourses=new ArrayList<>();
    boolean customReadError=false,todayLabel=false,verified=false,autoTried=false,autoSubmitted=false,autoRunning=false,credentialBusy=false;
    int page=0;
    long submittedAt;
    String loginStateScript,loginSubmitScript;
    CredentialStore.Credentials automaticCredentials;
    TextView authStatusText;
    final java.util.concurrent.ExecutorService authIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    FrameLayout root;
    LinearLayout screen;
    android.animation.ValueAnimator pageAnimator;
    android.animation.ValueAnimator weekAnimator;
    WeekSwipeLayout weekHost;
    TextView weekTitle,previousWeekButton,nextWeekButton;
    int previewWeek=0,queuedWeek=0;
    float weekProgress=0;
    final List<CourseCardView> previousCards=new ArrayList<>();
    int transitionFrom=-1;
    TextView stateText;
    TextView updateDot;
    Dialog activeSheet;
    final List<CourseCardView> visibleCards=new ArrayList<>();
    WebView web;
    Schedule schedule;
    JSONObject scheduleCandidate;
    String schedulePortalScript,scheduleListScript,scheduleStage="";
    AcademicStore academicStore;
    Grades grades,summaryGrades;
    Exams exams;
    String examTerm="",examState="尚无本地考试安排 · 联网后获取",examPortalScript;
    final List<String> examTerms=new ArrayList<>();
    final List<TextView> examBadges=new ArrayList<>();
    final List<LinearLayout> examCards=new ArrayList<>();
    TextView examPendingTitle;
    final List<Exams.Entry> examBadgeEntries=new ArrayList<>();
    final Runnable examClock=new Runnable(){public void run(){if(!inBackground&&page==4){updateExamBadges();handler.postDelayed(this,30000);}}};
    UiSheet gradeSummarySheet;
    boolean summaryAll=true;
    String summaryState="尚无本地汇总 · 联网后获取";
    String selectedTerm="",gradeTerm="",jobKind="schedule",jobTerm="",portalScript,parserScript;
    String gradeState="尚无本地成绩 · 联网后获取";
    final List<String> terms=new ArrayList<>();
    String state="尚未同步 · 登录后，课表会保存在这台手机";
    boolean busy=false, inBackground=false, loadError=false, mainLoaded=false, identityBusy=false;
    int selectedWeek=1, generation=0;
    long deadline;
    Handler handler=new Handler(Looper.getMainLooper());
    android.content.SharedPreferences prefs;
    AtomicFile cache;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        customStore=new CustomCourseStore(this);
        try{localCourses=customStore.load();}catch(Exception e){customReadError=true;}
        if(saved!=null){page=saved.getInt("page",0);todayLabel=saved.getBoolean("todayLabel",false);wallpaperAspect=saved.getFloat("wallpaperAspect",.65f);}
        refreshAppearance();
        cache=new AtomicFile(new File(getFilesDir(),"schedule.json"));
        try {loginStateScript=read(getAssets().open("login-state.js"));loginSubmitScript=read(getAssets().open("login-submit.js"));schedulePortalScript=read(getAssets().open("schedule-portal.js"));scheduleListScript=read(getAssets().open("schedule-list.js"));} catch(Exception e) {throw new IllegalStateException("课表适配器缺失",e);}
        try {
            JSONObject data=new JSONObject(new String(cache.readFully(),StandardCharsets.UTF_8));
            if(CachePolicy.usable(data.optLong("savedAt",0),data.optString("source",""))) {
                schedule=new Schedule(data);state="本地课表 · 上次更新 "+stamp(schedule.savedAt);
            } else {cache.delete();state="请先登录教务系统，获取你自己的课表";}
        } catch(Exception e){state="尚无本地课表 · 请先登录教务系统";}
        academicStore=new AcademicStore(this);
        try{portalScript=read(getAssets().open("portal.js"));parserScript=read(getAssets().open("portal-parser.js"));examPortalScript=read(getAssets().open("exam-portal.js"));}catch(Exception e){throw new IllegalStateException("教务适配器缺失",e);}
        try{JSONArray catalog=new JSONArray(prefs.getString("terms","[]"));for(int i=0;i<catalog.length();i++){String name=Term.label(catalog.getString(i));if(!terms.contains(name))terms.add(name);}}catch(Exception ignored){}
        if(schedule!=null){try{
            selectedTerm=Term.label(schedule.term);if(!terms.contains(selectedTerm))terms.add(selectedTerm);
            long migratedAt=0;try{migratedAt=academicStore.load("schedule",selectedTerm).optLong("savedAt");}catch(Exception ignored){}
            if(migratedAt<schedule.savedAt)academicStore.save("schedule",schedule.json);
        }catch(Exception ignored){}}
        selectedTerm=prefs.getString("selectedTerm",selectedTerm);
        if(!selectedTerm.isEmpty())loadSchedule();
        gradeTerm=prefs.getString("gradeTerm",selectedTerm);loadGrades();
        examTerm=prefs.getString("examTerm","");loadExams();
        try{JSONArray catalog=new JSONArray(prefs.getString("examTerms","[]"));for(int i=0;i<catalog.length();i++)examTerms.add(Term.label(catalog.getString(i)));}catch(Exception ignored){}
        try{summaryGrades=new Grades(academicStore.load("summary","入学以来"));summaryState="本地汇总 · "+stamp(summaryGrades.savedAt);}catch(Exception ignored){}
        if(schedule!=null) selectedWeek=currentWeek();
        root=new FrameLayout(this); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        setContentView(root); createWeb(); render();
    }
    @Override protected void onStart(){super.onStart();inBackground=false;selectedWeek=currentWeek();render();handler.removeCallbacks(examClock);if(page==4)handler.postDelayed(examClock,30000);if(prefs.getBoolean("loginCompleted",false)||canAutoLogin())sync();}
    @Override protected void onResume(){super.onResume();updateBadge();UpdateStore.check(getApplicationContext(),false,error->handler.post(()->{if(!isDestroyed())updateBadge();}));}
    void updateBadge(){if(updateDot!=null)updateDot.setVisibility(new UpdateStore(this).available(this)?View.VISIBLE:View.GONE);}
    @Override protected void onStop(){finishWeekTransition();super.onStop();if(pageAnimator!=null)pageAnimator.cancel();if(moreMenu!=null)moreMenu.dismiss();inBackground=true;verified=false;automaticCredentials=null;autoRunning=false;if(busy){generation++;busy=false;web.stopLoading();setState("本地课表 · 返回应用时重新同步");}CookieManager.getInstance().flush();}
    @Override protected void onDestroy(){finishWeekTransition();if(pageAnimator!=null)pageAnimator.cancel();if(activeSheet!=null)activeSheet.dismiss();handler.removeCallbacksAndMessages(null);authIo.shutdown();web.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("page",page);out.putBoolean("todayLabel",todayLabel);out.putFloat("wallpaperAspect",wallpaperAspect);}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);render();if(activeSheet!=null)activeSheet.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(560)),-1);}
    String read(InputStream in)throws IOException {try(InputStream input=in; ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    String stamp(long ms){return ms==0?"未知":java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));}
    public static boolean isSchoolHttps(String url){try {Uri u=Uri.parse(url);String h=u.getHost();return "https".equals(u.getScheme()) && h!=null && (h.equals("jhun.edu.cn")||h.endsWith(".jhun.edu.cn"));}catch(Exception e){return false;}}
    boolean trusted(String url){return isSchoolHttps(url);}
    boolean sourceOrigin(String url){try{return trusted(url)&&"jwxt.jhun.edu.cn".equals(Uri.parse(url).getHost());}catch(Exception e){return false;}}
    void createWeb(){
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.getSettings().setLoadWithOverviewMode(true);web.getSettings().setUseWideViewPort(true);
        web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){result.confirm();if(autoRunning)authAttention("自动登录未完成 · 请到个人页验证账号");return true;}});
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){mainLoaded=false;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                if(trusted(r.getUrl().toString())) return false;
                if(r.isForMainFrame()){if(busy)fail("登录跳转不受支持 · 已保留本地课表");Toast.makeText(MainActivity.this,"仅允许打开学校 HTTPS 页面",Toast.LENGTH_LONG).show();}return true;
            }
            @Override public void onPageFinished(WebView v,String url){mainLoaded=true;CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){loadError=true;if(busy)fail("连接失败 · 已保留本地课表");}}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame()){loadError=true;if(busy)fail("教务服务异常 · 已保留本地课表");}}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();loadError=true;if(busy)fail("安全连接失败 · 已保留本地课表");}
        });attachHidden();
    }
    void attachHidden(){if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);web.setAlpha(0);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);web.setFocusable(false);root.addView(web,0,new FrameLayout.LayoutParams(1,1));}
    void sync(){
        if(busy||credentialBusy)return;
        jobKind=page==4?"exams":page==3?(gradeSummarySheet!=null&&gradeSummarySheet.dialog.isShowing()&&summaryAll?"summary":"grades"):"schedule";jobTerm=jobKind.equals("exams")?examTerm:jobKind.equals("summary")?"入学以来":page==3?gradeTerm:selectedTerm;
        if(!prefs.getBoolean("loginCompleted",false)&&!canAutoLogin()){showLogin();return;}
        autoTried=false;autoRunning=false;loadError=false;mainLoaded=false;startPoll();web.loadUrl(HOME);
    }
    void cancelSync(){
        if(busy){if(jobKind.equals("exams"))examState=exams==null?"尚无本地考试安排":"本地考试安排 · "+stamp(exams.savedAt);else if(jobKind.equals("summary"))summaryState=summaryGrades==null?"尚无本地汇总":"本地汇总 · "+stamp(summaryGrades.savedAt);else if(jobKind.equals("grades"))gradeState=grades==null?"尚无该学期的本地成绩":"本地成绩 · "+stamp(grades.savedAt);else state=schedule==null?"尚无该学期的本地课表":"本地课表 · "+stamp(schedule.savedAt);}
        generation++;busy=false;autoRunning=false;automaticCredentials=null;web.stopLoading();
    }
    void loadSchedule(){
        schedule=null;
        try{schedule=new Schedule(academicStore.load("schedule",selectedTerm));state="本地课表 · 上次更新 "+stamp(schedule.savedAt);}catch(Exception e){state="该学期暂无本地课表 · 联网后获取";}
        scheduleCandidate=null;try{scheduleCandidate=academicStore.scheduleSnapshot(selectedTerm,"candidate");if(scheduleCandidate!=null)state="发现课表更新 · 待确认，当前课表已保留";}catch(Exception ignored){}
        selectedWeek=currentWeek();
    }
    void loadGrades(){
        grades=null;
        try{grades=new Grades(academicStore.load("grades",gradeTerm));gradeState="本地成绩 · 上次更新 "+stamp(grades.savedAt);}catch(Exception e){gradeState="该学期暂无本地成绩 · 联网后获取";}
    }
    void loadExams(){
        exams=null;
        try{exams=new Exams(academicStore.load("exams",examTerm));examState="本地考试安排 · 上次更新 "+stamp(exams.savedAt);}catch(Exception e){examState="尚无该学期的本地考试安排 · 联网后获取";}
    }
    void learnTerms(JSONObject obj)throws Exception{
        if(jobKind.equals("exams")){
            JSONArray catalog=obj.optJSONArray("terms");if(catalog!=null&&catalog.length()>0){examTerms.clear();for(int i=0;i<catalog.length();i++){String name=Term.label(catalog.getString(i));if(!examTerms.contains(name))examTerms.add(name);}prefs.edit().putString("examTerms",new JSONArray(examTerms).toString()).apply();}
            String resolved=obj.optString("selectedTerm");if(jobTerm.isEmpty()&&!resolved.isEmpty()){jobTerm=Term.label(resolved);examTerm=jobTerm;prefs.edit().putString("examTerm",examTerm).apply();loadExams();render();setState("正在获取考试安排 · 本地记录随时可看");}return;
        }
        if(jobKind.equals("summary"))return;
        JSONArray catalog=obj.optJSONArray("terms");
        if(catalog!=null&&catalog.length()>0){
            List<String> found=new ArrayList<>();for(int i=0;i<catalog.length();i++){String name=Term.label(catalog.getString(i));if(!found.contains(name))found.add(name);}
            if(!found.equals(terms)){terms.clear();terms.addAll(found);prefs.edit().putString("terms",new JSONArray(terms).toString()).apply();}
        }
        String resolved=obj.optString("selectedTerm","");
        if(jobTerm.isEmpty()&&!resolved.isEmpty()){
            jobTerm=Term.label(resolved);
            if(jobKind.equals("grades")){gradeTerm=jobTerm;prefs.edit().putString("gradeTerm",gradeTerm).apply();loadGrades();}
            else{selectedTerm=jobTerm;prefs.edit().putString("selectedTerm",selectedTerm).apply();loadSchedule();}
            render();setState("正在获取"+(jobKind.equals("exams")?"考试安排":jobKind.equals("summary")?"汇总":jobKind.equals("grades")?"成绩":"课表")+"…");
        }
    }
    boolean canAutoLogin(){return prefs.getBoolean("autoLogin",true)&&!prefs.getBoolean("autoBlocked",false)&&CredentialStore.exists(this);}
    void authAttention(String message){verified=false;automaticCredentials=null;autoRunning=false;prefs.edit().putBoolean("loginCompleted",false).putBoolean("autoBlocked",true).apply();fail(message);}
    void authenticated(){verified=true;prefs.edit().putBoolean("loginCompleted",true).putBoolean("autoBlocked",false).putLong("lastAuthAt",System.currentTimeMillis()).apply();}
    void tryAutoLogin(int token){
        verified=false;
        if(autoTried||!canAutoLogin()){authAttention("登录已过期 · 请到个人页登录，课表仍保留");return;}
        autoTried=true;autoRunning=true;setState("会话已过期 · 正在自动登录…");
        authIo.execute(()->{try{CredentialStore.Credentials credentials=CredentialStore.load(this);handler.post(()->{
            if(isDestroyed()||inBackground||token!=generation||!busy)return;
            automaticCredentials=credentials;autoSubmitted=false;mainLoaded=false;deadline=SystemClock.elapsedRealtime()+30000;
            web.loadUrl(LoginActivity.ENTRY);handler.postDelayed(()->pollLogin(token),600);
        });}catch(Exception e){handler.post(()->{if(token==generation&&busy)authAttention("本机登录凭证无法读取 · 请到个人页重新登录");});}});
    }
    void pollLogin(int token){
        if(token!=generation||!busy||inBackground)return;
        if(SystemClock.elapsedRealtime()>deadline){automaticCredentials=null;autoRunning=false;fail("自动登录超时 · 已保留本地课表");return;}
        if(!mainLoaded||!sourceOrigin(web.getUrl())){handler.postDelayed(()->pollLogin(token),600);return;}
        web.evaluateJavascript(loginStateScript,result->{
            if(token!=generation||!busy||inBackground)return;
            try{
                JSONObject data=new JSONObject(result);String kind=data.optString("state");
                if(kind.equals("success")){automaticCredentials=null;autoRunning=false;authenticated();mainLoaded=false;deadline=SystemClock.elapsedRealtime()+(jobKind.equals("exams")?120000:45000);setState("自动登录成功 · 正在获取数据");web.loadUrl(HOME);handler.postDelayed(()->poll(token),600);return;}
                if(kind.equals("ready")){
                    if(data.optBoolean("captcha")){authAttention("学校要求验证码 · 请到个人页完成登录");return;}
                    if(!autoSubmitted){
                        if(automaticCredentials==null){authAttention("请到个人页重新保存登录凭证");return;}
                        String call=loginSubmitScript.trim()+"("+JSONObject.quote(automaticCredentials.account)+","+JSONObject.quote(automaticCredentials.password)+",\"\")";
                        automaticCredentials=null;autoSubmitted=true;submittedAt=SystemClock.elapsedRealtime();
                        web.evaluateJavascript(call,response->{if(token!=generation||!busy)return;try{if(new JSONObject(response).has("error"))authAttention("自动登录需要手动验证 · 请打开个人页");}catch(Exception e){authAttention("学校登录页面变化 · 请打开个人页重试");}});
                    }else if(SystemClock.elapsedRealtime()-submittedAt>10000){authAttention("账号验证未通过 · 请到个人页检查登录信息");return;}
                }
                handler.postDelayed(()->pollLogin(token),600);
            }catch(Exception e){authAttention("学校登录页面暂时无法读取 · 请手动登录");}
        });
    }
    void startPoll(){if(busy)return;busy=true;int token=++generation;deadline=android.os.SystemClock.elapsedRealtime()+(jobKind.equals("exams")?120000:45000);setState("正在更新"+(jobKind.equals("exams")?"考试安排":jobKind.equals("summary")?"汇总":jobKind.equals("grades")?"成绩":"课表")+" · 本地记录随时可看");handler.postDelayed(()->poll(token),600);}
    void poll(int token){
        if(token!=generation || !busy || inBackground)return;
        if(android.os.SystemClock.elapsedRealtime()>deadline){fail((jobKind.equals("schedule")?scheduleStage+" · ":"")+"更新超时 · 已保留本地课表");return;}
        if(loadError){fail("连接失败 · 已保留本地课表");return;}
        if(!mainLoaded || !sourceOrigin(web.getUrl())){handler.postDelayed(()->poll(token),700);return;}
        String query=jobKind.equals("schedule")?schedulePortalScript+"("+token+","+JSONObject.quote(jobTerm)+","+scheduleListScript+")":jobKind.equals("exams")?examPortalScript+"("+token+","+JSONObject.quote(jobTerm)+","+parserScript+")":portalScript+"("+token+","+JSONObject.quote(jobKind)+","+JSONObject.quote(jobTerm)+","+parserScript+")";
        web.evaluateJavascript(query,result->{
            if(token!=generation || !busy)return;
            try {
                JSONObject obj=new JSONObject(result);
                learnTerms(obj);
                if(obj.has("error")) {
                    String error=obj.optString("error");
                    if(error.equals("login")){tryAutoLogin(token);return;}
                    if(error.equals("unavailable")){fail("教务系统未提供该学期 · 已保留上次记录");return;}
                    if(jobKind.equals("schedule")){
                        scheduleStage=scheduleStageLabel(obj.optString("stage"));
                        if(error.equals("structure")){fail(scheduleStage+" · 已保留本地课表");return;}
                        setState(scheduleStage+" · 本地记录随时可看");handler.postDelayed(()->poll(token),700);return;
                    }
                    if(error.equals("structure")||error.equals("incomplete")){fail("教务数据尚未完整加载 · 已保留上次记录，可稍后重试");return;}
                    handler.postDelayed(()->poll(token),700);return;
                }
                if(jobKind.equals("summary")?!obj.optString("scope").equals("all"):(jobTerm.isEmpty()||!Term.same(jobTerm,obj.getString("term"))))throw new IllegalArgumentException("学期不匹配");
                obj.put("savedAt",System.currentTimeMillis());obj.put("source","school-sync");
                if(jobKind.equals("exams")){
                    Exams fresh=new Exams(obj);academicStore.save("exams",obj);exams=fresh;examState="已保存到本地 · "+stamp(fresh.savedAt);
                }else if(jobKind.equals("summary")){
                    Grades fresh=new Grades(obj);academicStore.save("summary",obj);summaryGrades=fresh;summaryState="已保存到本地 · "+stamp(fresh.savedAt);
                }else if(jobKind.equals("grades")){
                    Grades fresh=new Grades(obj);academicStore.save("grades",obj);grades=fresh;
                    gradeState="已保存到本地 · "+stamp(grades.savedAt);
                }else{
                    Schedule fresh=new Schedule(obj);
                    if(academicStore.stageSchedule(obj)){schedule=fresh;scheduleCandidate=null;state="已保存到本地 · "+stamp(schedule.savedAt);}
                    else{scheduleCandidate=obj;state="发现课表更新 · 待确认，当前课表已保留";}
                }
                authenticated();busy=false;generation++;CookieManager.getInstance().flush();render();refreshSummary();
            }catch(Exception e){fail("数据格式变化或保存失败 · 已保留上次记录");}
        });
    }
    void setState(String s){if(jobKind.equals("exams"))examState=s.replace("本地课表","本地考试安排");else if(jobKind.equals("summary"))summaryState=s.replace("本地课表","本地汇总");else if(jobKind.equals("grades"))gradeState=s.replace("本地课表","本地成绩");else state=s;if(stateText!=null)stateText.setText(page==4?examState:page==3?gradeState:state);if(authStatusText!=null)authStatusText.setText(authStatus());refreshSummary();}
    void fail(String reason){automaticCredentials=null;autoRunning=false;busy=false;generation++;if(jobKind.equals("exams")?exams==null:jobKind.equals("summary")?summaryGrades==null:jobKind.equals("grades")?grades==null:schedule==null)reason=reason.replace("已保留本地课表","暂无缓存，可稍后重试").replace("已保留上次记录","暂无缓存，可稍后重试");setState(reason);}
    void showLogin(){if(credentialBusy)return;generation++;busy=false;automaticCredentials=null;autoRunning=false;web.stopLoading();verified=false;startActivityForResult(new Intent(this,LoginActivity.class),20);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==30 && result==RESULT_OK && data!=null && data.getData()!=null){importWallpaper(data.getData());}
        if(request==31){imageBusy=false;if(result==RESULT_OK){followBackgroundTheme();refreshAppearance();render();Toast.makeText(this,"裁切壁纸已保存",Toast.LENGTH_SHORT).show();}}
        if(request==20 && result==RESULT_OK){authenticated();handler.post(()->{if(!inBackground)sync();});}}
    PopupWindow moreMenu;
    void showMenu(View anchor){
        if(page!=1)return;
        if(moreMenu!=null && moreMenu.isShowing()){moreMenu.dismiss();return;}
        boolean hasPreviousSchedule=false;
        if(!selectedTerm.isEmpty())try{hasPreviousSchedule=academicStore.scheduleSnapshot(selectedTerm,"previous")!=null;}catch(Exception ignored){}
        moreMenu=MoreMenu.show(this,anchor,palette,hasPreviousSchedule,id->{switch(id){case 1:cancelSync();sync();break;case 2:changeWeek(currentWeek());break;case 3:calibrate();break;case 5:chooseWallpaper();break;case 6:restoreWallpaper();break;case 7:showTransparency();break;case 9:new ThemeColorSheet(this);break;case 12:chooseSemester(false);break;case 21:restoreSchedule();break;}});
    }
    void refreshAppearance(){
        wallpaper=WallpaperStore.load(this);int primary=prefs.getBoolean("manualTheme",false)?prefs.getInt("manualThemeColor",0xff2ecbff):backgroundPrimary();
        palette=AppTheme.from(this,primary);BG=palette.surface;INK=palette.text;MUTED=palette.muted;PRIMARY=palette.primary;ACCENT_TEXT=palette.accent;ON_PRIMARY=palette.onPrimary;
        prefs.edit().putInt("themeColor",PRIMARY).apply();
    }
    int backgroundPrimary(){return wallpaper==null?0xff2ecbff:WallpaperStore.dominant(wallpaper);}
    void chooseWallpaper(){if(imageBusy)return;if(weekHost!=null&&weekHost.getWidth()>0&&weekHost.getHeight()>0)wallpaperAspect=weekHost.getWidth()/(float)weekHost.getHeight();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(pick,30);}catch(ActivityNotFoundException e){Toast.makeText(this,"手机上没有可用的图片选择器",Toast.LENGTH_LONG).show();}}
    void importWallpaper(Uri uri){
        if(imageBusy)return;imageBusy=true;float aspect=weekHost!=null&&weekHost.getWidth()>0&&weekHost.getHeight()>0?weekHost.getWidth()/(float)weekHost.getHeight():wallpaperAspect;
        try{startActivityForResult(new Intent(this,WallpaperCropActivity.class).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("aspect",aspect),31);}catch(ActivityNotFoundException e){imageBusy=false;Toast.makeText(this,"裁切页面暂时无法打开",Toast.LENGTH_LONG).show();}
    }
    void followBackgroundTheme(){prefs.edit().putBoolean("manualTheme",false).remove("manualThemeColor").apply();}
    void restoreWallpaper(){if(imageBusy)return;WallpaperStore.clear(this);prefs.edit().remove("wallpaperUserCrop").apply();followBackgroundTheme();refreshAppearance();render();Toast.makeText(this,"已恢复默认背景与蓝色主题",Toast.LENGTH_SHORT).show();}
    int textOn(int background){return ThemePalette.neutralText(background);}
    LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    Schedule activeSchedule(){return schedule==null?(selectedTerm.isEmpty()?localSchedule:Schedule.local(selectedTerm)):schedule;}
    int maxWeek(){int max=activeSchedule().maxWeek;for(Course c:localCourses)if(c.term.equals("*")||c.term.equals(activeSchedule().term))for(int w:c.weeks)max=Math.max(max,w);return max;}
    String anchorKey(){return "monday:"+activeSchedule().term;}
    LocalDate anchor(){try{return LocalDate.parse(prefs.getString(anchorKey(),""));}catch(Exception e){return null;}}
    int currentWeek(){LocalDate a=anchor();if(a==null)return 1;long w=ChronoUnit.DAYS.between(a,today())/7+1;if(today().isBefore(a))return 1;return (int)Math.max(1,Math.min(maxWeek(),w));}
    void calibrate(){new CalibrationSheet(this);}
    void switchPage(int target){if(target==page)return;if(page==4)cancelSync();handler.removeCallbacks(examClock);transitionFrom=page;page=target;render();transitionFrom=-1;if(page==4)handler.postDelayed(examClock,30000);}
    void render(){
        finishWeekTransition();weekHost=null;
        if(pageAnimator!=null){pageAnimator.cancel();pageAnimator=null;}
        visibleCards.clear();selectedWeek=Math.max(1,Math.min(selectedWeek,maxWeek()));
        if(moreMenu!=null && moreMenu.isShowing())moreMenu.dismiss();
        root.setBackgroundColor(BG);AppTheme.applySystemBars(this,palette);
        if(screen!=null)root.removeView(screen);authStatusText=null;
        screen=column();screen.setPadding(dp(10),dp(3),dp(10),dp(3));root.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout mast=row();mast.setMinimumHeight(dp(48));LinearLayout brand=column();brand.addView(label(page==4?"考试查询":page==3?"成绩":page==2?"个人":"三角狐",25,INK,true));mast.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        updateDot=null;if(page==2){FrameLayout about=new FrameLayout(this);about.addView(iconButton("关于",16,()->startActivity(new Intent(this,AboutActivity.class))),new FrameLayout.LayoutParams(dp(48),dp(48)));updateDot=label("●",11,palette.error,true);updateDot.setContentDescription("有新版本");FrameLayout.LayoutParams dotSize=new FrameLayout.LayoutParams(dp(14),dp(14),Gravity.TOP|Gravity.RIGHT);dotSize.topMargin=dp(3);about.addView(updateDot,dotSize);mast.addView(about,new LinearLayout.LayoutParams(dp(48),dp(48)));updateBadge();}
        if(page==3||page==4)mast.addView(themedButton("返回首页",()->switchPage(0),false),new LinearLayout.LayoutParams(-2,dp(40)));
        if(page==1){TextView menu=label("⋮",28,ACCENT_TEXT,true);menu.setGravity(Gravity.CENTER);menu.setContentDescription("更多选项");menu.setFocusable(true);menu.setOnClickListener(v->showMenu(v));mast.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));}screen.addView(mast);
        LinearLayout pageContent=column();screen.addView(pageContent,new LinearLayout.LayoutParams(-1,0,1));
        if(page==1||page==3||page==4){
            String termName=page==4?(examTerm.isEmpty()?"学校当前学期":examTerm):page==3?(gradeTerm.isEmpty()?"选择学期":gradeTerm):(selectedTerm.isEmpty()?"选择学期":selectedTerm);
            pageContent.addView(semesterControl(termName,page==4?"选择考试学期":"选择学期",()->{if(page==4)chooseExamTerm();else chooseSemester(page==3);}),new LinearLayout.LayoutParams(-1,dp(44)));
        }else{
            TextView subtitle=label(page==0?"悠悠不山山 含含是散散":"教务账号与本机设置",12,MUTED,false);
            subtitle.setPadding(page==0?0:dp(8),0,dp(8),dp(5));
            pageContent.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
        }
        if(page==1){
            if(scheduleCandidate!=null)pageContent.addView(themedButton("发现课表更新 · 点击核对",()->reviewSchedule(),false),new LinearLayout.LayoutParams(-1,dp(40)));
            LinearLayout weekBar=row();previousWeekButton=button("‹",()->changeWeek(selectedWeek-1));previousWeekButton.setContentDescription("上一周");weekBar.addView(previousWeekButton,new LinearLayout.LayoutParams(dp(44),dp(44)));
            weekTitle=button("",()->chooseWeek());weekTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);weekBar.addView(weekTitle,new LinearLayout.LayoutParams(0,dp(44),1));nextWeekButton=button("›",()->changeWeek(selectedWeek+1));nextWeekButton.setContentDescription("下一周");weekBar.addView(nextWeekButton,new LinearLayout.LayoutParams(dp(44),dp(44)));pageContent.addView(weekBar);updateWeekTitle();
            weekHost=new WeekSwipeLayout(this,new WeekSwipeLayout.Listener(){public void drag(float offset){dragWeek(offset);}public void release(int direction){releaseWeek(direction);}});weekHost.addView(weekView(),new FrameLayout.LayoutParams(-1,-1));pageContent.addView(weekHost,new LinearLayout.LayoutParams(-1,0,1));
        }else{
            ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.addView(page==0?homeView():page==4?examsView():page==3?gradesView():userView());pageContent.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        }
        stateText=label(page==4?examState:page==3?gradeState:state,10,MUTED,false);stateText.setPadding(dp(2),dp(5),dp(2),dp(4));stateText.setMaxLines(2);screen.addView(stateText);
        LinearLayout nav=row();nav.setPadding(dp(5),dp(3),dp(5),dp(3));nav.setBackground(shape(palette.sheetSurface,22));
        final GradientDrawable[] tabBackgrounds=new GradientDrawable[3];final MoreMenu.Icon[] tabIcons=new MoreMenu.Icon[3];final TextView[] tabLabels=new TextView[3];
        String[] titles={"首页","课程","个人"};for(int i=0;i<3;i++){
            final int target=i;boolean selected=(page>=3?0:page)==i;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(0,dp(3),0,dp(3));item.setBaselineAligned(false);tabBackgrounds[i]=shape(selected?palette.selectedSurface:Color.TRANSPARENT,16);item.setBackground(tabBackgrounds[i]);
            MoreMenu.Icon icon=new MoreMenu.Icon(this,i+2,selected?INK:MUTED);item.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(30)));TextView navLabel=label(titles[i],11,selected?INK:MUTED,selected);navLabel.setGravity(Gravity.CENTER);navLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);navLabel.setIncludeFontPadding(false);navLabel.setSingleLine(true);item.addView(navLabel,new LinearLayout.LayoutParams(-1,dp(18)));item.setSelected(selected);item.setContentDescription(titles[i]+(selected?"，已选中":""));item.setFocusable(true);tabIcons[i]=icon;tabLabels[i]=navLabel;item.setOnClickListener(v->switchPage(target));nav.addView(item,new LinearLayout.LayoutParams(0,dp(54),1));
        }screen.addView(nav,new LinearLayout.LayoutParams(-1,dp(60)));
        if(transitionFrom>=0&&android.animation.ValueAnimator.areAnimatorsEnabled()){
            final int from=transitionFrom>=3?0:transitionFrom,to=page>=3?0:page;final int selectedColor=palette.selectedSurface;final float offset=dp(10)*(to>from?1:-1);
            android.animation.ArgbEvaluator evaluator=new android.animation.ArgbEvaluator();pageAnimator=android.animation.ValueAnimator.ofFloat(0,1);pageAnimator.setDuration(200);pageAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            pageAnimator.addUpdateListener(animation->{float t=(Float)animation.getAnimatedValue();pageContent.setAlpha(.2f+.8f*t);pageContent.setTranslationX(offset*(1-t));for(int i=0;i<3;i++){tabBackgrounds[i].setColor((Integer)evaluator.evaluate(t,i==from?selectedColor:selectedColor&0xffffff,i==to?selectedColor:selectedColor&0xffffff));int color=(Integer)evaluator.evaluate(t,i==from?INK:MUTED,i==to?INK:MUTED);tabIcons[i].setColor(color);tabLabels[i].setTextColor(color);}});pageAnimator.start();
        }
    }
    @Override public void onBackPressed(){if(page>=3){switchPage(0);return;}super.onBackPressed();}
    void updateWeekTitle(){
        String mid="第 "+selectedWeek+" 周";LocalDate a=anchor();
        if(a!=null)mid+="  ·  "+a.plusWeeks(selectedWeek-1).format(DateTimeFormatter.ofPattern("M/d"))+"—"+a.plusWeeks(selectedWeek-1).plusDays(6).format(DateTimeFormatter.ofPattern("M/d"));else mid+="  ·  待校准";
        weekTitle.setText(mid);previousWeekButton.setEnabled(selectedWeek>1);previousWeekButton.setAlpha(selectedWeek>1?1:.35f);nextWeekButton.setEnabled(selectedWeek<maxWeek());nextWeekButton.setAlpha(selectedWeek<maxWeek()?1:.35f);
    }
    String scheduleStageLabel(String stage){
        switch(stage){case "schedule-menu":return "正在打开教学安排";case "schedule-tab":return "正在打开个人课表";case "schedule-form":return "等待课表查询表单";case "term-loading":case "term-switching":return "正在切换目标学期";case "list-switching":return "正在切换列表视图";case "list-switch-failed":return "列表视图未能切换";case "report-pagination":return "等待课表所有分页";case "report-not-found":return "等待课程列表";case "empty-unconfirmed":return "等待学校确认空课表";case "report-settling":return "正在校验课表完整性";case "term-mismatch":case "selection-changed":return "课表学期尚未匹配";case "table-structure":case "rows-unreadable":return "课表列表格式无法识别";case "report-error":return "学校课表查询失败";default:return "正在加载列表课表";}
    }
    void reviewSchedule(){
        final String term=selectedTerm;cancelSync();
        try{
            final JSONObject candidate=academicStore.scheduleSnapshot(term,"candidate");if(candidate==null){Toast.makeText(this,"暂无待确认的课表更新",Toast.LENGTH_SHORT).show();return;}
            final String fingerprint=ScheduleRevision.fingerprint(candidate);Schedule fresh=new Schedule(candidate);
            UiSheet sheet=new UiSheet(this,"确认课表更新",term,.84f);
            int oldCount=schedule==null?0:ScheduleRevision.courseCount(schedule.json),newCount=ScheduleRevision.courseCount(candidate);
            sheet.body.addView(label("当前 "+oldCount+" 门 → 新课表 "+newCount+" 门",19,INK,true));space(sheet.body,10);
            sheet.body.addView(label("确认后替换学校课表，自定义课程不受影响。可在更多菜单恢复上一份。",13,MUTED,false));space(sheet.body,14);
            JSONArray issues=candidate.optJSONArray("issues");if(issues!=null&&issues.length()>0){sheet.body.addView(label("有 "+issues.length()+" 行未能解析，请先核对",15,palette.error,true));for(int i=0;i<issues.length();i++){JSONObject issue=issues.getJSONObject(i);space(sheet.body,6);sheet.body.addView(label(issue.optString("name")+" · "+issue.optString("reason")+"\n"+issue.optString("arrangement"),12,MUTED,false));}space(sheet.body,14);}
            for(Course c:fresh.courses){sheet.body.addView(label(c.name,15,INK,true));space(sheet.body,4);sheet.body.addView(label("周"+"一二三四五六日".charAt(c.day-1)+" · "+c.start+"—"+c.end+" 节 · "+c.weekText+" 周\n"+c.teacher+" · "+c.room,12,MUTED,false));space(sheet.body,12);}
            JSONArray extras=candidate.optJSONArray("unscheduled");if(extras!=null&&extras.length()>0){sheet.body.addView(label("无固定上课安排",15,INK,true));for(int i=0;i<extras.length();i++){JSONObject extra=extras.getJSONObject(i);space(sheet.body,6);sheet.body.addView(label(extra.optString("name")+" · "+extra.optString("note"),12,MUTED,false));}}
            TextView keep=themedButton("保留当前课表",()->{try{academicStore.discardSchedule(term);sheet.dialog.dismiss();loadSchedule();render();}catch(Exception e){Toast.makeText(this,"暂时无法保存选择，请重试",Toast.LENGTH_LONG).show();}},false);sheet.footer.addView(keep,new LinearLayout.LayoutParams(0,dp(48),1));
            TextView accept=themedButton("使用新课表",()->{try{academicStore.acceptSchedule(term,fingerprint);sheet.dialog.dismiss();loadSchedule();render();}catch(Exception e){Toast.makeText(this,"候选课表已变化或保存失败，请重新查看",Toast.LENGTH_LONG).show();}},true);LinearLayout.LayoutParams acceptSize=new LinearLayout.LayoutParams(0,dp(48),1);acceptSize.leftMargin=dp(10);sheet.footer.addView(accept,acceptSize);showSheet(sheet);
        }catch(Exception e){Toast.makeText(this,"候选课表读取失败，当前课表继续保留",Toast.LENGTH_LONG).show();}
    }
    void restoreSchedule(){
        final String term=selectedTerm;cancelSync();try{JSONObject previous=academicStore.scheduleSnapshot(term,"previous");if(previous==null){Toast.makeText(this,"尚无可恢复的上一份课表",Toast.LENGTH_SHORT).show();return;}
            UiSheet sheet=new UiSheet(this,"恢复上一份课表",term,.47f);sheet.body.addView(label("将恢复 "+stamp(previous.optLong("savedAt"))+" 保存的 "+ScheduleRevision.courseCount(previous)+" 门课程。",15,INK,false));sheet.actions(this,"恢复",()->{try{academicStore.restoreSchedule(term);sheet.dialog.dismiss();loadSchedule();render();}catch(Exception e){Toast.makeText(this,"恢复失败，当前课表继续保留",Toast.LENGTH_LONG).show();}});showSheet(sheet);
        }catch(Exception e){Toast.makeText(this,"上一份课表读取失败",Toast.LENGTH_LONG).show();}
    }
    void finishWeekTransition(){
        if(weekAnimator!=null){weekAnimator.removeAllUpdateListeners();weekAnimator.removeAllListeners();weekAnimator.cancel();weekAnimator=null;}
        if(weekHost!=null&&weekHost.getChildCount()>0){
            weekHost.transitioning=false;boolean commit=previewWeek!=0&&previewWeek==selectedWeek;
            View current=weekHost.getChildAt(commit?weekHost.getChildCount()-1:0);
            for(int i=weekHost.getChildCount()-1;i>=0;i--)if(weekHost.getChildAt(i)!=current)weekHost.removeViewAt(i);
            if(previewWeek!=0&&!commit){visibleCards.clear();visibleCards.addAll(previousCards);}
            current.setTranslationX(0);current.setAlpha(1);current.setScaleX(1);current.setScaleY(1);current.setLayerType(View.LAYER_TYPE_NONE,null);current.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        previewWeek=0;queuedWeek=0;weekProgress=0;previousCards.clear();
    }
    void changeWeek(int requested){
        int target=WeekSwipeGesture.target(selectedWeek,requested,maxWeek());if(page!=1||target==selectedWeek||weekHost==null)return;
        if(weekAnimator!=null){queuedWeek=target;return;}
        finishWeekTransition();prepareWeek(target);settleWeek(true);
    }
    void prepareWeek(int target){
        previewWeek=target;previousCards.clear();previousCards.addAll(visibleCards);visibleCards.clear();
        int current=selectedWeek;selectedWeek=target;View next=weekView();selectedWeek=current;
        weekHost.addView(next,new FrameLayout.LayoutParams(-1,-1));
        // Measure once before movement, then animate cached layers without relaying out cards.
        int w=weekHost.getWidth(),h=weekHost.getHeight();next.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));next.layout(0,0,w,h);
        for(int i=0;i<weekHost.getChildCount();i++){View v=weekHost.getChildAt(i);v.setLayerType(View.LAYER_TYPE_HARDWARE,null);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        moveWeek(0,target>current?1:-1);
    }
    void moveWeek(float progress,int direction){
        weekProgress=progress;if(weekHost==null||weekHost.getChildCount()<2)return;
        float distance=weekHost.getWidth()*direction;View old=weekHost.getChildAt(0),next=weekHost.getChildAt(1);
        old.setTranslationX(-distance*progress);next.setTranslationX(distance*(1-progress));
        old.setAlpha(1-.12f*progress);next.setAlpha(.88f+.12f*progress);
    }
    void dragWeek(float offset){
        if(weekHost==null||weekAnimator!=null||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        int direction=offset<0?1:-1,target=WeekSwipeGesture.target(selectedWeek,selectedWeek+direction,maxWeek());
        if(previewWeek!=0&&previewWeek!=target)finishWeekTransition();
        if(target==selectedWeek){weekHost.getChildAt(0).setTranslationX(Math.max(-dp(24),Math.min(dp(24),offset*.15f)));return;}
        if(previewWeek==0)prepareWeek(target);
        moveWeek(Math.min(.98f,Math.abs(offset)/Math.max(1,weekHost.getWidth())),direction);
    }
    void releaseWeek(int direction){
        if(weekHost==null||weekAnimator!=null)return;
        if(previewWeek==0){finishWeekTransition();if(direction!=0)changeWeek(selectedWeek+direction);return;}
        settleWeek(direction!=0&&Integer.signum(previewWeek-selectedWeek)==direction);
    }
    void settleWeek(boolean commit){
        final int direction=previewWeek>selectedWeek?1:-1;final float start=weekProgress,end=commit?1:0;
        if(commit){selectedWeek=previewWeek;updateWeekTitle();}
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()||weekHost.getWidth()==0){finishWeekTransition();return;}
        weekHost.transitioning=true;weekAnimator=android.animation.ValueAnimator.ofFloat(start,end);
        weekAnimator.setDuration((long)(180+180*Math.abs(end-start)));weekAnimator.setInterpolator(new android.view.animation.PathInterpolator(.22f,1f,.36f,1f));
        weekAnimator.addUpdateListener(a->moveWeek((Float)a.getAnimatedValue(),direction));
        weekAnimator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){int pending=queuedWeek;finishWeekTransition();if(pending!=0)changeWeek(pending);}});weekAnimator.start();
    }
    void chooseWeek(){
        UiSheet sheet=new UiSheet(this,"选择教学周",activeSchedule().term,.69f);
        int current=currentWeek();
        for(int start=1;start<=maxWeek();start+=4){LinearLayout line=row();
            for(int i=0;i<4;i++){final int week=start+i;LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)cell.leftMargin=dp(8);
                if(week>maxWeek()){line.addView(new View(this),cell);continue;}
                TextView choice=themedButton("第 "+week+" 周",()->{sheet.dialog.dismiss();changeWeek(week);},week==selectedWeek);choice.setPadding(0,0,0,0);choice.setSelected(week==selectedWeek);choice.setContentDescription("第 "+week+" 周"+(week==selectedWeek?"，已选中":"")+(week==current?"，当前周":""));line.addView(choice,cell);
            }sheet.body.addView(line);space(sheet.body,10);
        }
        sheet.actions(this,"回到本周",()->{sheet.dialog.dismiss();changeWeek(currentWeek());});showSheet(sheet);
    }
    void chooseSemester(boolean forGrades){
        UiSheet sheet=new UiSheet(this,"选择学期","选择后联网更新 · 各学期独立保存",.73f);
        if(terms.isEmpty()){sheet.body.addView(label("登录后即可读取教务系统提供的学期。",14,MUTED,false));}
        for(String name:terms){boolean chosen=Term.same(name,forGrades?gradeTerm:selectedTerm);
            TextView choice=themedButton(name+(chosen?"  ✓":""),()->{
                sheet.dialog.dismiss();cancelSync();
                if(forGrades){gradeTerm=name;prefs.edit().putString("gradeTerm",name).apply();loadGrades();}
                else{selectedTerm=name;prefs.edit().putString("selectedTerm",name).apply();loadSchedule();}
                render();sync();
            },chosen);choice.setTextSize(14);sheet.body.addView(choice,new LinearLayout.LayoutParams(-1,dp(52)));space(sheet.body,9);
        }
        sheet.actions(this,"刷新学期列表",()->{sheet.dialog.dismiss();cancelSync();sync();});showSheet(sheet);
    }
    void openGrades(){
        if(gradeTerm.isEmpty())gradeTerm=selectedTerm;loadGrades();cancelSync();switchPage(3);sync();
    }
    void openExams(){cancelSync();loadExams();switchPage(4);sync();}
    void chooseExamTerm(){
        UiSheet sheet=new UiSheet(this,"考试学期","各学期独立保存 · 自动合并所有考试轮次",.72f);
        if(examTerms.isEmpty())sheet.body.addView(label("联网获取后可选择学校开放的学期。",14,MUTED,false));
        for(int i=examTerms.size()-1;i>=0;i--){final String name=examTerms.get(i);boolean chosen=Term.same(name,examTerm);
            TextView choice=themedButton(name+(chosen?"  ✓":""),()->{sheet.dialog.dismiss();cancelSync();examTerm=name;prefs.edit().putString("examTerm",name).apply();loadExams();render();sync();},chosen);
            choice.setTextSize(14);sheet.body.addView(choice,new LinearLayout.LayoutParams(-1,dp(50)));space(sheet.body,9);
        }
        sheet.actions(this,"学校当前学期",()->{sheet.dialog.dismiss();cancelSync();examTerm="";prefs.edit().remove("examTerm").apply();loadExams();render();sync();});showSheet(sheet);
    }
    View examsView(){
        examBadges.clear();examCards.clear();examBadgeEntries.clear();
        LinearLayout content=column();content.setPadding(dp(6),dp(14),dp(6),dp(18));
        LinearLayout summary=panel(),heading=row(),words=column();ZonedDateTime now=ZonedDateTime.now(Exams.ZONE);int pending=0;
        if(exams!=null)for(Exams.Entry e:exams.entries)if(!e.finished(now))pending++;
        examPendingTitle=label(exams==null?"考试安排":pending+" 门待考",22,INK,true);words.addView(examPendingTitle);space(words,6);
        words.addView(label(exams==null?"获取后离线也能查看":"共 "+exams.entries.size()+" 门 · 按考试时间排列",12,MUTED,false));heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(iconButton("刷新考试安排",1,()->{cancelSync();sync();}),new LinearLayout.LayoutParams(dp(44),dp(44)));summary.addView(heading);content.addView(summary);space(content,14);
        if(exams==null||exams.entries.isEmpty()){
            LinearLayout empty=panel();empty.setPadding(dp(20),dp(36),dp(20),dp(36));TextView title=label(exams==null?"等待获取考试安排":"该学期暂无考试安排",18,INK,true);title.setGravity(Gravity.CENTER);empty.addView(title);space(empty,12);
            TextView note=label(exams==null?"使用教务账号登录后自动获取。\n已有记录会先显示，刷新失败仍可查看。":"学校尚未发布考试，或本学期没有安排。\n可稍后刷新，或切换其他学期。",13,MUTED,false);note.setGravity(Gravity.CENTER);note.setLineSpacing(dp(5),1);empty.addView(note);content.addView(empty);
            if(exams==null&&!prefs.getBoolean("loginCompleted",false)&&!canAutoLogin()){space(content,12);content.addView(themedButton("登录教务账号",()->showLogin(),true),new LinearLayout.LayoutParams(-1,dp(48)));}
        }else for(Exams.Entry e:exams.ordered(now)){
            LinearLayout card=panel();card.setPadding(dp(17),dp(18),dp(17),dp(18));examCards.add(card);LinearLayout first=row();
            LinearLayout details=column();TextView name=label(e.name.replaceFirst("^\\[[^\\]]+\\]\\s*",""),16,INK,true);name.setLineSpacing(dp(3),1);details.addView(name);space(details,9);
            TextView time=label(e.rawTime.isEmpty()?"考试时间待公布":e.rawTime,12,palette.deepAccent,true);time.setLineSpacing(dp(3),1);details.addView(time);first.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView badge=label("",13,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setIncludeFontPadding(false);badge.setLineSpacing(dp(4),1);badge.setPadding(dp(3),dp(5),dp(3),dp(5));
            LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(dp(66),dp(66));box.leftMargin=dp(12);first.addView(badge,box);examBadges.add(badge);examBadgeEntries.add(e);card.addView(first);space(card,13);
            card.addView(label("地点 · "+(e.room.isEmpty()?"待公布":e.room),13,MUTED,false));space(card,7);card.addView(label("座位 · "+(e.seat.isEmpty()||e.seat.matches("\\*+")?"待公布":e.seat),12,MUTED,false));
            if(!e.note.isEmpty()){space(card,7);card.addView(label("备注 · "+e.note,12,MUTED,false));}content.addView(card);space(content,11);
        }
        updateExamBadges();space(content,8);content.addView(label("考试安排以学校最新发布为准；倒计时按北京时间计算。",11,MUTED,false));return content;
    }
    void updateExamBadges(){
        ZonedDateTime now=ZonedDateTime.now(Exams.ZONE);
        if(exams!=null&&examPendingTitle!=null){int pending=0;for(Exams.Entry e:exams.entries)if(!e.finished(now))pending++;examPendingTitle.setText(pending+" 门待考");}
        for(int i=0;i<examBadges.size();i++){
            TextView badge=examBadges.get(i);Exams.Entry e=examBadgeEntries.get(i);int stateColor=e.finished(now)?0xff237d50:e.date==null?0xff64748b:0xffc83b48;
            badge.setText(e.badge(now));badge.setBackground(shape(stateColor,12));badge.setContentDescription(e.name+"，"+e.badge(now).replace('\n',' '));
            int surface=ThemePalette.mix(palette.panel,stateColor,palette.dark?.18:.10);
            GradientDrawable cardBackground=shape(surface,22);cardBackground.setStroke(dp(1),ThemePalette.mix(surface,stateColor,palette.dark?.28:.20));examCards.get(i).setBackground(cardBackground);
        }
    }
    View gradesView(){
        LinearLayout content=column();content.setPadding(dp(6),dp(14),dp(6),dp(18));
        LinearLayout summary=panel();LinearLayout heading=row();LinearLayout words=column();
        words.addView(label(grades==null?"学期成绩":grades.entries.size()+" 门课程",22,INK,true));space(words,6);words.addView(label(grades!=null&&!grades.effective?"旧版原始成绩 · 请刷新":"有效成绩 · 主修",12,MUTED,false));heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView summaryButton=themedButton("汇总",()->showGradeSummary(),false);summaryButton.setPadding(dp(10),0,dp(10),0);heading.addView(summaryButton,new LinearLayout.LayoutParams(dp(58),dp(36)));
        View refresh=iconButton("更新成绩",1,()->{cancelSync();sync();});LinearLayout.LayoutParams refreshSize=new LinearLayout.LayoutParams(dp(44),dp(44));refreshSize.leftMargin=dp(4);heading.addView(refresh,refreshSize);summary.addView(heading);content.addView(summary);space(content,14);
        if(grades==null||grades.entries.isEmpty()){
            LinearLayout empty=panel();empty.setPadding(dp(20),dp(35),dp(20),dp(35));TextView message=label(grades==null?"等待获取成绩":"该学期暂无成绩",18,INK,true);message.setGravity(Gravity.CENTER);empty.addView(message);space(empty,10);TextView note=label(grades==null?"登录后点击右上方刷新图标，成绩会保存在本机。":"可选择其他学期，或稍后更新。",13,MUTED,false);note.setGravity(Gravity.CENTER);empty.addView(note);content.addView(empty);
        }else for(Grades.Entry entry:grades.entries){
            LinearLayout card=panel();card.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout first=row();
            String name=entry.name.replaceFirst("^\\[[^\\]]+\\]\\s*","");TextView title=label(name,16,INK,true);title.setLineSpacing(dp(3),1);first.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout scoreBox=column();scoreBox.setGravity(Gravity.CENTER);scoreBox.setPadding(dp(10),dp(8),dp(10),dp(8));scoreBox.setBackground(shape(palette.entrySurface,13));scoreBox.setMinimumWidth(dp(64));
            TextView score=label(entry.score.isEmpty()?"待公布":entry.score,19,palette.deepAccent,true);score.setGravity(Gravity.CENTER);score.setMaxWidth(dp(105));scoreBox.addView(score);space(scoreBox,3);TextView point=label("绩点 "+displayValue(entry.point),11,ThemePalette.readable(MUTED,palette.entrySurface,4.5),false);point.setGravity(Gravity.CENTER);scoreBox.addView(point);
            LinearLayout.LayoutParams scoreSize=new LinearLayout.LayoutParams(-2,-2);scoreSize.leftMargin=dp(12);first.addView(scoreBox,scoreSize);card.addView(first);space(card,12);
            card.addView(label((entry.credits.isEmpty()?"—":entry.credits)+" 学分  ·  "+(entry.hours.isEmpty()?"—":entry.hours)+" 学时",13,palette.deepAccent,true));space(card,7);
            List<String> details=new ArrayList<>();for(String part:new String[]{entry.category,entry.nature,entry.assessment,entry.method})if(!part.isEmpty()&&!details.contains(part))details.add(part);
            if(!details.isEmpty())card.addView(label(String.join(" · ",details),12,MUTED,false));
            if(!entry.note.isEmpty()){space(card,7);card.addView(label("备注 · "+entry.note,12,INK,false));}
            content.addView(card);space(content,10);
        }
        return content;
    }
    String displayValue(String value){return value==null||value.trim().isEmpty()?"—":value;}
    View semesterControl(String termName,String actionName,Runnable action){
        LinearLayout control=row();control.setPadding(dp(14),0,dp(10),0);
        GradientDrawable surface=shape(palette.entrySurface,12);surface.setStroke(dp(1),palette.outline);
        control.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x22000000),surface,shape(palette.rippleMask,12)));
        TextView name=label(termName,14,palette.deepAccent,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER_VERTICAL);name.setIncludeFontPadding(false);
        control.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        View arrow=new View(this){final android.graphics.Paint pen=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);pen.setColor(palette.deepAccent);pen.setStyle(android.graphics.Paint.Style.STROKE);pen.setStrokeWidth(dp(2));pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                float x=getWidth()/2f,y=getHeight()/2f;canvas.drawLine(x-dp(5),y-dp(2),x,y+dp(3),pen);canvas.drawLine(x,y+dp(3),x+dp(5),y-dp(2),pen);}
        };arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);control.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(24)));
        control.setContentDescription(actionName+"，"+termName);control.setFocusable(true);control.setOnClickListener(v->action.run());return control;
    }
    View iconButton(String title,int id,Runnable action){
        FrameLayout hit=new FrameLayout(this);MoreMenu.Icon icon=new MoreMenu.Icon(this,id,palette.deepAccent);icon.setBackground(shape(palette.entrySurface,11));icon.setDuplicateParentStateEnabled(true);hit.addView(icon,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));hit.setContentDescription(title);hit.setFocusable(true);hit.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x22000000),null,shape(palette.rippleMask,12)));hit.setOnClickListener(v->action.run());return hit;
    }
    void showGradeSummary(){
        summaryAll=true;UiSheet sheet=new UiSheet(this,"成绩汇总","学校统计 · 主修有效成绩",.83f);gradeSummarySheet=sheet;
        TextView done=themedButton("知道了",()->sheet.dialog.dismiss(),true);sheet.footer.addView(done,new LinearLayout.LayoutParams(-1,dp(46)));showSheet(sheet);
        sheet.dialog.setOnDismissListener(d->{if(activeSheet==sheet.dialog)activeSheet=null;if(gradeSummarySheet==sheet)gradeSummarySheet=null;});
        refreshSummary();cancelSync();sync();
    }
    void refreshSummary(){
        UiSheet sheet=gradeSummarySheet;if(sheet==null||!sheet.dialog.isShowing())return;LinearLayout body=sheet.body;body.removeAllViews();
        LinearLayout tabs=row();TextView all=themedButton("入学以来",()->{summaryAll=true;refreshSummary();cancelSync();sync();},summaryAll),semester=themedButton("当前学期",()->{summaryAll=false;refreshSummary();if(grades==null||!grades.effective){cancelSync();sync();}},!summaryAll);
        tabs.addView(all,new LinearLayout.LayoutParams(0,dp(42),1));LinearLayout.LayoutParams tabSize=new LinearLayout.LayoutParams(0,dp(42),1);tabSize.leftMargin=dp(9);tabs.addView(semester,tabSize);body.addView(tabs);space(body,12);
        LinearLayout status=row();TextView scope=label(summaryAll?"入学以来":gradeTerm,12,MUTED,false);status.addView(scope,new LinearLayout.LayoutParams(0,-2,1));status.addView(iconButton("刷新汇总",1,()->{cancelSync();sync();}),new LinearLayout.LayoutParams(dp(44),dp(44)));body.addView(status);
        Grades data=summaryAll?summaryGrades:grades;
        if(data==null||data.total==null){space(body,24);TextView empty=label(data==null?(busy?"正在获取汇总数据":"暂无本地汇总"):"暂无可用的有效成绩汇总",15,INK,true);empty.setGravity(Gravity.CENTER);body.addView(empty);space(body,24);}
        else{
            Grades.Metric total=data.total;String[] labels={"平均成绩","平均学分绩点","加权平均成绩","已修学分"},values={total.average,total.gpa,total.weighted,total.earned};
            for(int r=0;r<2;r++){LinearLayout metrics=row();for(int col=0;col<2;col++){int i=r*2+col;LinearLayout metric=column();metric.setPadding(dp(14),dp(13),dp(14),dp(13));metric.setBackground(shape(palette.entrySurface,16));metric.addView(label(displayValue(values[i]),23,palette.deepAccent,true));space(metric,5);metric.addView(label(labels[i],11,ThemePalette.readable(MUTED,palette.entrySurface,4.5),false));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);if(col==1)size.leftMargin=dp(10);metrics.addView(metric,size);}body.addView(metrics);space(body,10);}
            space(body,10);body.addView(label("各环节统计",17,INK,true));space(body,12);
            LinearLayout columns=row();columns.addView(label("课程环节",11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));for(String name:new String[]{"已修学分","平均成绩"}){TextView title=label(name,11,MUTED,false);title.setGravity(Gravity.RIGHT);columns.addView(title,new LinearLayout.LayoutParams(dp(66),-2));}body.addView(columns);space(body,5);
            for(Grades.Metric group:data.groups){LinearLayout line=row();line.setPadding(0,dp(12),0,dp(12));TextView name=label(group.name,13,INK,false);name.setPadding(0,0,dp(8),0);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));for(String value:new String[]{group.earned,group.average}){TextView number=label(displayValue(value),14,palette.deepAccent,true);number.setGravity(Gravity.RIGHT);line.addView(number,new LinearLayout.LayoutParams(dp(66),-2));}body.addView(line);View rule=new View(this);rule.setBackgroundColor(palette.outline);body.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));}
        }
        space(body,12);TextView note=label(summaryAll?summaryState:gradeState,11,MUTED,false);note.setLineSpacing(dp(3),1);body.addView(note);
    }
    LinearLayout panel(){LinearLayout p=column();p.setPadding(dp(20),dp(20),dp(20),dp(20));p.setBackground(shape(palette.panel,22));return p;}
    void space(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    View homeView(){
        LinearLayout content=column();content.setPadding(dp(6),dp(16),dp(6),dp(18));TextView heading=label("常用入口",17,INK,true);content.addView(heading);space(content,14);
        String[] titles={"考试查询","自定义课程","成绩查看","校园地图","校历","网上报修","电费","大物实验报告"};
        String[] subtitles={"考试安排 · 倒计时","添加与管理","按学期查看","探索校园","2026—2027 学年","校园后勤服务","电量查询 · 在线缴费","需连接校园网"};
        int[] icons={19,8,11,13,14,15,17,18};Runnable[] actions={()->openExams(),()->showCustomCourses(),()->openGrades(),()->openCampusMap(),()->startActivity(new Intent(this,AcademicCalendarActivity.class)),()->openIdentity(true),()->openElectricity(),()->openPhysicsLab()};
        for(int i=0;i<titles.length;i+=2){LinearLayout line=row();for(int j=i;j<i+2;j++){LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);if(j>i)cell.leftMargin=dp(12);line.addView(homeEntry(titles[j],subtitles[j],icons[j],actions[j]),cell);}content.addView(line);if(i+2<titles.length)space(content,12);}return content;
    }

    void openCampusMap(){
        startActivity(new Intent(this,CampusMapActivity.class));
    }
    void openPhysicsLab(){
        startActivity(new Intent(this,PhysicsLabActivity.class));
    }
    View homeEntry(String title,String subtitle,int iconId,Runnable action){
        LinearLayout entry=column();entry.setPadding(dp(16),dp(14),dp(16),dp(14));entry.setMinimumHeight(dp(112));entry.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x22000000),shape(palette.entrySurface,20),shape(palette.rippleMask,20)));
        MoreMenu.Icon icon=new MoreMenu.Icon(this,iconId,palette.deepAccent);entry.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));space(entry,8);entry.addView(label(title,15,INK,true));TextView note=label(subtitle,11,ThemePalette.readable(MUTED,palette.entrySurface,4.5),false);note.setPadding(0,dp(4),0,0);entry.addView(note);entry.setContentDescription(title+"，"+subtitle);entry.setFocusable(true);entry.setOnClickListener(v->action.run());return entry;
    }
    void showCustomCourses(){
        UiSheet sheet=new UiSheet(this,"自定义课程","独立保存 · 刷新不会覆盖",.76f);LinearLayout content=sheet.body;
        if(customReadError)content.addView(label("自定义课程文件读取失败，原文件已保留。",13,MUTED,false));
        else if(localCourses.isEmpty()){TextView empty=label("还没有自定义课程\n点击下方按钮，添加一门课程。",14,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(8),1);empty.setPadding(0,dp(35),0,dp(35));content.addView(empty);}
        for(Course c:localCourses){LinearLayout item=panel();item.setPadding(dp(16),dp(13),dp(16),dp(13));LinearLayout title=row();View swatch=new View(this);swatch.setBackground(shape(courseColor(c),4));title.addView(swatch,new LinearLayout.LayoutParams(dp(7),dp(28)));TextView name=label(c.name,16,INK,true);name.setPadding(dp(10),0,0,0);title.addView(name);item.addView(title);space(item,8);item.addView(label("周"+"一二三四五六日".charAt(c.day-1)+" · "+c.start+"–"+c.end+" 节 · "+c.weekText+" 周",12,MUTED,false));item.addView(label((c.term.equals("*")?"所有学期":c.term)+(c.room.isEmpty()?"":" · "+c.room),12,MUTED,false));item.setOnClickListener(v->{sheet.dialog.dismiss();detail(c);});item.setFocusable(true);item.setContentDescription(c.name+"，自定义课程，点按管理");content.addView(item);space(content,10);}
        sheet.actions(this,"＋ 添加课程",()->{sheet.dialog.dismiss();editCustom(null);});showSheet(sheet);
    }
    String authStatus(){if(credentialBusy)return "正在处理本机凭证…";if(autoRunning)return "正在自动登录…";if(verified)return "已登录 · 教务连接正常";if(prefs.getBoolean("autoBlocked",false))return "需要重新登录或验证码";if(prefs.getBoolean("loginCompleted",false))return busy?"登录状态验证中…":"登录状态待联网验证";return "未登录";}
    View userView(){
        LinearLayout content=column();content.setPadding(dp(6),dp(12),dp(6),dp(18));LinearLayout user=panel();MoreMenu.Icon avatar=new MoreMenu.Icon(this,4,INK);avatar.setBackground(shape(palette.selectedSurface,20));user.addView(avatar,new LinearLayout.LayoutParams(dp(60),dp(60)));space(user,16);user.addView(label("教务账号",22,INK,true));space(user,8);authStatusText=label(authStatus(),15,INK,true);user.addView(authStatusText);
        long at=prefs.getLong("lastAuthAt",0);if(at>0){space(user,6);user.addView(label("最近验证 "+stamp(at),12,MUTED,false));}space(user,20);
        TextView login=button(verified?"重新登录 / 更换账号":"登录教务账号",()->showLogin());login.setBackground(shape(PRIMARY,16));login.setTextColor(ON_PRIMARY);user.addView(login,new LinearLayout.LayoutParams(-1,dp(48)));content.addView(user);space(content,16);
        LinearLayout storage=panel();Switch automatic=new Switch(this);SwitchTheme.apply(automatic,palette);automatic.setText("自动登录");automatic.setTextSize(16);automatic.setTextColor(INK);automatic.setChecked(prefs.getBoolean("autoLogin",true)&&CredentialStore.exists(this));automatic.setEnabled(!credentialBusy);storage.addView(automatic);space(storage,10);
        storage.addView(label(CredentialStore.exists(this)?"登录凭证已加密保存在本机。会话过期后尝试自动续登；验证码需手动填写。":"登录时开启「保存凭证并自动登录」，以后打开应用可自动续登。",13,MUTED,false));
        automatic.setOnCheckedChangeListener((v,enabled)->{if(enabled){prefs.edit().putBoolean("autoLogin",true).apply();showLogin();}else clearCredentials(false);});
        space(storage,18);TextView logout=themedButton("退出登录并清除凭证",()->logout(),false);logout.setEnabled(!credentialBusy);logout.setAlpha(credentialBusy?.45f:1f);storage.addView(logout,new LinearLayout.LayoutParams(-1,dp(48)));content.addView(storage);space(content,16);
        LinearLayout identity=panel();identity.addView(label("统一身份认证",22,INK,true));space(identity,8);
        android.content.SharedPreferences identityPrefs=getSharedPreferences("identity",MODE_PRIVATE);boolean savedIdentity=IdentityCredentialStore.exists(this);
        identity.addView(label(identityBusy?"正在清除统一认证会话…":identityPrefs.getBoolean("blocked",false)?"需要重新登录或完成学校验证":identityPrefs.getBoolean("completed",false)?"已有登录记录 · 使用时验证会话":savedIdentity?"已保存凭证 · 尚未验证":"未登录",14,INK,true));space(identity,8);
        identity.addView(label("用于服务大厅、网上报修、用电缴费，与教务账号分别管理。",13,MUTED,false));long identityAt=identityPrefs.getLong("lastAuthAt",0);if(identityAt>0){space(identity,6);identity.addView(label("最近验证 "+stamp(identityAt),12,MUTED,false));}space(identity,18);
        identity.addView(themedButton(savedIdentity?"登录 / 管理统一认证账号":"登录统一认证账号",()->openIdentity(false),true),new LinearLayout.LayoutParams(-1,dp(48)));space(identity,12);
        Switch identityAutomatic=new Switch(this);SwitchTheme.apply(identityAutomatic,palette);identityAutomatic.setText("统一认证自动登录");identityAutomatic.setTextSize(14);identityAutomatic.setTextColor(INK);identityAutomatic.setChecked(savedIdentity&&identityPrefs.getBoolean("autoLogin",false));identityAutomatic.setEnabled(!identityBusy);identity.addView(identityAutomatic);
        identityAutomatic.setOnCheckedChangeListener((v,enabled)->{if(enabled&&(!savedIdentity||!identityPrefs.getBoolean("completed",false))){identityAutomatic.setChecked(false);openIdentity(false);}else identityPrefs.edit().putBoolean("autoLogin",enabled).apply();});space(identity,10);
        identity.addView(label(savedIdentity?"统一认证凭证已单独加密保存在本机。":"登录时可选择保存凭证，方便下次使用。",12,MUTED,false));space(identity,14);
        identity.addView(themedButton("清除统一认证登录与凭证",()->clearIdentityPrompt(),false),new LinearLayout.LayoutParams(-1,dp(48)));content.addView(identity);space(content,16);
        content.addView(label("课表与自定义课程保存在本机，离线可查看。",12,MUTED,false));return content;
    }
    void openIdentity(boolean repair){if(identityBusy)return;Intent intent=new Intent(this,IdentityActivity.class);intent.putExtra("repair",repair);startActivity(intent);}
    void openElectricity(){if(identityBusy)return;startActivity(new Intent(this,IdentityActivity.class).putExtra("electricity",true));}
    void clearIdentityPrompt(){
        if(identityBusy)return;UiSheet sheet=new UiSheet(this,"清除统一认证？","教务账号和本地课程继续保留",.43f);sheet.body.addView(label("将清除本机统一认证凭证与校园服务会话，并关闭统一认证自动登录。",14,INK,false));
        sheet.actions(this,"确认清除",()->{sheet.dialog.dismiss();identityBusy=true;render();authIo.execute(()->{
            try{IdentityCredentialStore.clear(this);if(IdentityCredentialStore.exists(this))throw new IOException("Credentials remain");getSharedPreferences("identity",MODE_PRIVATE).edit().clear().apply();
                handler.post(()->{if(isDestroyed())return;SessionCookies.identity(()->{identityBusy=false;if(!isDestroyed()){render();Toast.makeText(this,"统一认证登录与凭证已清除",Toast.LENGTH_SHORT).show();}});});
            }catch(Exception e){handler.post(()->{identityBusy=false;if(!isDestroyed()){render();Toast.makeText(this,"凭证暂时无法清除，请重试",Toast.LENGTH_LONG).show();}});}
        });});showSheet(sheet);
    }
    void clearCredentials(boolean logout){
        if(credentialBusy)return;credentialBusy=true;generation++;busy=false;autoRunning=false;automaticCredentials=null;web.stopLoading();
        prefs.edit().putBoolean("autoLogin",false).apply();
        authIo.execute(()->{CredentialStore.clear(this);handler.post(()->{if(isDestroyed())return;
            if(logout){prefs.edit().putBoolean("loginCompleted",false).putBoolean("autoBlocked",false).remove("lastAuthAt").apply();verified=false;
                SessionCookies.teaching(()->{credentialBusy=false;setState("已退出登录 · 本地课程继续保留");render();});
            }else{credentialBusy=false;setState("自动登录已关闭，保存的凭证已清除");render();}
        });});render();
    }
    void logout(){
        if(credentialBusy)return;
        UiSheet sheet=new UiSheet(this,"退出登录？","清除这台设备上的登录凭证",.43f);
        LinearLayout summary=row();summary.setPadding(dp(15),dp(15),dp(15),dp(15));summary.setBackground(shape(palette.entrySurface,18));
        MoreMenu.Icon icon=new MoreMenu.Icon(this,10,palette.deepAccent);summary.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView message=label("退出后，自动登录将关闭。\n再次同步课程需要重新登录。",14,INK,false);message.setLineSpacing(dp(5),1);LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(0,-2,1);words.leftMargin=dp(12);summary.addView(message,words);sheet.body.addView(summary);space(sheet.body,14);
        TextView note=label("已保存的课表、自定义课程和背景都会保留。",12,MUTED,false);note.setLineSpacing(dp(4),1);sheet.body.addView(note);
        sheet.actions(this,"确认退出",()->{sheet.dialog.dismiss();clearCredentials(true);});showSheet(sheet);
    }
    int courseColor(Course c){String key="course-color:"+c.name;int index=prefs.getInt(key,-1);if(index<0){index=prefs.getInt("next-course-color",0);prefs.edit().putInt(key,index).putInt("next-course-color",index+1).apply();}return CourseColors.PALETTE[Math.floorMod(index,CourseColors.PALETTE.length)];}
    View weekView(){
        WeekGridView grid=new WeekGridView(this,dp(33),dp(32),dp(14));grid.setBackground(wallpaper==null?shape(palette.gridSurface,12):new WallpaperDrawable(wallpaper,dp(33),dp(32),palette.gridSurface,palette.wallpaperScrim,prefs.getBoolean("wallpaperUserCrop",false)));
        String[] days={"一","二","三","四","五","六","日"};LocalDate a=anchor();
        LocalDate monday=a==null?today().minusDays(today().getDayOfWeek().getValue()-1).plusWeeks(selectedWeek-currentWeek()):a.plusWeeks(selectedWeek-1);
        for(int d=1;d<=7;d++){
            LocalDate date=monday.plusDays(d-1);boolean isToday=date.equals(today());String text="周"+days[d-1]+"\n"+(isToday&&todayLabel?"今日":date.getDayOfMonth());
            TextView h=label(text,10,isToday?ThemePalette.neutralText(palette.deepAccent):INK,true);h.setGravity(Gravity.CENTER);h.setIncludeFontPadding(false);
            if(isToday){h.setBackground(shape(palette.deepAccent,9));final String weekday="周"+days[d-1];h.setContentDescription("今天，"+date+"，点按切换今日或日期");h.setFocusable(true);h.setOnClickListener(v->{todayLabel=!todayLabel;h.setText(weekday+"\n"+(todayLabel?"今日":date.getDayOfMonth()));});}
            else h.setBackground(shape(palette.gridSurface,7));
            grid.add(h,WeekGridView.HEADER,d,1,1,0,1);
        }
        for(int p=1;p<=12;p++){
            TextView time=label(p+"\n"+activeSchedule().starts[p]+"\n"+activeSchedule().ends[p],7,ThemePalette.readable(MUTED,palette.gridSurface,4.5),false);time.setAutoSizeTextTypeUniformWithConfiguration(5,8,1,android.util.TypedValue.COMPLEX_UNIT_SP);time.setContentDescription("第 "+p+" 节，"+activeSchedule().starts[p]+" 上课，"+activeSchedule().ends[p]+" 下课");time.setGravity(Gravity.CENTER);time.setIncludeFontPadding(false);time.setBackground(shape(palette.gridSurface,5));grid.add(time,WeekGridView.TIME,1,p,p,0,1);
            for(int d=1;d<=7;d++){View cell=new View(this);cell.setBackground(shape(wallpaper==null?BG:(palette.dark?0x15000000:0x15ffffff),5));grid.add(cell,WeekGridView.CELL,d,p,p,0,1);}
        }
        List<Course> courses=CustomCourses.at(activeSchedule().courses,localCourses,activeSchedule().term,selectedWeek);Map<Course,Integer> lanes=new IdentityHashMap<>(),counts=new IdentityHashMap<>();
        for(int d=1;d<=7;d++){List<Course> cluster=new ArrayList<>();int end=0;for(Course c:courses){if(c.day!=d)continue;if(!cluster.isEmpty()&&c.start>end){assign(cluster,lanes,counts);cluster.clear();}cluster.add(c);end=Math.max(cluster.size()==1?0:end,c.end);}assign(cluster,lanes,counts);}
        for(Course c:courses)for(int segmentStart=c.start;segmentStart<=c.end;segmentStart=((segmentStart-1)/4+1)*4+1){
            int segmentEnd=Math.min(c.end,((segmentStart-1)/4+1)*4);
            int cardColor=courseColor(c);CourseCardView card=new CourseCardView(this);card.setText(c.name+"\n"+c.room);card.setTextSize(10);card.setTextColor(INK);card.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));card.setTag(cardColor);visibleCards.add(card);card.setPadding(dp(2),dp(2),dp(2),dp(2));card.setGravity(Gravity.CENTER);card.setIncludeFontPadding(false);card.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);card.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
            card.setAutoSizeTextTypeUniformWithConfiguration(6,11,1,android.util.TypedValue.COMPLEX_UNIT_SP);styleCourseCard(card,cardColor,transparency());
            card.setContentDescription(c.name+"，"+c.room+"，第"+c.start+"至"+c.end+"节，点按查看完整详情");card.setFocusable(true);card.setOnClickListener(v->detail(c));grid.add(card,WeekGridView.CELL,c.day,segmentStart,segmentEnd,lanes.get(c),counts.get(c));
        }
        for(int after:new int[]{4,8}){String title=after==4?"午休":"晚餐";TextView gap=label(title+"  "+activeSchedule().ends[after]+"–"+activeSchedule().starts[after+1],8,palette.deepAccent,false);gap.setGravity(Gravity.CENTER);gap.setIncludeFontPadding(false);gap.setBackgroundColor(palette.controlSurface);grid.add(gap,WeekGridView.BREAK,1,after,after,0,1);}
        if(courses.isEmpty()){TextView free=label(schedule==null?"暂无该学期课表，联网后获取":"这一周没有课程安排",14,ACCENT_TEXT,true);free.setGravity(Gravity.CENTER);free.setShadowLayer(dp(3),0,0,palette.dark?Color.BLACK:Color.WHITE);grid.add(free,WeekGridView.EMPTY,1,1,12,0,1);}
        return grid;
    }
    void assign(List<Course> group,Map<Course,Integer> lanes,Map<Course,Integer> counts){List<Integer> ends=new ArrayList<>();for(Course c:group){int i=0;while(i<ends.size()&&ends.get(i)>=c.start)i++;if(i==ends.size())ends.add(c.end);else ends.set(i,c.end);lanes.put(c,i);}for(Course c:group)counts.put(c,ends.size());}
    void detail(Course c){CourseDetailSheet.show(this,c);}
    boolean saveCustom(List<Course> next){try{customStore.save(next);localCourses=next;render();return true;}catch(Exception e){Toast.makeText(this,"保存失败，原有自定义课程未改动",Toast.LENGTH_LONG).show();return false;}}
    void editCustom(Course existing){if(customReadError){Toast.makeText(this,"原自定义课程文件无法读取，暂时停止写入以免覆盖",Toast.LENGTH_LONG).show();return;}new CourseEditor(this,existing);}
    void showSheet(UiSheet sheet){if(activeSheet!=null)activeSheet.dismiss();activeSheet=sheet.dialog;sheet.dialog.setOnDismissListener(d->{if(activeSheet==sheet.dialog)activeSheet=null;});sheet.show(this);}
    int transparency(){return CourseAppearance.clamp(prefs.getInt("courseTransparency",0));}
    void styleCourseCard(CourseCardView card,int color,int transparency){card.setBackground(shape(CourseAppearance.background(color,transparency),6));card.setAppearance(color,transparency,BG);}
    void showTransparency(){
        UiSheet sheet=new UiSheet(this,"课程透明度","只调整课程底色，文字自动适应背景明暗",.56f);LinearLayout body=sheet.body;int initial=transparency();final int[] value={initial};
        FrameLayout preview=new FrameLayout(this);preview.setBackground(wallpaper==null?shape(palette.controlSurface,18):new WallpaperDrawable(wallpaper,0,0,BG,palette.wallpaperScrim,prefs.getBoolean("wallpaperUserCrop",false)));preview.setClipToOutline(true);
        LinearLayout examples=row();examples.setPadding(dp(16),dp(15),dp(16),dp(15));preview.addView(examples,new FrameLayout.LayoutParams(-1,-1));CourseCardView[] cards=new CourseCardView[3];String[] names={"高等数学\n教学楼 A101","大学英语\n教学楼 B202","自定义课程\n图书馆"};for(int i=0;i<3;i++){CourseCardView c=new CourseCardView(this);c.setText(names[i]);c.setTextSize(12);c.setGravity(Gravity.CENTER);c.setPadding(dp(5),dp(5),dp(5),dp(5));c.setIncludeFontPadding(false);c.setTag(CourseColors.PALETTE[i]);cards[i]=c;styleCourseCard(c,CourseColors.PALETTE[i],initial);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i>0)lp.leftMargin=dp(8);examples.addView(c,lp);}body.addView(preview,new LinearLayout.LayoutParams(-1,dp(116)));space(body,15);
        TextView percent=label("透明度 "+initial+"%",16,INK,true);percent.setGravity(Gravity.CENTER);body.addView(percent,new LinearLayout.LayoutParams(-1,-2));SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(initial);slider.setProgressTintList(android.content.res.ColorStateList.valueOf(PRIMARY));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(PRIMARY));slider.setContentDescription("课程卡片透明度，百分之零为不透明，百分之一百为底色完全透明");body.addView(slider,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout ends=row();TextView solid=label("不透明",11,MUTED,false),clear=label("全透明",11,MUTED,false);ends.addView(solid,new LinearLayout.LayoutParams(0,-2,1));clear.setGravity(Gravity.RIGHT);ends.addView(clear,new LinearLayout.LayoutParams(0,-2,1));body.addView(ends);space(body,12);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){value[0]=progress;percent.setText("透明度 "+progress+"%");for(CourseCardView card:cards)styleCourseCard(card,(Integer)card.getTag(),progress);for(CourseCardView card:visibleCards)styleCourseCard(card,(Integer)card.getTag(),progress);}});
        TextView reset=button("恢复不透明",()->slider.setProgress(0));body.addView(reset,new LinearLayout.LayoutParams(-1,dp(40)));
        sheet.actions(this,"保存设置",()->{prefs.edit().putInt("courseTransparency",value[0]).apply();sheet.dialog.dismiss();});showSheet(sheet);
        sheet.dialog.setOnDismissListener(d->{if(activeSheet==sheet.dialog)activeSheet=null;if(!isDestroyed())for(CourseCardView card:visibleCards)styleCourseCard(card,(Integer)card.getTag(),transparency());});
    }
    void place(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h);lp.leftMargin=x;lp.topMargin=y;parent.addView(v,lp);}
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,dp(44),1);}
    TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    TextView button(String s,Runnable action){TextView v=label(s,13,ACCENT_TEXT,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setMinHeight(dp(44));v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((PRIMARY&0xffffff)|0x33000000),shape(Color.TRANSPARENT,14),shape(palette.rippleMask,14)));v.setOnClickListener(x->action.run());v.setFocusable(true);v.setContentDescription(s);return v;}
    TextView themedButton(String text,Runnable action,boolean filled){
        TextView control=button(text,action);control.setTextColor(filled?ON_PRIMARY:palette.deepAccent);control.setIncludeFontPadding(false);control.setPadding(dp(14),0,dp(14),0);
        GradientDrawable surface=shape(filled?PRIMARY:palette.entrySurface,15);if(!filled)surface.setStroke(dp(1),palette.outline);
        control.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(((filled?ON_PRIMARY:PRIMARY)&0xffffff)|0x22000000),surface,shape(palette.rippleMask,15)));return control;
    }
    GradientDrawable shape(int c,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));return d;}
}
