package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;

/** Dedicated visible map browser; it never starts an external browser or login bridge. */
public final class CampusMapActivity extends Activity {
    static final String MAP="https://gis.jhun.edu.cn/m/";
    ThemePalette theme;
    WebView web;
    FrameLayout body;
    LinearLayout errorPanel;
    ProgressBar progress;
    TextView errorText;
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
        TextView title=text("校园地图",21,theme.text);title.setTypeface(Typeface.create("sans-serif-medium",0));titles.addView(title);titles.addView(text("江汉大学",12,theme.muted));header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(action("↻","重新加载地图",()->reload()),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(theme.primary));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        errorPanel=new LinearLayout(this);errorPanel.setOrientation(LinearLayout.VERTICAL);errorPanel.setGravity(Gravity.CENTER);errorPanel.setPadding(dp(28),dp(24),dp(28),dp(24));errorPanel.setBackgroundColor(theme.surface);
        TextView errorTitle=text("地图暂时未能打开",20,theme.text);errorTitle.setTypeface(Typeface.create("sans-serif-medium",0));errorPanel.addView(errorTitle);
        errorText=text("请检查网络后重试",14,theme.muted);errorText.setGravity(Gravity.CENTER);errorText.setPadding(0,dp(12),0,dp(24));errorPanel.addView(errorText);
        errorPanel.addView(action("重新加载","重新加载地图",()->reload()),new LinearLayout.LayoutParams(dp(144),dp(46)));errorPanel.setVisibility(View.GONE);body.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));
        createWebView();setContentView(root);web.loadUrl(MAP);
    }
    void createWebView(){
        web=new WebView(this);web.setBackgroundColor(theme.surface);body.addView(web,0,new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(!failed){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();boolean allowed="https".equalsIgnoreCase(uri.getScheme())&&"gis.jhun.edu.cn".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);
                if(!allowed&&request.isForMainFrame())Toast.makeText(CampusMapActivity.this,"此链接不属于校园地图",Toast.LENGTH_SHORT).show();return !allowed;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){failed=false;errorPanel.setVisibility(View.GONE);progress.setProgress(0);progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(View.INVISIBLE);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())showError("请检查网络连接，然后重新加载。");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())showError("学校地图服务暂时不可用（"+response.getStatusCode()+"），请稍后重试。");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();showError("地图安全连接失败，请检查手机日期或稍后重试。");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){body.removeView(view);view.destroy();web=null;showError("地图页面已中断，请重新加载。");return true;}
        });
    }
    void showError(String message){failed=true;progress.setVisibility(View.INVISIBLE);errorText.setText(message);errorPanel.setVisibility(View.VISIBLE);}
    void reload(){if(web==null)createWebView();failed=false;errorPanel.setVisibility(View.GONE);web.loadUrl(MAP);}
    void back(){if(web!=null&&web.canGoBack()){failed=false;errorPanel.setVisibility(View.GONE);web.goBack();}else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){body.removeView(web);web.stopLoading();web.destroy();web=null;}super.onDestroy();}
    TextView action(String label,String description,Runnable run){
        TextView button=text(label,label.length()>1?14:24,theme.deepAccent);button.setGravity(Gravity.CENTER);button.setContentDescription(description);button.setFocusable(true);
        GradientDrawable fill=new GradientDrawable();fill.setColor(theme.entrySurface);fill.setCornerRadius(dp(15));GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),fill,mask));button.setOnClickListener(v->run.run());return button;
    }
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
