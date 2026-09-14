package cn.jhun.sanjiaohu;

import android.app.*;
import android.os.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;

/** Native credentials form; the school page remains an invisible authentication engine. */
public final class LoginActivity extends Activity {
    static final String ENTRY="https://jwxt.jhun.edu.cn/cas/login.action";
    WebView engine;
    EditText account,password,captcha;
    TextView status,submit;
    ImageView captchaImage;
    LinearLayout challenge;
    ProgressBar progress;
    Switch remember;
    boolean completing=false;
    String submittedAccount,submittedPassword;
    final java.util.concurrent.ExecutorService vault=java.util.concurrent.Executors.newSingleThreadExecutor();
    ThemePalette theme;
    Handler handler=new Handler(Looper.getMainLooper());
    String stateScript,submitScript;
    boolean loaded=false,attempting=false,submitted=false;
    int attempt=0;
    long deadline,submittedAt;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=new ThemePalette(getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        try{stateScript=asset("login-state.js");submitScript=asset("login-submit.js");}catch(Exception e){finish();return;}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        engine=new WebView(this);engine.setAlpha(0);engine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);engine.setFocusable(false);root.addView(engine,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(theme.surface);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=column();content.setPadding(dp(28),dp(16),dp(28),dp(24));scroll.addView(content);
        TextView back=text("‹ 返回",14,theme.accent);back.setPadding(0,dp(12),0,dp(12));back.setOnClickListener(v->finish());content.addView(back);
        TextView brand=text("三角狐",34,theme.text);brand.setPadding(0,dp(36),0,dp(12));content.addView(brand);
        TextView title=text("登录教务账号",22,theme.text);content.addView(title);
        TextView intro=text("让课程与每一天，轻松同步。",14,theme.muted);intro.setPadding(0,dp(10),0,dp(30));content.addView(intro);
        account=input("账号 / 学号",false);password=input("密码",true);content.addView(account,fieldLayout());content.addView(password,fieldLayout());
        challenge=column();challenge.setVisibility(View.GONE);captcha=input("验证码",false);captcha.setInputType(InputType.TYPE_CLASS_TEXT);challenge.addView(captcha,fieldLayout());captchaImage=new ImageView(this);captchaImage.setAdjustViewBounds(true);captchaImage.setScaleType(ImageView.ScaleType.FIT_CENTER);captchaImage.setContentDescription("学校验证码，点击刷新");captchaImage.setOnClickListener(v->{if(trusted(engine.getUrl()))engine.evaluateJavascript("(function(){var p=document.getElementById('randpic');if(p)p.click();})()",x->inspectChallenge());});challenge.addView(captchaImage,new LinearLayout.LayoutParams(-1,dp(64)));content.addView(challenge);
        remember=new Switch(this);SwitchTheme.apply(remember,theme);remember.setText("保存凭证并自动登录");remember.setTextColor(theme.text);remember.setTextSize(14);remember.setChecked(getSharedPreferences("settings",MODE_PRIVATE).getBoolean("autoLogin",true));remember.setPadding(0,0,0,dp(18));content.addView(remember);
        submit=text("登 录",17,theme.onPrimary);submit.setGravity(Gravity.CENTER);submit.setBackground(shape(theme.primary));submit.setOnClickListener(v->login());content.addView(submit,new LinearLayout.LayoutParams(-1,dp(52)));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(ColorStateList.valueOf(theme.primary));progress.setVisibility(View.GONE);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(3));pp.topMargin=dp(12);content.addView(progress,pp);
        status=text("",13,theme.muted);status.setPadding(0,dp(16),0,dp(12));content.addView(status);
        TextView privacy=text("凭证加密保存在本机，仅用于学校登录。可在个人页关闭自动登录并清除凭证。",12,theme.muted);privacy.setPadding(0,dp(18),0,0);content.addView(privacy);
        setContentView(root);
        WebSettings settings=engine.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        engine.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){result.confirm();if(trusted(url)){error(message.length()>240?message.substring(0,240):message);inspectChallenge();}return true;}
        });
        engine.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,Bitmap icon){loaded=false;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){if(MainActivity.isSchoolHttps(r.getUrl().toString()))return false;if(r.isForMainFrame())error("学校登录跳转不受支持，请稍后重试。");return true;}
            @Override public void onPageFinished(WebView v,String url){loaded=true;if(trusted(url)&&"/frame/homes.action".equals(Uri.parse(url).getPath())){complete();return;}if(!attempting)inspectChallenge();}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){loaded=false;error("连接学校失败，请检查网络后重试。");}}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame()){loaded=false;error("学校服务暂不可用（"+e.getStatusCode()+"），请稍后重试。");}}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();loaded=false;error("学校安全连接失败，请检查手机日期或稍后重试。");}
        });
        if(CredentialStore.exists(this))vault.execute(()->{try{CredentialStore.Credentials savedCredentials=CredentialStore.load(this);handler.post(()->{if(!isDestroyed()&&!isFinishing()&&account.getText().length()==0){account.setText(savedCredentials.account);password.setText(savedCredentials.password);}});}catch(Exception ignored){}});
        // A manual login must not immediately reuse an existing session and dismiss the form.
        getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("loginCompleted",false).apply();
        submit.setEnabled(false);
        SessionCookies.teaching(()->{if(isDestroyed()||isFinishing())return;submit.setEnabled(true);engine.loadUrl(ENTRY);});
    }
    boolean trusted(String url){return MainActivity.isSchoolHttps(url)&&"jwxt.jhun.edu.cn".equals(Uri.parse(url).getHost());}
    void login(){
        if(attempting)return;
        if(account.getText().toString().trim().isEmpty()){account.setError("请输入账号");return;}
        if(password.getText().length()==0){password.setError("请输入密码");return;}
        attempting=true;submitted=false;account.setEnabled(false);password.setEnabled(false);remember.setEnabled(false);deadline=SystemClock.elapsedRealtime()+30000;int id=++attempt;submit.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setTextColor(theme.accent);status.setText("正在连接学校…");
        if(!loaded)engine.loadUrl(ENTRY);poll(id);
    }
    void poll(int id){
        if(id!=attempt||!attempting)return;
        if(SystemClock.elapsedRealtime()>deadline){error("登录超时，请检查网络后重试。");engine.stopLoading();return;}
        if(!loaded||!trusted(engine.getUrl())){handler.postDelayed(()->poll(id),600);return;}
        engine.evaluateJavascript(stateScript,value->{
            if(id!=attempt||!attempting)return;
            try{
                JSONObject state=new JSONObject(value);String kind=state.optString("state");
                if(kind.equals("success")){complete();return;}
                if(kind.equals("ready")){
                    showChallenge(state);
                    if(!submitted){
                        if(state.optBoolean("captcha")&&captcha.getText().length()==0){error("学校要求验证码，请输入后登录。");return;}
                        submitted=true;submittedAt=SystemClock.elapsedRealtime();status.setText("正在验证账号…");
                        submittedAccount=account.getText().toString().trim();submittedPassword=password.getText().toString();
                        String call=submitScript.trim()+"("+JSONObject.quote(submittedAccount)+","+JSONObject.quote(submittedPassword)+","+JSONObject.quote(captcha.getText().toString().trim())+")";
                        engine.evaluateJavascript(call,response->{if(id!=attempt)return;try{JSONObject r=new JSONObject(response);if(r.has("error")){error(r.optString("error").equals("captcha")?"请输入学校验证码":"学校登录表单发生变化，请稍后重试。");inspectChallenge();}}catch(Exception e){error("登录请求未成功发送，请重试。");}});
                    }else if(SystemClock.elapsedRealtime()-submittedAt>10000){error("登录未完成，请检查账号、密码或验证码。");return;}
                }
                handler.postDelayed(()->poll(id),600);
            }catch(Exception e){error("学校登录页面暂时无法读取，请重试。");}
        });
    }
    void inspectChallenge(){if(!loaded||!trusted(engine.getUrl()))return;engine.evaluateJavascript(stateScript,value->{if(isDestroyed())return;try{JSONObject state=new JSONObject(value);if(state.optString("state").equals("ready"))showChallenge(state);}catch(Exception ignored){}});}
    void showChallenge(JSONObject s){boolean required=s.optBoolean("captcha");challenge.setVisibility(required?View.VISIBLE:View.GONE);if(required){String image=s.optString("image");if(image.startsWith("data:image/png;base64,")){byte[] bytes=android.util.Base64.decode(image.substring(image.indexOf(',')+1),android.util.Base64.DEFAULT);captchaImage.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));}else{status.setText("验证码尚未加载，可点击图片区域刷新。");}}}
    void error(String message){if(completing||isDestroyed())return;attempt++;attempting=false;submitted=false;submittedPassword=null;account.setEnabled(true);password.setEnabled(true);remember.setEnabled(true);submit.setEnabled(true);progress.setVisibility(View.GONE);status.setTextColor(0xffa13d37);status.setText(message);}
    void complete(){
        if(completing||isFinishing())return;completing=true;attempt++;attempting=false;submit.setEnabled(false);
        final String user=submittedAccount,secret=submittedPassword;submittedPassword=null;password.setText("");
        final boolean keep=remember.isChecked();status.setText("登录成功，正在保存…");
        vault.execute(()->{
            boolean saved=true;
            try{if(!keep)CredentialStore.clear(this);else if(user!=null&&secret!=null)CredentialStore.save(this,user,secret);}catch(Exception e){saved=false;CredentialStore.clear(this);}
            final boolean stored=saved;
            getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("loginCompleted",true).putBoolean("autoLogin",keep&&stored).putBoolean("autoBlocked",false).putLong("lastAuthAt",System.currentTimeMillis()).apply();
            handler.post(()->{if(isDestroyed())return;if(!stored)Toast.makeText(this,"登录成功，但加密凭证保存失败，下次需手动登录",Toast.LENGTH_LONG).show();CookieManager.getInstance().flush();setResult(RESULT_OK);finish();});
        });
    }
    @Override protected void onDestroy(){attempt++;handler.removeCallbacksAndMessages(null);submittedPassword=null;vault.shutdown();password.setText("");engine.stopLoading();engine.destroy();super.onDestroy();}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    LinearLayout.LayoutParams fieldLayout(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.bottomMargin=dp(16);return p;}
    EditText input(String hint,boolean secret){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(theme.text);e.setHintTextColor(theme.muted);e.setSingleLine(true);e.setTextSize(16);e.setPadding(dp(16),0,dp(16),0);e.setBackground(shape(ThemePalette.mix(theme.primary,0xffffffff,.88)));e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_NORMAL));e.setSaveEnabled(false);e.setAutofillHints(secret?View.AUTOFILL_HINT_PASSWORD:View.AUTOFILL_HINT_USERNAME);return e;}
    TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
    GradientDrawable shape(int color){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(16));return s;}
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    String asset(String name)throws IOException{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
}
