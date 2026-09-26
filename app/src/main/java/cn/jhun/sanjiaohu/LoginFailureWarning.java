package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Password lockout reminder shared by manual and automatic school login. */
final class LoginFailureWarning {
    private LoginFailureWarning(){}

    static Dialog show(Activity activity,ThemePalette theme){
        if(activity.isFinishing()||activity.isDestroyed())return null;
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card=new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity,24),dp(activity,24),dp(activity,24),dp(activity,22));
        card.setBackground(shape(theme.sheetSurface,24,activity));

        TextView title=new TextView(activity);
        title.setText("小心");
        title.setTextSize(22);
        title.setTextColor(theme.error);
        card.addView(title);

        TextView message=new TextView(activity);
        message.setText("密码错误五次，教务系统与喜鹊儿账号将会被锁定 24 小时。如果忘记了密码，请及时到教务系统更改密码，不要过多尝试。");
        message.setTextSize(15);
        message.setTextColor(theme.text);
        message.setLineSpacing(dp(activity,4),1);
        LinearLayout.LayoutParams messageSize=new LinearLayout.LayoutParams(-1,-2);
        messageSize.topMargin=dp(activity,12);
        messageSize.bottomMargin=dp(activity,22);
        card.addView(message,messageSize);

        TextView acknowledge=new TextView(activity);
        acknowledge.setText("我知道了");
        acknowledge.setTextSize(16);
        acknowledge.setTextColor(theme.onPrimary);
        acknowledge.setGravity(Gravity.CENTER);
        acknowledge.setBackground(shape(theme.primary,14,activity));
        acknowledge.setOnClickListener(v->dialog.dismiss());
        card.addView(acknowledge,new LinearLayout.LayoutParams(-1,dp(activity,48)));

        dialog.setContentView(card);
        dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,48),dp(activity,420)),ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static GradientDrawable shape(int color,int radius,Activity activity){
        GradientDrawable background=new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(activity,radius));
        return background;
    }

    private static int dp(Activity activity,float size){return (int)(activity.getResources().getDisplayMetrics().density*size+.5f);}
}
