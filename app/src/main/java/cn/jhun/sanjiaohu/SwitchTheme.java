package cn.jhun.sanjiaohu;

import android.content.res.ColorStateList;
import android.widget.Switch;

/** Both native login switches use the current image theme, including disabled states. */
final class SwitchTheme {
    static void apply(Switch view,ThemePalette theme){
        int[][] states={{-android.R.attr.state_enabled},{android.R.attr.state_checked},{}};
        view.setThumbTintList(new ColorStateList(states,new int[]{ThemePalette.mix(theme.primary,theme.surface,.75),theme.deepAccent,0xfffafafa}));
        view.setTrackTintList(new ColorStateList(states,new int[]{ThemePalette.mix(theme.primary,theme.surface,.9),ThemePalette.mix(theme.primary,0xffffffff,.5),0xffb7bdc3}));
    }
}
