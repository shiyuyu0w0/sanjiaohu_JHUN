package cn.jhun.sanjiaohu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

/** One optical size and stroke family for native application controls. */
final class AppIcons {
    private AppIcons() {}
    static int resource(int id) {
        switch(id) {
            case 1:return R.drawable.ic_ui_refresh;
            case 2:return R.drawable.ic_ui_home;
            case 3:case 14:return R.drawable.ic_ui_calendar;
            case 5:return R.drawable.ic_ui_image;
            case 6:return R.drawable.ic_ui_image_reset;
            case 7:return R.drawable.ic_ui_layers;
            case 8:return R.drawable.ic_ui_add;
            case 9:return R.drawable.ic_ui_palette;
            case 10:return R.drawable.ic_ui_logout;
            case 11:return R.drawable.ic_ui_chart;
            case 12:return R.drawable.ic_ui_semester;
            case 13:return R.drawable.ic_ui_map;
            case 15:return R.drawable.ic_ui_tools;
            case 16:return R.drawable.ic_ui_info;
            case 17:return R.drawable.ic_ui_bolt;
            case 18:return R.drawable.ic_ui_flask;
            case 19:return R.drawable.ic_ui_exam;
            case 21:return R.drawable.ic_ui_history;
            case 22:return R.drawable.ic_ui_close;
            default:return R.drawable.ic_ui_user;
        }
    }
    static Drawable drawable(Context context,int resource,int color) {
        Drawable icon=context.getDrawable(resource).mutate();icon.setTint(color);return icon;
    }
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static void trailing(TextView view,int resource) {
        int inset=dp(view.getContext(),12);view.setPadding(Math.max(inset,view.getPaddingLeft()),view.getPaddingTop(),Math.max(inset,view.getPaddingRight()),view.getPaddingBottom());
        Drawable d=drawable(view.getContext(),resource,view.getCurrentTextColor());
        int size=dp(view.getContext(),16);d.setBounds(0,0,size,size);
        view.setCompoundDrawablesRelative(null,null,d,null);view.setCompoundDrawablePadding(dp(view.getContext(),8));
    }
    static void leading(TextView view,int resource) {
        Drawable d=drawable(view.getContext(),resource,view.getCurrentTextColor());
        int size=dp(view.getContext(),18);d.setBounds(0,0,size,size);
        view.setCompoundDrawablesRelative(d,null,null,null);view.setCompoundDrawablePadding(dp(view.getContext(),6));
    }
    static class Glyph extends View {
        private final Drawable icon;
        private final float size;
        Glyph(Context context,int resource,int color,float size){super(context);this.size=size;icon=drawable(context,resource,color);}
        void setColor(int color){icon.setTint(color);invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int s=Math.min(dp(getContext(),size),Math.min(getWidth(),getHeight()));int x=(getWidth()-s)/2,y=(getHeight()-s)/2;icon.setBounds(x,y,x+s,y+s);icon.draw(canvas);}
    }
    /** Keep existing labels and accessibility semantics; replace only standalone symbols. */
    static final class Label extends TextView {
        private int lastResource;
        private Drawable icon;
        Label(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){
            int res=0;String text=getText().toString();
            switch(text){case "‹":res=R.drawable.ic_ui_left;break;case "›":res=R.drawable.ic_ui_right;break;
                case "⋮":res=R.drawable.ic_ui_more;break;case "＋":case "+":res=R.drawable.ic_ui_add;break;
                case "−":res=R.drawable.ic_ui_minus;break;case "↻":res=R.drawable.ic_ui_rotate;break;
                case "⌄":res=R.drawable.ic_ui_down;break;default:break;}
            if(res==0){super.onDraw(canvas);return;}
            if(icon==null||lastResource!=res){icon=drawable(getContext(),res,getCurrentTextColor());lastResource=res;}
            icon.setTint(getCurrentTextColor());int s=Math.min(dp(getContext(),20),Math.min(getWidth(),getHeight()));
            int x=(getWidth()-s)/2,y=(getHeight()-s)/2;icon.setBounds(x,y,x+s,y+s);icon.draw(canvas);
        }
    }
}
