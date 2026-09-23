package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.provider.Settings;
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
    static final int LOCATION_REQUEST=61;
    GeolocationPermissions.Callback locationCallback;
    String locationOrigin;
    boolean permissionPending;
    AlertDialog locationHelp;

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
        header.addView(RefreshIconButton.create(this,theme,"重新加载地图",()->reload()),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);
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
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setGeolocationEnabled(true);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){if(!failed){progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){requestLocation(origin,callback);}
            @Override public void onGeolocationPermissionsHidePrompt(){finishLocation(false);}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();boolean allowed="https".equalsIgnoreCase(uri.getScheme())&&"gis.jhun.edu.cn".equalsIgnoreCase(uri.getHost())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443);
                if(!allowed&&request.isForMainFrame())Toast.makeText(CampusMapActivity.this,"此链接不属于校园地图",Toast.LENGTH_SHORT).show();return !allowed;
            }
            @Override public void onPageStarted(WebView view,String url,Bitmap icon){finishLocation(false);failed=false;errorPanel.setVisibility(View.GONE);progress.setProgress(0);progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(View.INVISIBLE);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())showError("请检查网络连接，然后重新加载。");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())showError("学校地图服务暂时不可用（"+response.getStatusCode()+"），请稍后重试。");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();showError("地图安全连接失败，请检查手机日期或稍后重试。");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){finishLocation(false);body.removeView(view);view.destroy();web=null;showError("地图页面已中断，请重新加载。");return true;}
        });
    }
    boolean hasLocation(String permission){return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    boolean locationEnabled(){
        LocationManager manager=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(manager==null)return false;
        try{return android.os.Build.VERSION.SDK_INT>=28?manager.isLocationEnabled():manager.isProviderEnabled(LocationManager.GPS_PROVIDER)||manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception e){return false;}
    }
    void requestLocation(String origin,GeolocationPermissions.Callback callback){
        if(isFinishing()||isDestroyed()||web==null||!MapLocationPolicy.trusted(origin)||!MapLocationPolicy.trusted(web.getUrl())||permissionPending){callback.invoke(origin,false,false);return;}
        finishLocation(false);locationOrigin=origin;locationCallback=callback;
        if(!locationEnabled()){finishLocation(false);showLocationHelp(false);return;}
        if(hasLocation(Manifest.permission.ACCESS_FINE_LOCATION)||hasLocation(Manifest.permission.ACCESS_COARSE_LOCATION)){finishLocation(true);return;}
        permissionPending=true;
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);
    }
    void finishLocation(boolean granted){
        GeolocationPermissions.Callback callback=locationCallback;String origin=locationOrigin;locationCallback=null;locationOrigin=null;
        if(callback!=null){boolean allowed=granted&&web!=null&&MapLocationPolicy.allow(origin,web.getUrl(),hasLocation(Manifest.permission.ACCESS_FINE_LOCATION),hasLocation(Manifest.permission.ACCESS_COARSE_LOCATION));callback.invoke(origin,allowed,false);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);if(request!=LOCATION_REQUEST)return;permissionPending=false;
        if(locationCallback==null||isFinishing()||isDestroyed())return;
        boolean allowed=hasLocation(Manifest.permission.ACCESS_FINE_LOCATION)||hasLocation(Manifest.permission.ACCESS_COARSE_LOCATION);
        finishLocation(allowed&&locationEnabled());
        if(allowed&&!locationEnabled())showLocationHelp(false);
        else if(!allowed){
            if(!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))showLocationHelp(true);
            else Toast.makeText(this,"未允许定位，仍可浏览地图；需要定位时可再次点击地图定位按钮。",Toast.LENGTH_LONG).show();
        }
    }
    void showLocationHelp(boolean permission){
        if(isFinishing()||isDestroyed()||(locationHelp!=null&&locationHelp.isShowing()))return;
        locationHelp=new AlertDialog.Builder(this).setTitle(permission?"尚未允许定位":"手机定位服务未开启")
            .setMessage(permission?"请在应用权限中允许三角狐访问位置，然后返回地图点击定位按钮。":"请开启手机的位置服务，然后返回地图点击定位按钮。")
            .setNegativeButton("暂不设置",null).setPositiveButton("去设置",(d,w)->{
                Intent intent=permission?new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())):new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                try{startActivity(intent);}catch(android.content.ActivityNotFoundException e){Toast.makeText(this,"请在手机设置中开启定位权限与位置服务",Toast.LENGTH_LONG).show();}
            }).show();
    }
    void showError(String message){failed=true;progress.setVisibility(View.INVISIBLE);errorText.setText(message);errorPanel.setVisibility(View.VISIBLE);}
    void reload(){if(web==null)createWebView();failed=false;errorPanel.setVisibility(View.GONE);web.loadUrl(MAP);}
    void back(){if(web!=null&&web.canGoBack()){failed=false;errorPanel.setVisibility(View.GONE);web.goBack();}else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){finishLocation(false);if(locationHelp!=null)locationHelp.dismiss();if(web!=null){body.removeView(web);web.stopLoading();web.destroy();web=null;}super.onDestroy();}
    TextView action(String label,String description,Runnable run){
        TextView button=text(label,label.length()>1?14:24,theme.deepAccent);button.setGravity(Gravity.CENTER);button.setContentDescription(description);button.setFocusable(true);
        GradientDrawable fill=new GradientDrawable();fill.setColor(theme.entrySurface);fill.setCornerRadius(dp(15));GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x33000000),fill,mask));button.setOnClickListener(v->run.run());return button;
    }
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
