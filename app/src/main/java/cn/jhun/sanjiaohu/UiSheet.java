package cn.jhun.sanjiaohu;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

/** Shared, keyboard-aware surface for editors and appearance controls. */
final class UiSheet {
    final Dialog dialog;
    final LinearLayout body,footer;
    UiSheet(MainActivity a,String title,String subtitle,float fraction){
        dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout outside=new FrameLayout(a);outside.setOnClickListener(v->dialog.dismiss());
        outside.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout panel=new LinearLayout(a){@Override protected void onMeasure(int w,int h){int available=View.MeasureSpec.getSize(h);super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((int)(available*fraction),View.MeasureSpec.EXACTLY));}};
        panel.setOrientation(LinearLayout.VERTICAL);panel.setBackground(a.shape(ThemePalette.mix(a.PRIMARY,Color.WHITE,.97),28));panel.setClipToOutline(true);panel.setOnClickListener(v->{});
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-1,Gravity.BOTTOM);outside.addView(panel,position);
        LinearLayout heading=a.row();heading.setPadding(a.dp(22),a.dp(18),a.dp(12),a.dp(12));LinearLayout words=a.column();words.addView(a.label(title,22,a.INK,true));TextView caption=a.label(subtitle,12,a.MUTED,false);caption.setPadding(0,a.dp(5),0,0);words.addView(caption);heading.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=a.themedButton("×",()->dialog.dismiss(),false);close.setTextSize(25);close.setContentDescription("关闭"+title);heading.addView(close,new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));panel.addView(heading);
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);body=a.column();body.setPadding(a.dp(22),a.dp(6),a.dp(22),a.dp(16));scroll.addView(body);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=a.row();footer.setPadding(a.dp(22),a.dp(12),a.dp(22),a.dp(16));panel.addView(footer);
        dialog.setContentView(outside);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.24f);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(R.style.SheetAnimation);
    }
    void show(MainActivity a){dialog.show();dialog.getWindow().setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels,a.dp(560)),-1);}
    void actions(MainActivity a,String confirm,Runnable save){TextView cancel=a.themedButton("取消",()->dialog.dismiss(),false);footer.addView(cancel,new LinearLayout.LayoutParams(a.dp(76),a.dp(46)));TextView done=a.themedButton(confirm,save,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(46),1);lp.leftMargin=a.dp(12);footer.addView(done,lp);}
}
