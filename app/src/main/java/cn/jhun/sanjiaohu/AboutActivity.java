package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.InputStream;

/** Offline about page; the sponsor image is bundled byte-for-byte as supplied. */
public final class AboutActivity extends Activity {
    /** What users can see and use in this release; plain wording, no version-by-version history. */
    static final String[][] UPDATES={
        {"v1.1.2","- 应用更新：在关于页检查新版本，支持加速和官方两条下载线路，下载后由系统确认覆盖升级。\n- 课表：同步后保存在本地，支持滑动切周与自定义课程。\n- 成绩：查看学期成绩和汇总。\n- 考试查询：显示考试时间、地点、座位和倒计时。\n- 电费：选择宿舍房间后查看空调与照明电量，支持分别缴费。\n- 校园地图与校历：查看校园地点、当前位置和学期安排。\n- 网上报修：进入学校报修服务。\n- 大物实验报告：在校园网内提交实验报告。\n- 登录：保存账号后方便再次进入学校服务。\n- 外观：支持主题配色、壁纸与深色模式。\n- 本页：查看更新内容和技术支持。"},
    };
    /** Technical-support names shown under the「技术支持」heading. */
    static final String[] SUPPORT={"广","yy792e"};
    ThemePalette theme;
    UpdatePrompt updatePrompt;
    @Override protected void onResume(){super.onResume();if(updatePrompt!=null)updatePrompt.resume();}
    @Override protected void onPause(){if(updatePrompt!=null)updatePrompt.pause();super.onPause();}
    @Override protected void onDestroy(){if(updatePrompt!=null)updatePrompt.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(updatePrompt!=null)updatePrompt.save(state);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));
        AppTheme.applySystemBars(this,theme);
        LinearLayout root=column();root.setBackgroundColor(theme.surface);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView back=text("‹",28,theme.deepAccent,true);back.setGravity(Gravity.CENTER);back.setContentDescription("返回个人");back.setFocusable(true);back.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),shape(theme.entrySurface,15),shape(theme.rippleMask,15)));back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text("关于",30,theme.text,true);title.setPadding(dp(16),0,0,0);header.addView(title);root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);FrameLayout center=new FrameLayout(this);scroll.addView(center);
        LinearLayout content=column();content.setPadding(0,dp(12),0,dp(28));int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(520));center.addView(content,new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sponsor=card();TextView sponsorTitle=text("赞助",22,theme.deepAccent,true);sponsorTitle.setGravity(Gravity.CENTER);sponsor.addView(sponsorTitle,new LinearLayout.LayoutParams(-1,-2));gap(sponsor,18);
        FrameLayout plate=new FrameLayout(this);plate.setPadding(dp(8),dp(8),dp(8),dp(8));plate.setBackground(shape(Color.WHITE,18));
        ImageView code=new ImageView(this);code.setAdjustViewBounds(true);code.setScaleType(ImageView.ScaleType.FIT_CENTER);code.setContentDescription("赞助码");
        try(InputStream in=getAssets().open("sponsor.png")){Bitmap bitmap=BitmapFactory.decodeStream(in);if(bitmap==null)throw new java.io.IOException();code.setImageBitmap(bitmap);}catch(java.io.IOException e){TextView error=text("赞助码暂时无法显示",14,theme.text,false);plate.addView(error);}
        plate.addView(code,new FrameLayout.LayoutParams(-1,-2));sponsor.addView(plate,new LinearLayout.LayoutParams(-1,-2));gap(sponsor,16);
        TextView caption=text("觉得好用就打赏一杯咖啡吧",13,ThemePalette.readable(theme.muted,theme.entrySurface,4.5),false);caption.setGravity(Gravity.CENTER);caption.setLineSpacing(dp(3),1);sponsor.addView(caption,new LinearLayout.LayoutParams(-1,-2));content.addView(sponsor);gap(content,16);
        String current="1.0.1";try{current=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        LinearLayout version=card();version.setOrientation(LinearLayout.HORIZONTAL);version.setGravity(Gravity.CENTER_VERTICAL);version.addView(text("版本号",15,theme.text,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView updateEntry=text("检查更新",12,theme.deepAccent,true);updateEntry.setGravity(Gravity.CENTER);updateEntry.setSingleLine(true);updateEntry.setPadding(dp(10),dp(7),dp(10),dp(7));updateEntry.setMinHeight(dp(36));updateEntry.setFocusable(true);updateEntry.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),shape(theme.controlSurface,12),shape(theme.rippleMask,12)));LinearLayout.LayoutParams updateSize=new LinearLayout.LayoutParams(-2,-2);updateSize.rightMargin=dp(10);version.addView(updateEntry,updateSize);updatePrompt=new UpdatePrompt(this,theme,updateEntry,saved);updateEntry.setOnClickListener(v->updatePrompt.check());
        version.addView(text(current.startsWith("v")?current:"v"+current,18,theme.deepAccent,true));content.addView(version);gap(content,16);
        LinearLayout updates=card();TextView updatesTitle=text("更新内容",22,theme.deepAccent,true);updatesTitle.setGravity(Gravity.CENTER);updates.addView(updatesTitle,new LinearLayout.LayoutParams(-1,-2));
        for(String[] entry:UPDATES){updates.addView(text(entry[0],15,theme.deepAccent,true),new LinearLayout.LayoutParams(-1,-2));TextView body=text(entry[1],13,ThemePalette.readable(theme.text,theme.entrySurface,7),false);body.setLineSpacing(dp(5),1);body.setPadding(0,dp(10),0,0);updates.addView(body,new LinearLayout.LayoutParams(-1,-2));}
        content.addView(updates);gap(content,16);
        LinearLayout support=card();support.setBackground(shape(theme.entrySurface,24));
        TextView supportTitle=text("技术支持",22,theme.deepAccent,true);supportTitle.setGravity(Gravity.CENTER);support.addView(supportTitle,new LinearLayout.LayoutParams(-1,-2));
        TextView supportNote=text("感谢以下同学在开发与测试中的帮助",12,ThemePalette.readable(theme.muted,theme.entrySurface,4.5),false);supportNote.setGravity(Gravity.CENTER);supportNote.setLineSpacing(dp(3),1);gap(support,10);support.addView(supportNote,new LinearLayout.LayoutParams(-1,-2));
        for(String name:SUPPORT){LinearLayout row=column();row.setGravity(Gravity.CENTER);gap(support,10);TextView person=text(name,14,theme.deepAccent,true);person.setGravity(Gravity.CENTER);person.setPadding(dp(18),dp(9),dp(18),dp(9));person.setBackground(shape(theme.controlSurface,14));row.addView(person,new LinearLayout.LayoutParams(-2,-2));support.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        content.addView(support);gap(content,16);
        setContentView(root);
    }
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(shape(theme.entrySurface,24));return v;}
    void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
}
