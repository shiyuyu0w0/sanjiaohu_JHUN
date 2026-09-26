package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.Locale;

/** Checks inline on the about page; only an available update needs a prompt. */
final class UpdatePrompt {
    static WeakReference<UpdatePrompt> visible=new WeakReference<>(null);
    final Activity host;
    final ThemePalette theme;
    final UpdateStore store;
    final TextView entry;
    final Handler handler=new Handler(Looper.getMainLooper());
    Dialog dialog;
    LinearLayout details,actions;
    TextView status;
    ProgressBar progress;
    int route=UpdatePolicy.GITEE;
    boolean checking,working,polling,permissionPending,restorePrompt,resumeCheck,showResult,destroyed;
    String rendered="",error="";
    final Runnable tick=new Runnable(){public void run(){
        if(destroyed||visible.get()!=UpdatePrompt.this)return;
        paint();
        if(!polling&&store.task()!=null){polling=true;UpdateStore.IO.execute(()->{
            try{UpdateDownload.reconcile(host.getApplicationContext());}
            finally{host.runOnUiThread(()->polling=false);}
        });}
        handler.postDelayed(this,1000);
    }};

    UpdatePrompt(Activity host,ThemePalette theme,TextView entry,Bundle saved){
        this.host=host;this.theme=theme;this.entry=entry;store=new UpdateStore(host);
        if(saved!=null){route=saved.containsKey("updateRoute")?saved.getInt("updateRoute"):saved.getBoolean("updateDirect")?UpdatePolicy.GITHUB:UpdatePolicy.GHPROXY;permissionPending=saved.getBoolean("updatePermission");restorePrompt=saved.getBoolean("updatePrompt");resumeCheck=saved.getBoolean("updateChecking");}
    }
    void resume(){
        visible=new WeakReference<>(this);handler.post(tick);
        if(restorePrompt){restorePrompt=false;if(store.available(host)||store.task()!=null)show();}
        if(permissionPending){permissionPending=false;if(host.getPackageManager().canRequestPackageInstalls())install();else notifyUser("尚未允许安装，可稍后重试");}
        if(showResult){showResult=false;result();}
        if(resumeCheck){resumeCheck=false;check();}
    }
    void pause(){handler.removeCallbacks(tick);if(visible.get()==this)visible.clear();}
    void destroy(){destroyed=true;pause();handler.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();}
    void save(Bundle state){state.putInt("updateRoute",route);state.putBoolean("updatePermission",permissionPending);state.putBoolean("updatePrompt",dialog!=null&&dialog.isShowing());state.putBoolean("updateChecking",checking);}

    void check(){
        if(checking||working)return;
        checking=true;error="";paint();
        if(store.task()!=null)show();
        UpdateStore.check(host,true,message->host.runOnUiThread(()->{
            if(destroyed)return;
            checking=false;error=message==null?"":message;paint();
            if(visible.get()==this)result();else showResult=true;
        }));
    }
    void result(){
        if(!error.isEmpty()){notifyUser(error);return;}
        if(store.available(host)||store.task()!=null){show();return;}
        UpdateManifest m=store.manifest();
        notifyUser(m!=null&&m.enabled&&m.code>UpdateStore.installed(host)&&m.minSdk>Build.VERSION.SDK_INT?"新版本需要更高的 Android 系统版本":"暂无可用更新");
    }
    void show(){
        if(destroyed||visible.get()!=this||host.isFinishing())return;
        if(dialog!=null&&dialog.isShowing()){paint();return;}
        dialog=new Dialog(host);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(host);LinearLayout body=column();body.setPadding(dp(22),dp(22),dp(22),dp(16));body.setBackground(shape(theme.entrySurface,24));scroll.addView(body);
        details=column();body.addView(details);
        status=text("",14,false);status.setPadding(0,dp(12),0,dp(10));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        progress=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(theme.primary));body.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        actions=column();body.addView(actions);dialog.setContentView(scroll);dialog.setOnDismissListener(d->rendered="");dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(host.getResources().getDisplayMetrics().widthPixels-dp(40),dp(420)),-2);}
        rendered="";paint();
    }
    void work(Runnable task){
        if(working)return;working=true;error="";paint();
        UpdateStore.IO.execute(()->{try{task.run();}finally{host.runOnUiThread(()->{working=false;rendered="";if(!destroyed)paint();});}});
    }
    void download(){
        UpdateManifest m=store.manifest();if(m==null)return;
        if(UpdatePolicy.downloadUrl(m,route)==null)route=UpdatePolicy.firstRoute(m);
        int selected=route;
        work(()->{try{UpdateDownload.start(host.getApplicationContext(),m,selected);}catch(Exception e){host.runOnUiThread(()->fail(e,"暂时无法下载，请重试"));}});
    }
    int activeRoute(){return store.prefs.getInt("route",store.prefs.getBoolean("direct",false)?UpdatePolicy.GITHUB:UpdatePolicy.GHPROXY);}
    void install(){
        if(!host.getPackageManager().canRequestPackageInstalls()){
            permissionPending=true;
            try{host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+host.getPackageName())));}
            catch(Exception e){permissionPending=false;notifyUser("请在系统设置中允许三角狐安装未知应用");}
            return;
        }
        work(()->{try{UpdateInstaller.install(host.getApplicationContext());}catch(Exception e){host.runOnUiThread(()->fail(e,"无法开始安装，请重试"));}});
    }
    void continueConfirmation(){
        if(destroyed||visible.get()!=this)return;
        Intent pending=UpdateInstaller.confirmation;if(pending==null){install();return;}
        try{host.startActivity(pending);UpdateInstaller.confirmation=null;}catch(Exception e){notifyUser("无法打开系统安装界面，请重试");}
    }
    void cancel(){work(()->UpdateDownload.cancel(host.getApplicationContext(),"已取消更新"));}
    void fail(Exception e,String fallback){if(!destroyed){error=e.getMessage()==null?fallback:e.getMessage();paint();}}
    void notifyUser(String message){if(!destroyed&&visible.get()==this)Toast.makeText(host,message,Toast.LENGTH_LONG).show();}

    void paint(){
        if(destroyed)return;
        entry.setText(checking?"检查中…":"检查更新");entry.setEnabled(!checking&&!working);entry.setAlpha(checking||working?.6f:1);
        entry.setContentDescription(store.available(host)?"检查更新，有新版本":"检查更新");
        if(dialog==null||!dialog.isShowing())return;
        UpdateManifest m=store.manifest(),task=store.task();String phase=store.prefs.getString("phase","idle");
        boolean active=phase.equals("downloading")||phase.equals("verifying");long bytes=store.prefs.getLong("bytes",0);
        progress.setVisibility(active?View.VISIBLE:View.GONE);progress.setIndeterminate(phase.equals("verifying"));progress.setProgress(task==null||task.size==0?0:(int)Math.min(100,100*bytes/task.size));
        status.setText(!error.isEmpty()?error:checking?"正在检查更新…":store.prefs.getString("message","")+(active&&task!=null?"\n"+UpdatePolicy.routeName(activeRoute())+" · "+String.format(Locale.CHINA,"%.1f / %.1f MB",bytes/1048576.0,task.size/1048576.0):""));
        status.setTextColor(error.isEmpty()?theme.muted:theme.error);
        String signature=(m==null?"":m.canonical)+phase+working+checking+error+route+activeRoute()+store.available(host);
        if(signature.equals(rendered))return;rendered=signature;details.removeAllViews();actions.removeAllViews();
        if(m!=null&&store.available(host)){
            details.addView(text("发现新版本 "+m.name,22,true));
            TextView notes=text(m.notes,15,false);notes.setPadding(0,dp(14),0,dp(10));notes.setLineSpacing(dp(4),1);details.addView(notes);
            details.addView(text(String.format(Locale.CHINA,"安装包 %.2f MB",m.size/1048576.0),13,false));
            if(!active&&!phase.equals("ready")&&!phase.equals("installing")&&!phase.equals("confirm")){
                RadioGroup routes=new RadioGroup(host);RadioButton gitee=new RadioButton(host),fast=new RadioButton(host),official=new RadioButton(host);
                gitee.setId(View.generateViewId());fast.setId(View.generateViewId());official.setId(View.generateViewId());
                gitee.setText("Gitee（优先）");fast.setText("GitHub 加速");official.setText("GitHub 官方");
                for(RadioButton b:new RadioButton[]{gitee,fast,official}){b.setTextColor(theme.text);b.setButtonTintList(ColorStateList.valueOf(theme.deepAccent));routes.addView(b);}
                gitee.setEnabled(!m.gitee.isEmpty());fast.setEnabled(!m.mirror.isEmpty());
                if(UpdatePolicy.downloadUrl(m,route)==null)route=UpdatePolicy.firstRoute(m);
                routes.check(route==UpdatePolicy.GITEE?gitee.getId():route==UpdatePolicy.GHPROXY?fast.getId():official.getId());
                routes.setOnCheckedChangeListener((g,id)->route=id==gitee.getId()?UpdatePolicy.GITEE:id==fast.getId()?UpdatePolicy.GHPROXY:UpdatePolicy.GITHUB);
                details.addView(routes);addButton("下载更新",this::download);
            }
        }else details.addView(text(store.prefs.getBoolean("blocked",false)?"更新信息异常":"暂无可用更新",20,true));
        if(active){
            addButton("取消下载",this::cancel);
            if(m!=null){int next=UpdatePolicy.nextChoice(m,activeRoute());if(next!=activeRoute())addButton("切换到 "+UpdatePolicy.routeName(next),()->{route=next;download();});}
        }
        if(task!=null&&(phase.equals("ready")||phase.equals("installing")||phase.equals("confirm"))){addButton(phase.equals("ready")?"立即安装":"继续安装",phase.equals("confirm")?this::continueConfirmation:this::install);addButton("取消更新",this::cancel);}
        if(m!=null&&phase.equals("error"))addButton("打开发布页",()->{try{host.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(m.releasePage)));}catch(Exception e){notifyUser("没有可打开发布页的浏览器");}});
        addButton("稍后",()->dialog.dismiss());
    }
    LinearLayout column(){LinearLayout view=new LinearLayout(host);view.setOrientation(LinearLayout.VERTICAL);return view;}
    TextView text(String value,int size,boolean bold){TextView view=new TextView(host);view.setText(value);view.setTextSize(size);view.setTextColor(theme.text);if(bold)view.setTypeface(Typeface.create("sans-serif-medium",0));return view;}
    void addButton(String value,Runnable click){
        TextView view=text(value,15,true);view.setTextColor(theme.deepAccent);view.setGravity(Gravity.CENTER);view.setPadding(dp(10),dp(10),dp(10),dp(10));view.setMinHeight(dp(44));view.setFocusable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),shape(theme.controlSurface,14),null));view.setEnabled(!working);view.setAlpha(working?.5f:1);view.setOnClickListener(v->{if(!working)click.run();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);actions.addView(view,lp);
    }
    GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    int dp(float value){return (int)(value*host.getResources().getDisplayMetrics().density+.5f);}
}
