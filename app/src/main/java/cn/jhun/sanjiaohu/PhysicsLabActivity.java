package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;

/** In-app browser for the physics lab report service. The site answers only inside the campus
 *  network, so every failed main-frame load raises the "请连接校园网" notice. */
public final class PhysicsLabActivity extends Activity {
    static final int FILE_PICK=702;
    ThemePalette theme;
    WebView web;
    FrameLayout body;
    LinearLayout errorPanel;
    ProgressBar progress;
    TextView errorText;
    Dialog notice;
    ValueCallback<Uri[]> fileCallback;
    boolean failed;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        AppTheme.applySystemBars(this,theme);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),dp(10),dp(18),dp(10));
        header.addView(action("‹","返回",()->back()),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(dp(14),0,dp(8),0);
        TextView title=text("大物实验报告",21,theme.text);title.setTypeface(Typeface.create("sans-serif-medium",0));titles.addView(title);titles.addView(text("需连接校园网",12,theme.muted));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(action("↻","重新加载实验报告",()->reload()),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(theme.primary));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setGravity(Gravity.CENTER);errorPanel.setPadding(dp(28),dp(24),dp(28),dp(24));errorPanel.setBackgroundColor(theme.surface);
        TextView errorTitle=text("实验报告暂时未能打开",20,theme.text);errorTitle.setTypeface(Typeface.create("sans-serif-medium",0));errorPanel.addView(errorTitle);
        errorText=text("请连接校园网",15,theme.deepAccent);errorText.setGravity(Gravity.CENTER);errorText.setLineSpacing(dp(4),1);errorText.setPadding(0,dp(12),0,dp(24));errorPanel.addView(errorText);
        errorPanel.addView(action("重新加载","重新加载实验报告",()->reload()),new LinearLayout.LayoutParams(dp(144),dp(46)));errorPanel.setVisibility(View.GONE);body.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));
        createWebView();setContentView(root);load();
    }
    void createWebView(){
        web=new WebView(this);web.setBackgroundColor(theme.surface);body.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(!failed){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}}
            /** A web page can only open the phone's picker when the app answers this callback;
             *  the report form's 选择文件 button does nothing without it. */
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}
                Intent pick=params.createIntent();
                pick.addCategory(Intent.CATEGORY_OPENABLE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                // Some forms pass bare extensions (".docx") as accept values; pickers match those
                // against no MIME type and show an empty list. Fall back to any file.
                String fallback=PhysicsLabPolicy.pickerType(params.getAcceptTypes());
                if(fallback!=null){pick.setType(fallback);pick.removeExtra(Intent.EXTRA_MIME_TYPES);}
                fileCallback=callback;
                try{startActivityForResult(pick,FILE_PICK);}
                catch(ActivityNotFoundException e){fileCallback=null;callback.onReceiveValue(null);Toast.makeText(PhysicsLabActivity.this,"手机上没有可用的文件选择器",Toast.LENGTH_LONG).show();return false;}
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                if(PhysicsLabPolicy.allowed(request.getUrl().toString()))return false;
                Toast.makeText(PhysicsLabActivity.this,"仅允许打开校园网内的实验报告页面",Toast.LENGTH_SHORT).show();return true;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){failed=false;dismissNotice();errorPanel.setVisibility(View.GONE);progress.setProgress(0);progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(View.INVISIBLE);CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())fail(describe(error));}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())fail("服务返回 "+response.getStatusCode());}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();fail("安全连接失败");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){body.removeView(view);view.destroy();web=null;fail("页面已中断");return true;}
        });
    }
    String describe(WebResourceError error){
        int code=error.getErrorCode();
        if(code==WebViewClient.ERROR_HOST_LOOKUP)return "无法解析实验报告服务器地址";
        if(code==WebViewClient.ERROR_CONNECT||code==WebViewClient.ERROR_TIMEOUT||code==WebViewClient.ERROR_IO)return "无法连接实验报告服务器";
        return "错误代码 "+code;
    }
    /** Main-frame load failed: show the inline panel and raise the campus-network notice. */
    void fail(String detail){
        failed=true;progress.setVisibility(View.INVISIBLE);
        String hint=detail==null||detail.isEmpty()?"":"（"+detail+"）";
        errorText.setText("请连接校园网"+hint);
        errorPanel.setVisibility(View.VISIBLE);
        campusNotice(detail);
    }
    void campusNotice(String detail){
        if(notice!=null&&notice.isShowing())return;
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout outside=new FrameLayout(this);outside.setOnClickListener(v->dialog.dismiss());
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(22),dp(22),dp(22),dp(16));
        card.setBackground(shape(theme.sheetSurface,26));card.setClipToOutline(true);card.setOnClickListener(v->{});
        card.addView(text("无法打开大物实验报告",15,theme.muted));
        TextView message=text("请连接校园网",23,theme.deepAccent);message.setTypeface(Typeface.create("sans-serif-medium",0));message.setPadding(0,dp(10),0,0);card.addView(message);
        TextView hint=text(detail==null||detail.isEmpty()?"实验报告系统只在校园网内开放。":"实验报告系统只在校园网内开放（"+detail+"）。",13,theme.muted);
        hint.setLineSpacing(dp(4),1);hint.setPadding(0,dp(8),0,dp(18));card.addView(hint);
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.END);
        buttons.addView(themedButton("知道了",()->dialog.dismiss(),false),new LinearLayout.LayoutParams(dp(96),dp(44)));
        LinearLayout.LayoutParams reloadSize=new LinearLayout.LayoutParams(0,dp(44),1);reloadSize.leftMargin=dp(10);
        buttons.addView(themedButton("重新加载",()->{dialog.dismiss();reload();},true),reloadSize);card.addView(buttons);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);position.setMargins(dp(24),dp(24),dp(24),dp(24));outside.addView(card,position);
        dialog.setContentView(outside);
        Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.34f);
        dialog.setOnDismissListener(d->{if(notice==d)notice=null;});
        notice=dialog;dialog.show();
        window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),dp(420)),ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    void dismissNotice(){Dialog dialog=notice;notice=null;if(dialog!=null)dialog.dismiss();}
    void load(){if(web==null)createWebView();failed=false;progress.setProgress(0);progress.setVisibility(View.VISIBLE);web.loadUrl(PhysicsLabPolicy.REPORT);}
    void reload(){dismissNotice();errorPanel.setVisibility(View.GONE);load();}
    void back(){
        if(notice!=null&&notice.isShowing()){dismissNotice();return;}
        if(web!=null&&web.canGoBack()){failed=false;errorPanel.setVisibility(View.GONE);web.goBack();}else finish();
    }
    @Override public void onBackPressed(){back();}
    /** Result of the phone's picker: the page gets the chosen file, or null when cancelled. */
    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request!=FILE_PICK){super.onActivityResult(request,result,data);return;}
        ValueCallback<Uri[]> callback=fileCallback;fileCallback=null;
        if(callback!=null)callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));
    }
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){dismissNotice();if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}if(web!=null){body.removeView(web);web.stopLoading();web.destroy();web=null;}super.onDestroy();}
    TextView action(String label,String description,Runnable run){
        TextView button=text(label,label.length()>1?14:24,theme.deepAccent);button.setGravity(Gravity.CENTER);button.setContentDescription(description);button.setFocusable(true);
        GradientDrawable fill=new GradientDrawable();fill.setColor(theme.entrySurface);fill.setCornerRadius(dp(15));GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),fill,mask));button.setOnClickListener(v->run.run());return button;
    }
    TextView themedButton(String value,Runnable run,boolean filled){
        TextView button=text(value,14,filled?theme.onPrimary:theme.deepAccent);button.setGravity(Gravity.CENTER);button.setContentDescription(value);button.setFocusable(true);button.setIncludeFontPadding(false);
        GradientDrawable fill=shape(filled?theme.primary:theme.entrySurface,15);if(!filled)fill.setStroke(dp(1),theme.outline);GradientDrawable mask=shape(theme.rippleMask,15);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(((filled?theme.onPrimary:theme.primary)&0xffffff)|0x22000000),fill,mask));button.setOnClickListener(v->run.run());return button;
    }
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
