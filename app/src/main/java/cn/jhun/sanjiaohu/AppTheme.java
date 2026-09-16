package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

/** Android bridge for the system-controlled light/dark palette. */
public final class AppTheme {
    private AppTheme(){}

    public static boolean isDark(Context context){
        return (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }

    public static ThemePalette from(Context context,int primary){return new ThemePalette(primary,isDark(context));}

    public static void applySystemBars(Activity activity,ThemePalette theme){
        activity.getWindow().setStatusBarColor(theme.surface);
        activity.getWindow().setNavigationBarColor(theme.surface);
        View decor=activity.getWindow().getDecorView();
        int flags=decor.getSystemUiVisibility();
        if(theme.dark)flags&=~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }
}
