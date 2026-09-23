package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/** The same drawn refresh symbol and 44 dp touch target used by the main pages. */
final class RefreshIconButton {
    private RefreshIconButton(){}

    static View create(Activity activity,ThemePalette theme,String description,Runnable action){
        FrameLayout button=new FrameLayout(activity);
        MoreMenu.Icon glyph=new MoreMenu.Icon(activity,1,theme.deepAccent);
        glyph.setBackground(MoreMenu.shape(activity,theme.entrySurface,11));
        glyph.setDuplicateParentStateEnabled(true);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(glyph,new FrameLayout.LayoutParams(MoreMenu.dp(activity,32),MoreMenu.dp(activity,32),Gravity.CENTER));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),null,MoreMenu.shape(activity,theme.rippleMask,12)));
        button.setOnClickListener(v->action.run());
        return button;
    }
}
