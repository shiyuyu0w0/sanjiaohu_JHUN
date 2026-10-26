package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

/** Android bridge for the system-controlled light/dark palette. */
public final class AppTheme {
    private AppTheme(){}

    public static boolean isDark(Context context){
        return (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }

    public static ThemePalette from(Context context,int primary){return new ThemePalette(primary,isDark(context));}

    /**
     * 状态栏/导航栏的图标明暗。
     *
     * targetSdk 35 起 Android 15 强制边到边，setStatusBarColor / setNavigationBarColor 变成空操作，
     * 栏底色由应用内容自己顶上去（各界面根布局本来就是 theme.surface，并按 insets 加了内边距，
     * 所以视觉结果不变）。真正还需要我们管的只剩图标颜色：浅色底要深图标，深色底要浅图标，
     * 否则深色模式下会出现白底白图标。
     *
     * API 30 起用 WindowInsetsController；26—29 没有这个接口，仍然只能走 setSystemUiVisibility。
     * 旧的 setStatusBarColor 也保留着：在 35 以下它依然有效，在 35 及以上无害。
     */
    public static void applySystemBars(Activity activity,ThemePalette theme){
        activity.getWindow().setStatusBarColor(theme.surface);
        activity.getWindow().setNavigationBarColor(theme.surface);
        // Secondary activities call this before setContentView(). PhoneWindow's
        // getInsetsController() dereferences its decor directly on Android 15;
        // getDecorView() must create that decor before we ask for its controller.
        View decor=activity.getWindow().getDecorView();
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=activity.getWindow().getInsetsController();
            if(controller==null)return;
            int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(theme.dark?0:light,light);
            return;
        }
        int flags=decor.getSystemUiVisibility();
        if(theme.dark)flags&=~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    /**
     * 把系统栏的 insets 转成根布局内边距。九个界面原本各写一遍同样的 lambda，统一到这里。
     * 行为与原先一致（含 consume），只是不再散落在各处。
     */
    @SuppressWarnings("deprecation")
    public static void padForSystemBars(View root){
        root.setOnApplyWindowInsetsListener((v,i)->{
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());
            return i.consumeSystemWindowInsets();
        });
    }
}
