package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shown after the school rejects a previously authenticated QR session. */
final class QrSessionExpiredWarning {
    private QrSessionExpiredWarning(){}

    static Dialog show(Activity activity,ThemePalette theme,Runnable passwordLogin){
        if(activity.isFinishing()||activity.isDestroyed())return null;
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity,24),dp(activity,24),dp(activity,24),dp(activity,22));
        card.setBackground(shape(theme.sheetSurface,24,activity));

        TextView title=text(activity,"扫码登录会话已过期",22,theme.text);card.addView(title);
        TextView message=text(activity,"学校的扫码登录会话已过期，需要重新登录。建议使用账号密码登录，并开启「保存凭证并自动登录」，方便下次续登。\n\n已保存的课表、成绩和考试安排仍可在本地查看。",15,theme.text);
        message.setLineSpacing(dp(activity,4),1);
        LinearLayout.LayoutParams messageSize=new LinearLayout.LayoutParams(-1,-2);messageSize.topMargin=dp(activity,12);messageSize.bottomMargin=dp(activity,22);card.addView(message,messageSize);

        LinearLayout actions=new LinearLayout(activity);
        TextView acknowledge=text(activity,"我知道了",15,theme.text);acknowledge.setGravity(Gravity.CENTER);acknowledge.setBackground(shape(theme.controlSurface,14,activity));acknowledge.setOnClickListener(v->dialog.dismiss());actions.addView(acknowledge,new LinearLayout.LayoutParams(0,dp(activity,48),1));
        TextView login=text(activity,"密码登录",16,theme.onPrimary);login.setGravity(Gravity.CENTER);login.setBackground(shape(theme.primary,14,activity));login.setOnClickListener(v->{dialog.dismiss();passwordLogin.run();});
        LinearLayout.LayoutParams loginSize=new LinearLayout.LayoutParams(0,dp(activity,48),1);loginSize.leftMargin=dp(activity,12);actions.addView(login,loginSize);card.addView(actions);

        dialog.setContentView(card);dialog.show();Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,48),dp(activity,420)),ViewGroup.LayoutParams.WRAP_CONTENT);}
        return dialog;
    }
    private static TextView text(Activity activity,String value,int size,int color){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);return text;}
    private static GradientDrawable shape(int color,int radius,Activity activity){GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(activity,radius));return background;}
    private static int dp(Activity activity,float size){return (int)(activity.getResources().getDisplayMetrics().density*size+.5f);}
}
