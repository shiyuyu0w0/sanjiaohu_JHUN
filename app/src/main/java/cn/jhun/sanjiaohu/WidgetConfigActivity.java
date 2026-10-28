package cn.jhun.sanjiaohu;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.UUID;

/** Used both by launchers during placement and by tapping a widget's picture. */
public final class WidgetConfigActivity extends Activity {
    int widgetId;String draft;boolean changed,reset;ThemePalette theme;FrameLayout preview;Bitmap previewPhoto;LinearLayout instances;
    final java.util.List<Bitmap> thumbnails=new java.util.ArrayList<>();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId!=AppWidgetManager.INVALID_APPWIDGET_ID&&!TodayWidgetProvider.owns(this,widgetId)){finish();return;}
        draft=saved==null?UUID.randomUUID().toString():saved.getString("draft");changed=saved!=null&&saved.getBoolean("changed");reset=saved!=null&&saved.getBoolean("reset");
        theme=AppTheme.from(this,getSharedPreferences("settings",MODE_PRIVATE).getInt("themeColor",0xff2ecbff));AppTheme.applySystemBars(this,theme);
        LinearLayout root=column();root.setBackgroundColor(theme.surface);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),dp(8),dp(16),dp(8));
        AppIcons.Glyph back=new AppIcons.Glyph(this,R.drawable.ic_ui_left,theme.text,22);back.setContentDescription("返回");back.setFocusable(true);back.setBackground(ripple(android.graphics.Color.TRANSPARENT,12));back.setOnClickListener(v->{Ui.tap(v);finish();});header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text("桌面小组件",20,theme.text);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(widgetId>0){TextView save=button("保存",this::save);save.setBackground(ripple(android.graphics.Color.TRANSPARENT,12));header.addView(save,new LinearLayout.LayoutParams(dp(56),dp(44)));}root.addView(header);
        ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(16),dp(12),dp(16),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        preview=new FrameLayout(this);body.addView(preview,new LinearLayout.LayoutParams(-1,dp(190)));space(body,16);
        if(widgetId>0){
            LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams actionSize=new LinearLayout.LayoutParams(0,dp(48),1);actionSize.rightMargin=dp(8);actions.addView(button("更换图片",this::choose),actionSize);
            TextView restore=button("恢复默认",()->{changed=true;reset=true;showPreview();});restore.setTextColor(theme.muted);restore.setBackground(ripple(android.graphics.Color.TRANSPARENT,14));actions.addView(restore,new LinearLayout.LayoutParams(0,dp(48),1));body.addView(actions);
        }else{
            body.addView(button("添加到桌面",this::pin),new LinearLayout.LayoutParams(-1,dp(48)));space(body,24);
            instances=column();body.addView(instances);showInstances();
        }
        setContentView(root);showPreview();
    }
    @Override protected void onResume(){super.onResume();if(instances!=null){showInstances();showPreview();}}
    void showInstances(){
        instances.removeAllViews();for(Bitmap bitmap:thumbnails)bitmap.recycle();thumbnails.clear();int number=0;int[] ids=TodayWidgetProvider.ids(this);
        if(ids.length>0){TextView label=text("已添加",12,theme.muted);label.setPadding(dp(8),0,0,dp(8));instances.addView(label);}
        for(int id:ids){
            number++;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));row.setBackground(ripple(android.graphics.Color.TRANSPARENT,14));
            ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(shape(theme.controlSurface,8));photo.setClipToOutline(true);Bitmap bitmap=WidgetPhotoStore.load(WidgetPhotoStore.photo(this,id));if(bitmap!=null){thumbnails.add(bitmap);photo.setImageBitmap(bitmap);}else photo.setImageResource(R.drawable.launcher_art);photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(photo,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView label=text("小组件 "+number,15,theme.text);label.setPadding(dp(14),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));AppIcons.Glyph arrow=new AppIcons.Glyph(this,R.drawable.ic_ui_right,theme.muted,18);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(24)));
            row.setFocusable(true);row.setContentDescription("小组件 "+number+"，编辑图片");row.setOnClickListener(v->{Ui.tap(v);startActivity(new Intent(this,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));});instances.addView(row,new LinearLayout.LayoutParams(-1,dp(60)));
        }
    }
    void showPreview(){
        Bitmap old=previewPhoto;previewPhoto=null;
        if(widgetId>0&&!reset)previewPhoto=WidgetPhotoStore.load(changed?WidgetPhotoStore.draft(this,draft):WidgetPhotoStore.photo(this,widgetId));
        // Pass id 0 so Reset cannot accidentally reload the existing saved photo.
        preview.removeAllViews();View card=TodayWidgetProvider.preview(this,0,previewPhoto).apply(this,preview);preview.addView(card,new FrameLayout.LayoutParams(-1,-1));disableClicks(card);
        if(widgetId>0){View picture=card.findViewById(R.id.widget_picture_area);picture.setFocusable(true);picture.setContentDescription("更换小组件图片");picture.setOnClickListener(v->{Ui.tap(v);choose();});}
        if(old!=null)old.recycle();
    }
    void disableClicks(View v){v.setOnClickListener(null);v.setClickable(false);if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++)disableClicks(((android.view.ViewGroup)v).getChildAt(i));}
    void choose(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),1);}catch(ActivityNotFoundException e){Toast.makeText(this,"这台设备没有可用的图片选择器",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        if(request==1&&data!=null&&data.getData()!=null){Intent crop=new Intent(this,WallpaperCropActivity.class).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("widgetPhotoDraft",draft).putExtra("aspect",1f);startActivityForResult(crop,2);}
        if(request==2){changed=true;reset=false;showPreview();}
    }
    void save(){
        if(!TodayWidgetProvider.owns(this,widgetId)){Toast.makeText(this,"这个小组件已被移除，请重新添加",Toast.LENGTH_LONG).show();finish();return;}
        try{if(changed)WidgetPhotoStore.commit(this,widgetId,draft,reset);TodayWidgetProvider.updateAll(this);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();}
        catch(Exception e){Toast.makeText(this,"图片未保存，请重新选择后重试",Toast.LENGTH_LONG).show();}
    }
    void pin(){
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(!manager.isRequestPinAppWidgetSupported()){Toast.makeText(this,"请长按桌面空白处，在小组件中添加三角狐",Toast.LENGTH_LONG).show();return;}
        PendingIntent callback=PendingIntent.getBroadcast(this,0,new Intent(this,TodayWidgetProvider.class).setAction(TodayWidgetProvider.TICK),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.requestPinAppWidget(TodayWidgetProvider.component(this),null,callback);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("draft",draft);out.putBoolean("changed",changed);out.putBoolean("reset",reset);}
    @Override protected void onDestroy(){if(isFinishing()&&draft!=null)new android.util.AtomicFile(WidgetPhotoStore.draft(this,draft)).delete();if(previewPhoto!=null)previewPhoto.recycle();for(Bitmap bitmap:thumbnails)bitmap.recycle();super.onDestroy();}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView text(String label,int size,int color){TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);return v;}
    TextView button(String label,Runnable action){TextView v=text(label,15,theme.deepAccent);v.setGravity(Gravity.CENTER);v.setBackground(ripple(theme.entrySurface,14));v.setFocusable(true);v.setOnClickListener(w->{Ui.tap(w);action.run();});return v;}
    android.graphics.drawable.RippleDrawable ripple(int color,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((theme.primary&0xffffff)|0x22000000),shape(color,radius),shape(android.graphics.Color.WHITE,radius));}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void space(LinearLayout body,int n){body.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    int dp(float n){return Ui.dp(this,n);}
}
