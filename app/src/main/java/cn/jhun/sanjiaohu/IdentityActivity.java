package cn.jhun.sanjiaohu;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.net.http.SslError;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.*;

/** Separate native identity form and visible campus service browser. */
public final class IdentityActivity extends Activity {
    ThemePalette theme;
    SharedPreferences prefs;
    WebView web;
    FrameLayout body;
    ScrollView form;
    TextView status,subtitle,submit,errorText;
    EditText account,password,captcha;
    ImageView captchaImage;
    LinearLayout challenge,errorPanel;
    Switch remember;
    ProgressBar progress;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService vault=Executors.newSingleThreadExecutor();
    final IdentityNavigation navigation=new IdentityNavigation();
    final IdentityDiagnostics diagnostics=new IdentityDiagnostics();
    long lastAlipayLaunch;
    String adapter,documentProbe,lastDocument="",lastUrl="",pendingAccount,pendingPassword;
    boolean documentReady;
    int documentEpoch;
    boolean repair,electricity,loaded,attempting,submitted,saving,failed,autoTried,ticketSeen,manualPage,clearing;
    final Runnable pageTimeout=()->{if(!loaded&&!navigation.stopped&&!isDestroyed())networkError("学校页面加载超时，已停止加载。请点击重新加载后再试。");};
    long deadline,submittedAt;
    int generation;
    ValueCallback<Uri[]> fileCallback;
    Dialog webDialog;
    ElectricityPanel electricityPanel;
    boolean manualVerificationVisible;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        electricity=getIntent().getBooleanExtra("electricity",false);
        repair=!electricity&&getIntent().getBooleanExtra("repair",false);
        prefs=getSharedPreferences("identity",MODE_PRIVATE);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        try(InputStream in=getAssets().open("identity-login.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);adapter=out.toString("UTF-8");}catch(IOException e){finish();return;}
        try(InputStream in=getAssets().open("identity-document.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[2048];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);documentProbe=out.toString("UTF-8");}catch(IOException e){finish();return;}
        AppTheme.applySystemBars(this,theme);
        LinearLayout root=column();root.setBackgroundColor(theme.surface);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=row();header.setPadding(dp(18),dp(10),dp(18),dp(10));header.addView(action("‹",false,()->back()),new LinearLayout.LayoutParams(dp(44),dp(44)));header.getChildAt(0).setContentDescription("返回");
        LinearLayout titles=column();titles.setPadding(dp(14),0,dp(8),0);titles.addView(text(electricity?"电费":repair?"网上报修":"统一身份认证",20,theme.text,true));subtitle=text("江汉大学 · 校园服务",11,theme.muted,false);titles.addView(subtitle);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView manage=action("账号",false,()->manualLogin());header.addView(manage,new LinearLayout.LayoutParams(dp(52),dp(44)));View refresh=RefreshIconButton.create(this,theme,"重新加载",()->reload());LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(44),dp(44));rp.leftMargin=dp(8);header.addView(refresh,rp);root.addView(header);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(theme.primary));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));buildForm();buildError();createWeb();
        if(electricity)electricityPanel=new ElectricityPanel(this);
        setContentView(root);
        if(repair||electricity){showWeb();startPage(loginEntry());}else prepareLogin(false);
    }
    String loginEntry(){return electricity?IdentityPolicy.ELECTRICITY_LOGIN:IdentityPolicy.LOGIN;}
    void buildForm(){
        form=new ScrollView(this);form.setFillViewport(true);form.setBackgroundColor(theme.surface);form.setVerticalScrollBarEnabled(false);
        LinearLayout content=column();content.setPadding(dp(26),dp(22),dp(26),dp(24));form.addView(content);
        MoreMenu.Icon mark=new MoreMenu.Icon(this,4,theme.deepAccent);mark.setBackground(shape(theme.entrySurface,20));content.addView(mark,new LinearLayout.LayoutParams(dp(54),dp(54)));gap(content,18);
        content.addView(text("登录校园服务",25,theme.text,true));gap(content,8);content.addView(text("使用统一身份认证账号，\n与课表使用的教务账号分别保存。",14,theme.muted,false));gap(content,24);
        account=input("学号 / 工号",false);password=input("统一身份认证密码",true);content.addView(account,field());content.addView(password,field());
        challenge=column();captcha=input("验证码",false);captcha.setAutofillHints((String[])null);challenge.addView(captcha,field());captchaImage=new ImageView(this);captchaImage.setAdjustViewBounds(true);captchaImage.setScaleType(ImageView.ScaleType.FIT_CENTER);captchaImage.setContentDescription("学校验证码，点击重新加载登录页");captchaImage.setOnClickListener(v->reload());challenge.addView(captchaImage,new LinearLayout.LayoutParams(-1,dp(64)));gap(challenge,16);challenge.setVisibility(View.GONE);content.addView(challenge);
        remember=new Switch(this);SwitchTheme.apply(remember,theme);remember.setText("保存凭证并自动登录");remember.setTextSize(14);remember.setTextColor(theme.text);remember.setChecked(prefs.getBoolean("autoLogin",true));content.addView(remember);gap(content,20);
        submit=action("登录",true,()->login(false));content.addView(submit,new LinearLayout.LayoutParams(-1,dp(50)));gap(content,12);content.addView(action("在学校页面完成验证",false,()->officialPage()),new LinearLayout.LayoutParams(-1,dp(46)));
        status=text("凭证仅提交给学校统一身份认证平台。",13,theme.muted,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);gap(content,16);content.addView(status);gap(content,20);content.addView(text("凭证使用系统密钥加密保存在本机。\n网页验证码或额外验证需要你亲自完成。",12,theme.muted,false));body.addView(form,new FrameLayout.LayoutParams(-1,-1));
        gap(content,12);content.addView(action("连接诊断",false,()->showDiagnostics()),new LinearLayout.LayoutParams(-1,dp(42)));
    }
    void buildError(){errorPanel=column();errorPanel.setGravity(Gravity.CENTER);errorPanel.setPadding(dp(28),dp(24),dp(28),dp(24));errorPanel.setBackgroundColor(theme.surface);errorPanel.addView(text("校园服务暂时未能打开",20,theme.text,true));gap(errorPanel,12);errorText=text("请检查网络后重试。",14,theme.muted,false);errorText.setGravity(Gravity.CENTER);errorPanel.addView(errorText);gap(errorPanel,24);errorPanel.addView(action("重新加载",true,()->reload()),new LinearLayout.LayoutParams(dp(160),dp(46)));gap(errorPanel,12);errorPanel.addView(action("登录统一认证",false,()->manualLogin()),new LinearLayout.LayoutParams(dp(160),dp(46)));gap(errorPanel,12);errorPanel.addView(action("连接诊断",false,()->showDiagnostics()),new LinearLayout.LayoutParams(dp(160),dp(42)));errorPanel.setVisibility(View.GONE);body.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));}
    void createWeb(){
        web=new WebView(this);web.setBackgroundColor(theme.surface);body.addView(web,0,new FrameLayout.LayoutParams(-1,-1));setWebVisible(!backgroundElectricity());WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);CookieManager.getInstance().setAcceptCookie(true);
        settings.setUserAgentString(IdentityDiagnostics.browserAgent(WebSettings.getDefaultUserAgent(this)));
        web.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_UP)navigation.userGesture(SystemClock.elapsedRealtime());return false;});
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(!failed&&!navigation.stopped){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}}
            @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){showWebDialog(message,result,false);return true;}
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){showWebDialog(message,result,true);return true;}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(!IdentityPolicy.repair(view.getUrl())){callback.onReceiveValue(null);return true;}
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{startActivityForResult(pick,701);}catch(ActivityNotFoundException e){fileCallback.onReceiveValue(null);fileCallback=null;Toast.makeText(IdentityActivity.this,"未找到图片选择器",Toast.LENGTH_SHORT).show();}return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(navigation.stopped)return true;
                String url=request.getUrl().toString();
                if(request.isForMainFrame())diagnostics.add("GET".equals(request.getMethod())?IdentityDiagnostics.Event.GET:"POST".equals(request.getMethod())?IdentityDiagnostics.Event.POST:IdentityDiagnostics.Event.OTHER_METHOD,url,request.isRedirect()?1:0);
                // The direct CAS callback redirects to /wsbx/#/wybx, a directory returning 403.
                // Open the actual mobile HTML entry after that callback, preserving its cookies.
                if(!electricity&&request.isForMainFrame()&&"GET".equals(request.getMethod())&&IdentityPolicy.repairRoot(url)){view.loadUrl(IdentityPolicy.REPAIR);return true;}
                // Follow the school's real redirect without issuing another loadUrl.
                // Android cleartext permission is scoped to the exact school hosts.
                if(IdentityPolicy.allowed(url)||(electricity&&PaymentNavigation.alipayWeb(url))){if(request.isForMainFrame()){observeTicket(url);if(IdentityPolicy.parse(url).getHost()==null)diagnostics.add(IdentityDiagnostics.Event.LENIENT_URL,url,0);}return false;}
                if(PaymentNavigation.sourceAllowed(electricity,lastUrl,request.isForMainFrame(),request.getMethod())){
                    String link=PaymentNavigation.alipayLink(url);if(link!=null){openAlipay(link);return true;}
                }
                diagnostics.add(request.isForMainFrame()?IdentityDiagnostics.Event.BLOCKED_MAIN:IdentityDiagnostics.Event.BLOCKED_FRAME,url,0);
                if(request.isForMainFrame())networkError("学校跳转到了暂不支持的地址："+IdentityDiagnostics.route(url)+"。请复制连接诊断以便排查。");return true;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){
                // Manual verification may show the login page; hide subsequent service
                // redirects before the new document can draw its first frame.
                if(electricity&&IdentityPolicy.electricity(url))manualVerificationVisible=false;
                if(backgroundElectricity())setWebVisible(false);
                if(electricityPanel!=null)electricityPanel.invalidate();
                documentReady=false;documentEpoch++;diagnostics.add(IdentityDiagnostics.Event.START,url,0);
                if(!navigation.visit(url,SystemClock.elapsedRealtime())){networkError("学校页面出现重复跳转，已停止循环加载。请重新加载或返回后重试。");return;}
                // Observe a fresh CAS ticket only when it is redirected from the real IdP.
                observeTicket(url);
                if(!electricity&&IdentityPolicy.repairRoot(url)){view.loadUrl(IdentityPolicy.REPAIR);return;}
                lastUrl=url;loaded=false;failed=false;errorPanel.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);subtitle.setText("正在连接校园服务…");
                handler.removeCallbacks(pageTimeout);handler.postDelayed(pageTimeout,45000);
                if(IdentityPolicy.auth(url)&&!manualPage)showForm();
                else if(backgroundElectricity())electricityPanel.connecting();
            }
            @Override public void onPageFinished(WebView view,String url){
                if(failed||navigation.stopped||isFinishing()||!currentDocument(url))return;loaded=true;handler.removeCallbacks(pageTimeout);progress.setVisibility(View.INVISIBLE);subtitle.setText(electricity?"校园用电服务":repair?"校园后勤服务":"独立管理校园服务账号");
                documentReady=true;diagnostics.add(IdentityDiagnostics.Event.FINISH,url,0);probeDocument();
                // Let the school's desktop landing finish consuming its SSO session first.
                if(repair&&IdentityPolicy.desktopRepair(url)){showWeb();view.loadUrl(IdentityPolicy.REPAIR);return;}
                if(IdentityPolicy.auth(url)){prepareSchoolForm();if(!attempting&&!manualPage){showForm();inspect();if((repair||electricity)&&!autoTried)tryAutomatic();}}
                else if(electricity&&IdentityPolicy.electricity(url)){showWeb();if(ticketSeen)complete();if(electricityPanel!=null)electricityPanel.ready(url);}
                else if(electricity&&PaymentNavigation.alipayWeb(url)){showWeb();}
                else if(!electricity&&ticketSeen&&(IdentityPolicy.hall(url)||IdentityPolicy.repairLanding(url))){complete();}
                else if(!electricity&&IdentityPolicy.hall(url)){verifyHall(0);}
                else if(!IdentityPolicy.auth(url)&&manualPage){showWeb();}
            }
            @Override public void onPageCommitVisible(WebView view,String url){if(!navigation.stopped&&currentDocument(url)){documentReady=true;diagnostics.add(IdentityDiagnostics.Event.COMMIT,url,0);probeDocument();if(IdentityPolicy.auth(url))prepareSchoolForm();}}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){diagnostics.add(IdentityDiagnostics.Event.ERROR,request.getUrl().toString(),error.getErrorCode());networkError("请检查网络连接；部分校园服务可能需要连接校园网后使用。");}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()){diagnostics.add(IdentityDiagnostics.Event.HTTP_ERROR,request.getUrl().toString(),response.getStatusCode());networkError("学校服务暂时无法访问（"+response.getStatusCode()+"），请稍后重试。");}}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler h,SslError e){h.cancel();diagnostics.add(IdentityDiagnostics.Event.SSL_ERROR,e.getUrl(),e.getPrimaryError());networkError("学校安全连接失败，请检查手机日期或稍后重试。");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){body.removeView(view);view.destroy();web=null;networkError("校园服务页面已中断，请重新加载。");return true;}
        });
    }
    JSONObject waitingState(){JSONObject state=new JSONObject();try{state.put("state","waiting");}catch(Exception ignored){}return state;}
    void probeDocument(){
        if(web==null||documentProbe==null)return;final int epoch=documentEpoch;final String address=web.getUrl();
        web.evaluateJavascript(documentProbe,value->{if(isDestroyed()||epoch!=documentEpoch)return;try{JSONObject state=new JSONObject(value);String origin=state.optString("originKind");if(!origin.equals("https-auth")&&!origin.equals("http-auth"))origin="other";String ready=state.optString("ready");if(!ready.equals("complete")&&!ready.equals("interactive"))ready="loading";lastDocument="页面来源="+origin+"，DOM="+ready+"，登录路径="+state.optBoolean("login")+"，登录表单="+state.optBoolean("form");diagnostics.add(IdentityDiagnostics.Event.DOCUMENT,address,state.optBoolean("login")&&state.optBoolean("form")?(origin.equals("https-auth")?1:origin.equals("http-auth")?2:0):0);}catch(Exception ignored){lastDocument="无法读取页面状态";}});
    }
    String diagnosticsReport(){
        String version="未知";try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);version=info.versionName+" ("+info.versionCode+")";}catch(Exception ignored){}
        android.content.pm.PackageInfo engine=WebView.getCurrentWebViewPackage();
        return "三角狐 "+version+" · Android API "+Build.VERSION.SDK_INT+"\nWebView "+(engine==null?"未知":engine.versionName)+"\n浏览器模式：移动浏览器兼容\n"+lastDocument+"\n\n"+diagnostics.report();
    }
    void showDiagnostics(){
        if(webDialog!=null)webDialog.dismiss();Dialog dialog=new Dialog(this);webDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=column();panel.setPadding(dp(22),dp(22),dp(22),dp(20));panel.setBackground(shape(theme.surface,24));panel.addView(text("连接诊断",21,theme.text,true));gap(panel,8);panel.addView(text("仅含版本、跳转路径和状态码，不含账号密码及认证参数。",12,theme.muted,false));gap(panel,14);
        ScrollView scroll=new ScrollView(this);TextView details=text(diagnosticsReport(),12,theme.text,false);details.setTextIsSelectable(true);scroll.addView(details);panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(380),getResources().getDisplayMetrics().heightPixels/2)));gap(panel,16);LinearLayout buttons=row();buttons.addView(action("关闭",false,()->dialog.dismiss()),new LinearLayout.LayoutParams(0,dp(46),1));TextView copy=action("复制诊断",true,()->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("三角狐连接诊断",diagnosticsReport()));Toast.makeText(this,"诊断已复制",Toast.LENGTH_SHORT).show();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.leftMargin=dp(10);buttons.addView(copy,lp);panel.addView(buttons);dialog.setContentView(panel);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(520)),-2);
    }
    void prepareSchoolForm(){evaluate("prepare",null,null,null,result->{});}
    void observeTicket(String url){if(IdentityPolicy.auth(lastUrl)&&IdentityPolicy.allowed(url)&&!IdentityPolicy.auth(url)){String ticket=Uri.parse(url).getQueryParameter("ticket");if(ticket!=null&&ticket.startsWith("ST-"))ticketSeen=true;}}
    boolean currentDocument(String url){return web!=null&&url!=null&&web.getUrl()!=null&&url.split("#",2)[0].equals(web.getUrl().split("#",2)[0]);}
    void startPage(String url){documentReady=false;documentEpoch++;diagnostics.add(IdentityDiagnostics.Event.RETRY,url,0);navigation.begin(SystemClock.elapsedRealtime());ticketSeen=false;failed=false;loaded=false;lastUrl="";if(web==null)createWeb();web.stopLoading();web.loadUrl(url);}
    void openAlipay(String link){
        long now=SystemClock.elapsedRealtime();if(lastAlipayLaunch>0&&now-lastAlipayLaunch<2000)return;
        lastAlipayLaunch=now;handler.removeCallbacks(pageTimeout);progress.setVisibility(View.INVISIBLE);showWeb();
        // Construct a fresh intent, fixed to Alipay. Never launch a webpage's raw Intent.
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(PaymentNavigation.ALIPAY_PACKAGE);
        try{startActivity(intent);diagnostics.add(IdentityDiagnostics.Event.ALIPAY_OPEN,link,0);subtitle.setText("已打开支付宝，请在支付宝内确认付款");}
        catch(ActivityNotFoundException e){diagnostics.add(IdentityDiagnostics.Event.ALIPAY_UNAVAILABLE,link,0);subtitle.setText("未找到支付宝，可继续使用网页支付");Toast.makeText(this,"未找到可用的支付宝，请安装支付宝或在页面选择其他支付方式。",Toast.LENGTH_LONG).show();}
        catch(SecurityException e){diagnostics.add(IdentityDiagnostics.Event.ALIPAY_UNAVAILABLE,link,1);subtitle.setText("系统未允许打开支付宝");Toast.makeText(this,"系统未允许打开支付宝，请检查手机设置或使用网页支付。",Toast.LENGTH_LONG).show();}
    }
    void verifyHall(int retries){
        if(web==null||!IdentityPolicy.hall(web.getUrl()))return;int id=generation;
        // Some WebView versions omit intermediate redirect callbacks. Require a
        // real account/sign-out marker; merely arriving at the public hall is not success.
        String script;
        try(InputStream in=getAssets().open("identity-session.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[2048];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);script=out.toString("UTF-8");}catch(IOException e){authError("服务大厅识别组件读取失败，请重新进入。");return;}
        web.evaluateJavascript(script,value->{if(isDestroyed()||navigation.stopped||id!=generation||web==null||!IdentityPolicy.hall(web.getUrl()))return;if("true".equals(value))complete();else if(retries<10)handler.postDelayed(()->{if(id==generation)verifyHall(retries+1);},500);else{showWeb();status.setText("请在学校页面完成登录。");}});
    }
    void manualLogin(){prepareLogin(true);}
    void prepareLogin(boolean switchAccount){
        if(electricityPanel!=null&&!saving&&!clearing){electricityPanel.checkout=false;electricityPanel.invalidate();}
        if(saving||clearing)return;cancelAttempt();manualPage=false;autoTried=true;ticketSeen=false;clearing=true;showForm();submit.setEnabled(false);status.setText("正在准备统一身份认证…");if(web==null)createWeb();web.stopLoading();
        Runnable open=()->{if(isDestroyed())return;clearing=false;submit.setEnabled(true);startPage(loginEntry());};
        if(switchAccount){prefs.edit().putBoolean("completed",false).apply();SessionCookies.identity(open);}else open.run();
        if(IdentityCredentialStore.exists(this))vault.execute(()->{try{IdentityCredentialStore.Credentials saved=IdentityCredentialStore.load(this);handler.post(()->{if(!isDestroyed()&&account.getText().length()==0){account.setText(saved.account);password.setText(saved.password);}});}catch(Exception ignored){}});
    }
    void tryAutomatic(){
        autoTried=true;if(!prefs.getBoolean("autoLogin",false)||prefs.getBoolean("blocked",false)||!IdentityCredentialStore.exists(this))return;
        int id=generation;status.setText("正在使用本机统一认证凭证…");vault.execute(()->{try{IdentityCredentialStore.Credentials saved=IdentityCredentialStore.load(this);handler.post(()->{if(isDestroyed()||id!=generation||manualPage||attempting)return;account.setText(saved.account);password.setText(saved.password);remember.setChecked(true);login(true);});}catch(Exception e){handler.post(()->{if(!isDestroyed()){prefs.edit().putBoolean("blocked",true).apply();status.setText("本机凭证暂时无法读取，请手动登录。");}});}});
    }
    void login(boolean automatic){
        if(attempting||saving||clearing){Toast.makeText(this,"正在处理登录，请稍候",Toast.LENGTH_SHORT).show();return;}
        boolean retryPage=navigation.stopped;
        if(account.getText().toString().trim().isEmpty()){account.setError("请输入统一认证账号");return;}if(password.getText().length()==0){password.setError("请输入统一认证密码");return;}
        if(web==null)createWeb();pendingAccount=account.getText().toString().trim();pendingPassword=password.getText().toString();manualPage=false;ticketSeen=false;attempting=true;submitted=false;failed=false;setInputs(false);deadline=SystemClock.elapsedRealtime()+45000;int id=++generation;
        navigation.begin(SystemClock.elapsedRealtime());showForm();submit.setText("正在连接学校…");status.setText(automatic?"正在自动登录统一身份认证…":"正在连接学校登录表单…");
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(password.getWindowToken(),0);
        // A retry must not destroy the CAS form's session or wait on thousands of cookie callbacks.
        if(retryPage||!IdentityPolicy.auth(web.getUrl()))startPage(loginEntry());
        poll(id);
    }
    void poll(int id){
        if(id!=generation||!attempting||web==null)return;if(SystemClock.elapsedRealtime()>deadline){authError("登录超时，请检查网络或在学校页面完成验证。");return;}
        // onPageFinished waits for every image/resource and is not a form-readiness signal.
        if(!IdentityPolicy.auth(web.getUrl())){submit.setText(submitted?"正在完成认证…":"正在连接学校…");handler.postDelayed(()->poll(id),600);return;}
        evaluate("inspect",null,null,null,result->{
            if(id!=generation||!attempting)return;String state=result.optString("state");
            if(state.equals("ready")){
                showChallenge(result);
                if(!submitted){
                    if(result.optBoolean("captcha")&&captcha.getText().length()==0){authError("学校要求验证码，请填写后再登录。");return;}
                    submitted=true;submittedAt=SystemClock.elapsedRealtime();
                    submit.setText("正在提交登录…");status.setText("表单已就绪，正在通过学校页面提交…");
                    evaluate("submit",pendingAccount,pendingPassword,captcha.getText().toString().trim(),reply->{if(id!=generation)return;String outcome=reply.optString("state");if(outcome.equals("submitted")){submit.setText("正在验证账号…");status.setText("已提交，正在等待学校认证结果…");}else if(outcome.equals("waiting")){submitted=false;submit.setText("正在准备表单…");}else authError(reply.optString("error","学校表单未能提交，请重新加载后再试，或在学校页面完成验证。"));});
                }else if(SystemClock.elapsedRealtime()-submittedAt>1800&&!result.optString("error").isEmpty()){authError(result.optString("error"));return;}
                else if(SystemClock.elapsedRealtime()-submittedAt>12000){authError("学校尚未确认登录，请检查账号、密码或验证码。也可在学校页面完成验证。");return;}
            }else if(state.equals("unsupported")||state.equals("script_error")){authError(result.optString("error","学校登录表单暂不支持自动填写，请在学校页面完成验证。"));return;}
            else if(!submitted){submit.setText("正在准备表单…");status.setText("正在等待学校登录表单就绪…");}
            handler.postDelayed(()->poll(id),600);
        });
    }
    interface StateResult {void accept(JSONObject value);}
    void evaluate(String action,String user,String secret,String code,StateResult callback){
        if(web==null||navigation.stopped||!documentReady||!IdentityPolicy.auth(web.getUrl())){callback.accept(waitingState());return;}
        final int epoch=documentEpoch,attempt=generation;
        String call=adapter+"("+JSONObject.quote(action)+","+JSONObject.quote(user==null?"":user)+","+JSONObject.quote(secret==null?"":secret)+","+JSONObject.quote(code==null?"":code)+")";
        String script="(function(){try{return "+call+";}catch(e){return {state:'script_error',error:'学校登录脚本执行失败，请重新加载后再试。'};}})()";
        web.evaluateJavascript(script,value->{if(isDestroyed()||attempt!=generation)return;if(navigation.stopped||epoch!=documentEpoch){if(!"submit".equals(action))callback.accept(waitingState());return;}try{JSONObject result=new JSONObject(value);if("unsupported".equals(result.optString("state"))||"script_error".equals(result.optString("state"))){diagnostics.add(IdentityDiagnostics.Event.SCRIPT_ERROR,web.getUrl(),0);probeDocument();}callback.accept(result);}catch(Exception e){JSONObject result=new JSONObject();try{result.put("state","script_error").put("error","学校页面没有返回有效结果，请重新加载后再试。");}catch(Exception ignored){}callback.accept(result);}});
    }
    void inspect(){int id=generation;evaluate("inspect",null,null,null,value->{if(isDestroyed()||attempting||navigation.stopped||id!=generation)return;showChallenge(value);status.setText(value.optString("state").equals("ready")?"学校登录页面已就绪。":"可尝试登录，或在学校页面完成验证。");});}
    void showChallenge(JSONObject value){boolean required=value.optBoolean("captcha");challenge.setVisibility(required?View.VISIBLE:View.GONE);if(required){String data=value.optString("image");if(data.startsWith("data:image/png;base64,"))try{byte[] bytes=android.util.Base64.decode(data.substring(data.indexOf(',')+1),android.util.Base64.DEFAULT);captchaImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));}catch(Exception ignored){}else{captchaImage.setImageDrawable(null);status.setText("验证码图片暂时无法读取，可在学校页面完成验证。");}}}
    void authError(String message){cancelAttempt();prefs.edit().putBoolean("blocked",true).putBoolean("completed",false).apply();status.setText(message);showForm();Toast.makeText(this,message,Toast.LENGTH_LONG).show();form.post(()->form.smoothScrollTo(0,submit.getTop()));}
    void cancelAttempt(){generation++;attempting=false;submitted=false;pendingPassword=null;pendingAccount=null;setInputs(true);}
    void setInputs(boolean enabled){account.setEnabled(enabled);password.setEnabled(enabled);remember.setEnabled(enabled);submit.setEnabled(enabled&&!clearing);if(enabled)submit.setText("登录");}
    void officialPage(){
        if(clearing||saving)return;cancelAttempt();manualPage=true;manualVerificationVisible=true;ticketSeen=false;showWeb();if(web==null)createWeb();if(navigation.stopped||!IdentityPolicy.auth(web.getUrl()))startPage(loginEntry());
        Toast.makeText(this,"在学校页面亲自完成验证；网页中输入的密码不会被应用读取或保存",Toast.LENGTH_LONG).show();
    }
    void complete(){
        if(saving||!navigation.finishOnce())return;saving=true;generation++;attempting=false;ticketSeen=false;
        final String user=pendingAccount,secret=pendingPassword;final boolean keep=remember.isChecked()&&submitted&&user!=null&&secret!=null;
        final boolean preserve=user==null&&!manualPage&&IdentityCredentialStore.exists(this);final boolean previousAuto=prefs.getBoolean("autoLogin",false);
        pendingAccount=pendingPassword=null;password.setText("");submit.setEnabled(false);status.setText("认证成功，正在保存…");CookieManager.getInstance().flush();
        vault.execute(()->{
            boolean stored=preserve;try{if(keep){IdentityCredentialStore.save(this,user,secret);stored=true;}else if(!preserve){IdentityCredentialStore.clear(this);}}catch(Exception e){stored=false;IdentityCredentialStore.clear(this);}
            prefs.edit().putBoolean("completed",true).putBoolean("blocked",false).putBoolean("autoLogin",stored&&(keep||previousAuto)).putLong("lastAuthAt",System.currentTimeMillis()).apply();final boolean didStore=stored;
            handler.post(()->{if(isDestroyed())return;saving=false;setInputs(true);if(keep&&!didStore)Toast.makeText(this,"认证成功，但凭证保存失败，下次需要手动登录",Toast.LENGTH_LONG).show();
                if(electricity){manualPage=false;showWeb();if(electricityPanel!=null)electricityPanel.ready(web.getUrl());}else if(repair){manualPage=false;showWeb();if(!IdentityPolicy.repairLanding(web.getUrl()))web.loadUrl(IdentityPolicy.REPAIR);}else{Toast.makeText(this,didStore?"统一认证登录成功，凭证已单独加密保存":"统一认证登录成功",Toast.LENGTH_SHORT).show();finish();}
            });
        });
    }
    void networkError(String message){if(electricityPanel!=null)electricityPanel.invalidate();documentReady=false;documentEpoch++;probeDocument();navigation.stop();handler.removeCallbacks(pageTimeout);loaded=false;failed=true;ticketSeen=false;cancelAttempt();if(web!=null)web.stopLoading();progress.setVisibility(View.INVISIBLE);subtitle.setText("连接未完成");if(form.getVisibility()==View.VISIBLE){status.setText(message);}else{errorText.setText(message);errorPanel.setVisibility(View.VISIBLE);}}
    void reload(){if(clearing||saving)return;if(electricityPanel!=null&&electricityPanel.visible()&&electricityPanel.connected){electricityPanel.refresh();return;}if(electricityPanel!=null&&electricityPanel.back())return;cancelAttempt();errorPanel.setVisibility(View.GONE);if(form.getVisibility()==View.VISIBLE)status.setText("正在重新连接学校…");startPage(loginEntry());}
    boolean backgroundElectricity(){return electricity&&!manualVerificationVisible&&(electricityPanel==null||!electricityPanel.checkout);}
    void setWebVisible(boolean visible){if(web!=null){web.setAlpha(visible?1f:0f);web.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}}
    void showForm(){manualVerificationVisible=false;setWebVisible(false);if(electricityPanel!=null)electricityPanel.hide();form.setVisibility(View.VISIBLE);errorPanel.setVisibility(View.GONE);}
    void showWeb(){if(backgroundElectricity()&&electricityPanel!=null){if(electricityPanel.connected)electricityPanel.show();else electricityPanel.connecting();return;}if(electricityPanel!=null)electricityPanel.hide();form.setVisibility(View.GONE);errorPanel.setVisibility(View.GONE);setWebVisible(true);}
    void back(){if(saving)return;if(electricityPanel!=null){if(electricityPanel.visible()){finish();return;}if(electricityPanel.back())return;}if(!navigation.stopped&&form.getVisibility()!=View.VISIBLE&&web!=null&&web.canGoBack()){cancelAttempt();navigation.begin(SystemClock.elapsedRealtime());web.goBack();}else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(electricityPanel!=null&&electricityPanel.checkout&&lastAlipayLaunch>0){lastAlipayLaunch=0;electricityPanel.back();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=701||fileCallback==null)return;Uri[] uris=null;if(result==RESULT_OK&&data!=null){java.util.ArrayList<Uri> list=new java.util.ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri uri=data.getClipData().getItemAt(i).getUri();if("content".equals(uri.getScheme()))list.add(uri);}}else if(data.getData()!=null&&"content".equals(data.getData().getScheme()))list.add(data.getData());if(!list.isEmpty())uris=list.toArray(new Uri[0]);}fileCallback.onReceiveValue(uris);fileCallback=null;}
    @Override protected void onDestroy(){if(electricityPanel!=null)electricityPanel.destroy();generation++;handler.removeCallbacksAndMessages(null);pendingPassword=null;password.setText("");if(webDialog!=null)webDialog.dismiss();if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}if(web!=null){body.removeView(web);web.stopLoading();web.destroy();web=null;}vault.shutdown();super.onDestroy();}
    void showWebDialog(String message,JsResult result,boolean confirm){
        if(webDialog!=null)webDialog.dismiss();Dialog dialog=new Dialog(this);webDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=column();panel.setPadding(dp(24),dp(22),dp(24),dp(20));panel.setBackground(shape(theme.surface,24));panel.addView(text("校园服务提示",19,theme.text,true));gap(panel,14);TextView words=text(message.length()>800?message.substring(0,800):message,14,theme.text,false);ScrollView scroll=new ScrollView(this);scroll.addView(words);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(144)));gap(panel,18);LinearLayout buttons=row();final boolean[] handled={false};
        if(confirm){TextView cancel=action("取消",false,()->{handled[0]=true;result.cancel();dialog.dismiss();});buttons.addView(cancel,new LinearLayout.LayoutParams(0,dp(46),1));}TextView ok=action("确定",true,()->{handled[0]=true;result.confirm();dialog.dismiss();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.leftMargin=confirm?dp(10):0;buttons.addView(ok,lp);panel.addView(buttons);dialog.setContentView(panel);dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.setOnDismissListener(v->{if(!handled[0])result.cancel();});dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(420)),-2);
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void gap(LinearLayout v,int size){v.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    TextView action(String label,boolean primary,Runnable run){TextView v=text(label,label.length()==1?24:14,primary?theme.onPrimary:theme.deepAccent,true);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),shape(primary?theme.primary:theme.entrySurface,15),shape(theme.rippleMask,15)));v.setOnClickListener(view->run.run());return v;}
    EditText input(String hint,boolean secret){EditText v=new EditText(this);v.setHint(hint);v.setSingleLine(true);v.setTextSize(16);v.setTextColor(theme.text);v.setHintTextColor(theme.muted);v.setPadding(dp(16),0,dp(16),0);v.setBackground(shape(theme.entrySurface,16));v.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_NORMAL));v.setSaveEnabled(false);v.setAutofillHints(secret?View.AUTOFILL_HINT_PASSWORD:View.AUTOFILL_HINT_USERNAME);return v;}
    LinearLayout.LayoutParams field(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.bottomMargin=dp(14);return lp;}
    GradientDrawable shape(int color,int radius){GradientDrawable v=new GradientDrawable();v.setColor(color);v.setCornerRadius(dp(radius));return v;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
