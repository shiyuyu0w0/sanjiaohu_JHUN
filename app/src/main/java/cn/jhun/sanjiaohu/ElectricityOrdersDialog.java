package cn.jhun.sanjiaohu;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.Locale;

/** Separate read-only browser preserves the room/payment form beneath the order viewer. */
final class ElectricityOrdersDialog {
    final IdentityActivity a;
    final Dialog dialog;
    final WebView web;
    final TextView title,subtitle,message;
    final LinearLayout overlay;
    final ProgressBar progress;
    final Handler handler=new Handler(Looper.getMainLooper());
    String adapter,themeJson,current=ElectricityOrdersPolicy.LIST;
    int epoch;
    boolean disposed,failed;
    final Runnable timeout=()->error("历史订单加载超时，请检查网络后重试。");

    ElectricityOrdersDialog(IdentityActivity activity){
        a=activity;
        dialog=new Dialog(a){@Override public void onBackPressed(){back();}};
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=a.column();root.setBackgroundColor(a.theme.surface);
        LinearLayout header=a.row();header.setPadding(a.dp(18),a.dp(10),a.dp(18),a.dp(10));
        TextView back=a.action("‹",false,this::back);back.setContentDescription("返回");header.addView(back,new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));
        LinearLayout words=a.column();words.setPadding(a.dp(14),0,a.dp(8),0);title=a.text("历史缴费订单",20,a.theme.text,true);words.addView(title);subtitle=a.text("当前缴费账号 · 按月份查看",11,a.theme.muted,false);words.addView(subtitle);header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(RefreshIconButton.create(a,a.theme,"刷新历史订单",this::reload),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));root.addView(header);
        progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setIndeterminateTintList(ColorStateList.valueOf(a.theme.primary));root.addView(progress,new LinearLayout.LayoutParams(-1,a.dp(3)));
        FrameLayout body=new FrameLayout(a);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        web=new WebView(a);web.setBackgroundColor(a.theme.surface);web.setAlpha(0);body.addView(web,new FrameLayout.LayoutParams(-1,-1));
        overlay=a.column();overlay.setGravity(Gravity.CENTER);overlay.setPadding(a.dp(28),a.dp(24),a.dp(28),a.dp(24));overlay.setBackgroundColor(a.theme.surface);
        message=a.text("正在获取历史缴费订单…",15,a.theme.muted,false);message.setGravity(Gravity.CENTER);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);overlay.addView(message);a.gap(overlay,22);
        overlay.addView(a.action("重新加载",true,this::reload),new LinearLayout.LayoutParams(a.dp(164),a.dp(46)));a.gap(overlay,12);
        overlay.addView(a.action("重新登录电费服务",false,()->{dialog.dismiss();a.manualLogin();}),new LinearLayout.LayoutParams(a.dp(164),a.dp(46)));
        body.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        try(InputStream in=a.getAssets().open("electricity-orders.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);adapter=out.toString("UTF-8");
            JSONObject theme=new JSONObject();theme.put("surface",color(a.theme.surface));theme.put("card",color(a.theme.controlSurface));theme.put("text",color(a.theme.text));theme.put("muted",color(a.theme.muted));theme.put("accent",color(a.theme.deepAccent));theme.put("outline",color(a.theme.outline));themeJson=theme.toString();
        }catch(Exception ignored){}
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setUserAgentString(a.web.getSettings().getUserAgentString());
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onJsAlert(WebView v,String url,String text,JsResult result){result.confirm();if(ElectricityOrdersPolicy.page(url)&&!disposed){if(text.contains("暂无订单"))subtitle.setText("当前缴费账号暂无历史订单");else Toast.makeText(a,text.length()>160?text.substring(0,160):text,Toast.LENGTH_LONG).show();}return true;}});
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                String url=request.getUrl().toString();
                if("GET".equals(request.getMethod())&&ElectricityOrdersPolicy.page(url))return false;
                if(request.isForMainFrame()){
                    if(ElectricityOrdersPolicy.home(url)&&request.hasGesture()){dialog.dismiss();return true;}
                    if(IdentityPolicy.allowed(url))error("缴费登录可能已过期，请重新登录电费服务后查看订单。");
                    else Toast.makeText(a,"请返回订单列表查看缴费记录",Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
                if(disposed)return;
                if(!ElectricityOrdersPolicy.page(url)){error("缴费登录已过期，请重新登录后查看订单。");return;}
                current=url;epoch++;failed=false;web.setAlpha(0);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);overlay.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);title.setText(ElectricityOrdersPolicy.detail(url)?"缴费订单详情":"历史缴费订单");subtitle.setText(ElectricityOrdersPolicy.detail(url)?"缴费金额、房间与处理进度":"当前缴费账号 · 按月份查看");handler.removeCallbacks(timeout);handler.postDelayed(timeout,45000);
            }
            @Override public void onPageCommitVisible(WebView v,String url){decorate(url,false);}
            @Override public void onPageFinished(WebView v,String url){decorate(url,true);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()||ElectricityOrdersPolicy.query(r.getUrl().toString()))error("订单获取失败，请检查网络后重新加载。");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()||ElectricityOrdersPolicy.query(r.getUrl().toString()))error(response.getStatusCode()==401||response.getStatusCode()==403?"缴费登录已过期，请重新登录后查看订单。":"订单服务暂不可用（"+response.getStatusCode()+"），请稍后重试。");}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e){h.cancel();error("订单页面安全连接失败，请检查手机日期或稍后重试。");}
        });
        dialog.setContentView(root);dialog.setOnDismissListener(d->dispose());
    }
    void show(){
        dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setStatusBarColor(a.theme.surface);w.setNavigationBarColor(a.theme.surface);w.getDecorView().setSystemUiVisibility(a.theme.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
        reload();
    }
    void reload(){if(disposed)return;failed=false;handler.removeCallbacks(timeout);web.stopLoading();if(adapter==null){error("订单页面组件读取失败，请重新进入电费页面。");return;}web.loadUrl(current);}
    void decorate(String url,boolean finished){
        if(disposed||failed||!ElectricityOrdersPolicy.page(url)||!url.equals(web.getUrl()))return;
        int token=epoch;web.evaluateJavascript(adapter+"("+themeJson+")",result->{if(disposed||failed||token!=epoch)return;if("true".equals(result)){overlay.setVisibility(View.GONE);web.setAlpha(1);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);if(finished){handler.removeCallbacks(timeout);progress.setVisibility(View.INVISIBLE);}}else if(finished)error("暂时无法读取订单页面，请重新登录电费服务后重试。");});
    }
    void error(String text){if(disposed)return;failed=true;epoch++;handler.removeCallbacks(timeout);web.stopLoading();web.setAlpha(0);web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);progress.setVisibility(View.INVISIBLE);message.setText(text);overlay.setVisibility(View.VISIBLE);}
    void back(){if(disposed)return;if(ElectricityOrdersPolicy.detail(current)){WebBackForwardList history=web.copyBackForwardList();WebHistoryItem previous=history.getCurrentIndex()>0?history.getItemAtIndex(history.getCurrentIndex()-1):null;current=ElectricityOrdersPolicy.LIST;if(!failed&&previous!=null&&ElectricityOrdersPolicy.list(previous.getUrl()))web.goBack();else reload();}else dialog.dismiss();}
    void dispose(){if(disposed)return;disposed=true;epoch++;handler.removeCallbacksAndMessages(null);web.stopLoading();((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();}
    static String color(int value){return String.format(Locale.ROOT,"#%06x",value&0xffffff);}
}
