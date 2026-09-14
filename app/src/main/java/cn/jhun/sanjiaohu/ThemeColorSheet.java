package cn.jhun.sanjiaohu;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** Edits a draft theme; cancelling never changes the saved appearance. */
final class ThemeColorSheet {
    final MainActivity a;
    final UiSheet sheet;
    final float[] hsv=new float[3];
    final ColorWheelView wheel;
    final SeekBar brightness;
    final TextView brightnessLabel;
    final TextView[] samples=new TextView[3];
    final Switch follow;
    final EditText hex;
    final TextView error;
    boolean updating;
    int color;
    ThemeColorSheet(MainActivity activity){
        a=activity;color=a.PRIMARY;Color.colorToHSV(color,hsv);
        sheet=new UiSheet(a,"主题调色","点选或拖动色盘，找到喜欢的颜色",.88f);LinearLayout body=sheet.body;
        LinearLayout preview=a.row();String[] labels={"主题色","今日","入口底色"};for(int i=0;i<3;i++){TextView t=a.label(labels[i],13,a.INK,true);t.setGravity(Gravity.CENTER);samples[i]=t;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(54),1);if(i>0)lp.leftMargin=a.dp(8);preview.addView(t,lp);}body.addView(preview);a.space(body,10);
        follow=new Switch(a);follow.setText("跟随背景主色");follow.setTextSize(15);follow.setTextColor(a.INK);follow.setChecked(!a.prefs.getBoolean("manualTheme",false));SwitchTheme.apply(follow,a.palette);body.addView(follow,new LinearLayout.LayoutParams(-1,a.dp(44)));a.space(body,8);
        wheel=new ColorWheelView(a);body.addView(wheel,new LinearLayout.LayoutParams(-1,a.dp(226)));
        wheel.setListener((hue,saturation)->{if(updating)return;hsv[0]=hue;hsv[1]=saturation;setManual();color=Color.HSVToColor(hsv);update();});
        TextView hint=a.label("外圈鲜艳 · 中心柔和",11,a.MUTED,false);hint.setGravity(Gravity.CENTER);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));a.space(body,12);
        brightnessLabel=a.label("明暗",12,a.MUTED,false);body.addView(brightnessLabel);brightness=new SeekBar(a);brightness.setMax(100);brightness.setSplitTrack(false);brightness.setContentDescription("调色盘明暗，零为黑色，一百为最亮");brightness.setProgressTintList(null);brightness.setProgressBackgroundTintList(null);body.addView(brightness,new LinearLayout.LayoutParams(-1,a.dp(40)));
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){if(!user||updating)return;hsv[2]=progress/100f;setManual();color=Color.HSVToColor(hsv);update();}});
        LinearLayout swatches=a.row();int[] presets={0xff2ecbff,0xff658cf0,0xffa68cdb,0xffef91ac,0xffeeae79,0xff6fc5a6};
        for(int preset:presets){android.widget.FrameLayout hit=new android.widget.FrameLayout(a);android.view.View dot=new android.view.View(a);android.graphics.drawable.GradientDrawable circle=a.shape(preset,20);circle.setStroke(a.dp(1),ThemePalette.mix(preset,Color.BLACK,.12));dot.setBackground(circle);hit.addView(dot,new android.widget.FrameLayout.LayoutParams(a.dp(28),a.dp(28),Gravity.CENTER));hit.setContentDescription("选择颜色 "+String.format(Locale.ROOT,"#%06X",preset&0xffffff));hit.setFocusable(true);hit.setOnClickListener(v->{color=preset;Color.colorToHSV(color,hsv);setManual();update();});swatches.addView(hit,new LinearLayout.LayoutParams(0,a.dp(42),1));}body.addView(swatches);a.space(body,4);
        TextView caption=a.label("颜色代码",12,a.MUTED,false);caption.setPadding(0,a.dp(6),0,a.dp(7));body.addView(caption);hex=new EditText(a);hex.setSingleLine(true);hex.setTextSize(16);hex.setTextColor(a.INK);hex.setHintTextColor(a.MUTED);hex.setHint("#2ECBFF");hex.setPadding(a.dp(14),0,a.dp(14),0);hex.setBackground(a.shape(ThemePalette.mix(a.PRIMARY,Color.WHITE,.9),13));hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});body.addView(hex,new LinearLayout.LayoutParams(-1,a.dp(46)));error=a.label("",12,0xffa13d37,false);error.setPadding(0,a.dp(8),0,0);body.addView(error);
        follow.setOnCheckedChangeListener((button,enabled)->{if(updating)return;if(enabled){color=a.backgroundPrimary();Color.colorToHSV(color,hsv);}update();});
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable text){if(updating)return;try{color=parse(text.toString());Color.colorToHSV(color,hsv);setManual();update();}catch(IllegalArgumentException e){error.setText("请输入 6 位颜色代码，例如 #2ECBFF");}}});
        sheet.actions(a,"保存颜色",()->{try{int chosen=parse(hex.getText().toString());a.prefs.edit().putBoolean("manualTheme",!follow.isChecked()).putInt("manualThemeColor",chosen).apply();a.refreshAppearance();sheet.dialog.dismiss();a.render();}catch(IllegalArgumentException e){error.setText("请输入有效的 6 位颜色代码");hex.requestFocus();}});
        update();a.showSheet(sheet);
    }
    static int parse(String input){String s=input.trim();if(s.startsWith("#"))s=s.substring(1);if(!s.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("颜色代码格式不正确");return 0xff000000|Integer.parseInt(s,16);}
    void setManual(){updating=true;follow.setChecked(false);updating=false;}
    void update(){
        updating=true;ThemePalette theme=new ThemePalette(color);int[] surfaces={color,theme.deepAccent,theme.entrySurface};
        for(int i=0;i<3;i++){samples[i].setBackground(a.shape(surfaces[i],14));samples[i].setTextColor(ThemePalette.neutralText(surfaces[i]));}
        wheel.setColor(hsv[0],hsv[1],hsv[2]);brightness.setProgress(Math.round(hsv[2]*100));brightnessLabel.setText("明暗  "+Math.round(hsv[2]*100)+"%");
        int bright=Color.HSVToColor(new float[]{hsv[0],hsv[1],1});android.graphics.drawable.GradientDrawable track=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.BLACK,bright});track.setCornerRadius(a.dp(8));track.setSize(a.dp(1),a.dp(12));brightness.setProgressDrawable(track);
        android.graphics.drawable.GradientDrawable thumb=a.shape(color,20);thumb.setSize(a.dp(22),a.dp(22));thumb.setStroke(a.dp(2),Color.WHITE);brightness.setThumb(thumb);brightness.setThumbTintList(null);
        String code=String.format(Locale.ROOT,"#%06X",color&0xffffff);if(!hex.getText().toString().equals(code)){hex.setText(code);hex.setSelection(code.length());}error.setText("");SwitchTheme.apply(follow,theme);TextView save=(TextView)sheet.footer.getChildAt(1);save.setBackground(a.shape(color,15));save.setTextColor(theme.onPrimary);updating=false;
    }
}
