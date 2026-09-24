package cn.jhun.sanjiaohu;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native electricity page sharing the existing identity session and theme. */
final class ElectricityPanel {
    final IdentityActivity a;final ThemePalette t;final ScrollView view;
    final LinearLayout content,cards;final TextView buildingButton,floorButton,roomButton,hint,footnote;
    final SharedPreferences prefs;final ExecutorService worker=Executors.newSingleThreadExecutor();
    SortedMap<String,List<ElectricityApi.Branch>> buildings;
    SortedMap<String,List<ElectricityApi.Floor>> floors;
    SortedMap<String,List<ElectricityApi.Meter>> rooms;
    // The third slot is for a single school meter whose AC/lighting type is
    // genuinely unspecified. Never present it as either named meter.
    final ElectricityApi.Meter[] meters=new ElectricityApi.Meter[3];
    final ElectricityApi.Reading[] readings=new ElectricityApi.Reading[3];
    String building,floor,room,adapter;int epoch;Future<?> pending;boolean busy,checkout,disposed,integerOnly,connected;
    Dialog sheet;

    ElectricityPanel(IdentityActivity a){
        this.a=a;t=a.theme;prefs=a.getSharedPreferences("electricity",0);
        Map<String,?> saved=prefs.getAll();building=ElectricityModel.savedSelection(saved.get("building"));floor=ElectricityModel.savedSelection(saved.get("floor"));room=ElectricityModel.savedSelection(saved.get("room"));
        try(InputStream in=a.getAssets().open("electricity-payment.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);adapter=out.toString("UTF-8");}catch(IOException ignored){}
        view=new ScrollView(a);view.setFillViewport(true);view.setVerticalScrollBarEnabled(false);view.setBackgroundColor(t.surface);
        content=a.column();content.setPadding(a.dp(20),a.dp(20),a.dp(20),a.dp(28));view.addView(content);
        LinearLayout selection=a.column();selection.setPadding(a.dp(18),a.dp(18),a.dp(18),a.dp(18));selection.setBackground(a.shape(t.controlSurface,24));
        selection.addView(a.text("宿舍楼",12,t.muted,false));a.gap(selection,8);
        buildingButton=a.action("选择宿舍楼  ›",false,()->chooseBuilding());selection.addView(buildingButton,new LinearLayout.LayoutParams(-1,a.dp(52)));a.gap(selection,14);
        LinearLayout row=a.row(),left=a.column(),right=a.column();left.addView(a.text("楼层 / 单元",12,t.muted,false));right.addView(a.text("房间",12,t.muted,false));a.gap(left,8);a.gap(right,8);
        floorButton=a.action("选楼层/单元  ›",false,()->chooseFloor());roomButton=a.action("选择房间  ›",false,()->chooseRoom());left.addView(floorButton,new LinearLayout.LayoutParams(-1,a.dp(52)));right.addView(roomButton,new LinearLayout.LayoutParams(-1,a.dp(52)));
        row.addView(left,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=a.dp(12);row.addView(right,rp);selection.addView(row);content.addView(selection);
        a.gap(content,14);hint=a.text("正在连接电费服务…",12,t.muted,false);hint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(hint);a.gap(content,18);
        cards=a.column();content.addView(cards);a.gap(content,20);footnote=a.text("空调与照明分别计量、分别缴费。\n从支付宝返回后可刷新电量，到账以学校系统为准。",12,t.muted,false);content.addView(footnote);a.gap(content,14);
        content.addView(a.action("重新登录电费服务",false,()->a.manualLogin()),new LinearLayout.LayoutParams(-1,a.dp(46)));
        view.setVisibility(View.GONE);a.body.addView(view,new android.widget.FrameLayout.LayoutParams(-1,-1));renderCards();buttons();
    }
    static boolean isPage(String url){return IdentityPolicy.cloudPayment(url)&&"/CloudPayment/bill/selectPayProject.do".equals(IdentityPolicy.navigationUri(url).getPath());}
    boolean visible(){return view.getVisibility()==View.VISIBLE;}
    void show(){a.setWebVisible(false);a.form.setVisibility(View.GONE);a.errorPanel.setVisibility(View.GONE);view.setVisibility(View.VISIBLE);a.subtitle.setText(connected?"宿舍电费 · 剩余电量":"正在连接电费服务…");}
    void connecting(){show();hint.setTextColor(t.muted);hint.setText("正在连接电费服务…");buttons();}
    void hide(){view.setVisibility(View.GONE);if(sheet!=null)sheet.dismiss();}
    void invalidate(){epoch++;busy=false;connected=false;if(pending!=null)pending.cancel(true);buildings=null;floors=null;rooms=null;clearReadings();buttons();hide();}
    void ready(String url){
        if(checkout||disposed||!IdentityPolicy.cloudPayment(url))return;
        if("/CloudPayment/bill/type.do".equals(IdentityPolicy.navigationUri(url).getPath())){a.web.loadUrl(ElectricityApi.PAGE);return;}
        if(!isPage(url))return;
        connected=true;show();if(buildings==null&&!busy)catalog();
    }
    interface Job<T>{T run(ElectricityApi api)throws Exception;}
    interface Done<T>{void accept(T value);}
    <T> void request(String message,Job<T> job,Done<T> done){
        int token=++epoch;if(pending!=null)pending.cancel(true);busy=true;hint.setTextColor(t.muted);hint.setText(message);buttons();
        ElectricityApi api=new ElectricityApi(CookieManager.getInstance().getCookie(ElectricityApi.ORIGIN+"/CloudPayment/"),a.web.getSettings().getUserAgentString());
        pending=worker.submit(()->{
            try{T result=job.run(api);a.handler.post(()->{if(disposed||token!=epoch)return;busy=false;done.accept(result);buttons();});}
            catch(Exception e){a.handler.post(()->{if(disposed||token!=epoch)return;busy=false;hint.setTextColor(t.error);hint.setText(e instanceof ElectricityApi.SessionExpired?"登录已过期，请点击下方重新登录":e instanceof IOException?e.getMessage():"暂时无法读取电费信息，请刷新重试");buttons();renderCards();});}
        });
    }
    void catalog(){clearReadings();request("正在获取宿舍楼…",ElectricityApi::buildings,data->{buildings=data;if(!buildings.containsKey(building)){building="";floor="";room="";}buttons();if(!building.isEmpty())loadFloors();else hint.setText("请选择宿舍楼、楼层或单元、房间");});}
    void loadFloors(){List<ElectricityApi.Branch> selected=new ArrayList<>(buildings.get(building));floors=null;rooms=null;clearReadings();request("正在获取楼层 / 单元…",api->api.floors(selected),data->{floors=data;if(floor.isEmpty()||!floors.containsKey(floor)){floor="";room="";}if(!floor.isEmpty())loadRooms();else hint.setText("请选择楼层或单元");});}
    void loadRooms(){List<ElectricityApi.Floor> selected=new ArrayList<>(floors.get(floor));rooms=null;clearReadings();request("正在获取房间…",api->api.rooms(selected),data->{rooms=data;if(!rooms.containsKey(room))room="";if(!room.isEmpty())query();else hint.setText("请选择房间");});}
    void clearReadings(){Arrays.fill(meters,null);Arrays.fill(readings,null);renderCards();}
    void query(){
        clearReadings();if(rooms==null||!rooms.containsKey(room))return;
        List<ElectricityApi.Meter> candidates=rooms.get(room);
        for(int i=0;i<2;i++){int kind=i==0?ElectricityModel.AC:ElectricityModel.LIGHT;ElectricityApi.Meter chosen=null;int count=0;
            Set<String> ids=new HashSet<>();for(ElectricityApi.Meter m:candidates)if(m.floor.kind==kind&&ids.add(m.room.id)){chosen=m;count++;}if(count==1)meters[i]=chosen;
        }
        if(candidates.size()==1&&candidates.get(0).floor.kind==ElectricityModel.UNKNOWN)meters[2]=candidates.get(0);
        save();ElectricityApi.Meter[] selected=meters.clone();renderCards();
        request("正在查询 "+building+" "+room+"…",api->{ElectricityApi.Reading[] r=new ElectricityApi.Reading[3];for(int i=0;i<r.length;i++)if(selected[i]!=null)r[i]=api.reading(selected[i]);return r;},data->{
            System.arraycopy(data,0,readings,0,readings.length);boolean expired=false,failed=false;for(ElectricityApi.Reading r:data)if(r!=null){expired|=r.expired;failed|=!r.ok;}
            hint.setText(expired?"登录已过期，请重新登录":failed?"部分电表查询失败，可点击右上角刷新":building+" · "+ElectricityModel.levelLabel(floor)+" · "+room+" 室");hint.setTextColor(failed?t.error:t.muted);renderCards();
        });
    }
    void refresh(){if(!connected||busy)return;if(buildings==null)catalog();else if(!building.isEmpty()&&floors==null)loadFloors();else if(!floor.isEmpty()&&rooms==null)loadRooms();else if(!room.isEmpty())query();else hint.setText("请选择宿舍楼、楼层或单元、房间");}
    void buttons(){
        buildingButton.setText(building.isEmpty()?"选择宿舍楼  ›":building+"  ›");floorButton.setText(floor.isEmpty()?"选楼层/单元  ›":ElectricityModel.levelLabel(floor)+"  ›");roomButton.setText(room.isEmpty()?"选择房间  ›":room+" 室  ›");
        enable(buildingButton,connected&&!busy&&buildings!=null);enable(floorButton,connected&&!busy&&floors!=null);enable(roomButton,connected&&!busy&&rooms!=null);
    }
    void enable(View v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1f:.5f);}
    void save(){prefs.edit().putString("building",building).putString("floor",floor).putString("room",room).apply();}
    void chooseBuilding(){if(buildings!=null)choices("选择宿舍楼",new ArrayList<>(buildings.keySet()),value->{building=value;floor="";room="";save();loadFloors();});}
    void chooseFloor(){if(floors!=null)choices("选择楼层 / 单元",new ArrayList<>(floors.keySet()),value->{floor=value;room="";save();loadRooms();});}
    void chooseRoom(){if(rooms!=null)choices("选择房间",new ArrayList<>(rooms.keySet()),value->{room=value;query();});}
    void choices(String title,List<String> values,Done<String> selected){
        LinearLayout box=sheet(title);ScrollView scroll=new ScrollView(a);LinearLayout list=a.column();scroll.addView(list);
        for(String value:values){TextView option=a.action(value,false,()->{sheet.dismiss();selected.accept(value);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,a.dp(48));p.bottomMargin=a.dp(8);list.addView(option,p);}
        box.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(a.dp(400),a.getResources().getDisplayMetrics().heightPixels/2)));openSheet(box);
    }
    LinearLayout sheet(String title){
        if(sheet!=null)sheet.dismiss();sheet=new Dialog(a);sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout box=a.column();box.setPadding(a.dp(22),a.dp(22),a.dp(22),a.dp(24));box.setBackground(a.shape(t.sheetSurface,26));
        LinearLayout head=a.row();head.addView(a.text(title,21,t.text,true),new LinearLayout.LayoutParams(0,-2,1));head.addView(a.action("×",false,()->sheet.dismiss()),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));box.addView(head);a.gap(box,18);return box;
    }
    void openSheet(LinearLayout box){sheet.setContentView(box);Window w=sheet.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setGravity(Gravity.BOTTOM);sheet.show();w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(20),a.dp(520)),-2);}
    void renderCards(){
        cards.removeAllViews();boolean untyped=meters[2]!=null;
        footnote.setText(untyped?"学校目录没有标明此电表属于空调还是照明；缴费前请核对房间和电表编号。\n从支付宝返回后可刷新电量，到账以学校系统为准。":"空调与照明分别计量、分别缴费。\n从支付宝返回后可刷新电量，到账以学校系统为准。");
        for(int i=0;i<meters.length;i++){
            if(untyped?i!=2:i==2)continue;
            final int index=i;ElectricityApi.Meter meter=meters[i];ElectricityApi.Reading reading=readings[i];
            LinearLayout card=a.column();card.setPadding(a.dp(20),a.dp(20),a.dp(20),a.dp(20));card.setBackground(a.shape(t.controlSurface,24));LinearLayout head=a.row();
            View stripe=new View(a);stripe.setBackground(a.shape(i==0?0xff2f6fed:i==1?0xfff08a24:0xff6b7280,3));head.addView(stripe,new LinearLayout.LayoutParams(a.dp(4),a.dp(22)));
            String type=i==0?"空调":i==1?"照明":"宿舍电表";
            TextView title=a.text(i==2?"宿舍电费":type+"电费",18,t.text,true);title.setPadding(a.dp(10),0,a.dp(8),0);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView pay=a.action("缴费",true,()->pay(index));pay.setContentDescription(type+"缴费");enable(pay,reading!=null&&reading.ok&&reading.canBuy&&meter!=null&&!checkout);head.addView(pay,new LinearLayout.LayoutParams(a.dp(70),a.dp(44)));card.addView(head);a.gap(card,16);
            boolean ok=reading!=null&&reading.ok;TextView value=a.text(ok?String.format(Locale.CHINA,"%.2f",reading.quantity):"--",38,ok&&reading.quantity<20?t.error:t.text,true);
            LinearLayout amount=a.row();amount.addView(value);TextView unit=a.text("  度",14,t.muted,false);amount.addView(unit);card.addView(amount);a.gap(card,8);
            String message=ok?(reading.quantity<20?"电量偏低":"电量正常")+" · "+new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(reading.at))+" 更新":reading!=null?reading.message:room.isEmpty()?"选择房间后自动查询":meter==null?"学校未提供可明确对应的电表":"正在查询…";
            card.addView(a.text(message,12,ok&&reading.quantity<20?t.error:t.muted,false));
            if(i==2){a.gap(card,6);card.addView(a.text("学校未标明空调或照明类型",12,t.muted,false));}
            if(meter!=null){a.gap(card,8);card.addView(a.text("电表 "+meter.room.id,11,t.muted,false));}
            if(ok&&!reading.canBuy){a.gap(card,6);card.addView(a.text("学校暂未开放此电表缴费",12,t.muted,false));}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=a.dp(16);cards.addView(card,p);
        }
    }
    void pay(int index){
        if(checkout||busy||adapter==null||meters[index]==null||readings[index]==null||!readings[index].ok||!readings[index].canBuy)return;
        if(!isPage(a.web.getUrl())||!a.documentReady){hint.setText("正在重新连接缴费页面，请稍后再试");a.startPage(ElectricityApi.PAGE);return;}
        final ElectricityApi.Meter selected=meters[index];final int token=epoch;
        a.web.evaluateJavascript(adapter+"(\"inspect\",null)",raw->{
            if(disposed||token!=epoch||!isPage(a.web.getUrl()))return;
            try{JSONObject state=new JSONObject(raw);if(!state.optBoolean("ready")){hint.setText("缴费页面尚未就绪，请刷新或重新登录");return;}integerOnly=state.optBoolean("integerOnly");}
            catch(Exception e){hint.setText("无法准备缴费，请重新登录后重试");return;}
            LinearLayout box=sheet(index==0?"空调缴费":index==1?"照明缴费":"宿舍电表缴费");box.addView(a.text(building+" · "+room+" 室",16,t.text,true));a.gap(box,6);box.addView(a.text("电表 "+selected.room.id,12,t.muted,false));a.gap(box,20);
            EditText amount=a.input("输入缴费金额（元）",false);amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setAutofillHints((String[])null);box.addView(amount,new LinearLayout.LayoutParams(-1,a.dp(58)));a.gap(box,10);
            TextView error=a.text(integerOnly?"学校要求 10～200 元的整数":"金额单位：元，最多两位小数",12,t.muted,false);box.addView(error);a.gap(box,20);
            TextView button=a.action("支付 · 前往支付宝",true,()->{
                if(token!=epoch||checkout||!isPage(a.web.getUrl())){error.setText("房间或登录状态已变化，请返回重试");return;}
                String money;try{money=ElectricityModel.amount(amount.getText().toString(),integerOnly);}catch(IllegalArgumentException e){error.setTextColor(t.error);error.setText(e.getMessage());return;}
                checkout=true;sheet.dismiss();a.showWeb();a.subtitle.setText("正在前往支付宝，请核对房间和金额");
                try{JSONObject payload=new JSONObject();payload.put("room",selected.room.id);payload.put("description",selected.description());payload.put("amount",money);
                    a.web.evaluateJavascript(adapter+"(\"pay\","+payload+")",result->{if(disposed)return;try{JSONObject reply=new JSONObject(result);if(!reply.optBoolean("submitted")){checkout=false;show();hint.setText(reply.optString("message","未能发起支付，请重试"));}}catch(Exception ignored){/* Navigation may have already left the payment form. Never resubmit. */}});
                }catch(Exception e){checkout=false;show();hint.setText("未能发起支付，请重试");}
            });box.addView(button,new LinearLayout.LayoutParams(-1,a.dp(50)));a.gap(box,12);box.addView(a.text("请在支付宝核对金额并完成付款。",12,t.muted,false));openSheet(box);
        });
    }
    boolean back(){if(visible())return false;if(checkout){checkout=false;a.setWebVisible(false);invalidate();connecting();a.startPage(ElectricityApi.PAGE);return true;}return false;}
    void destroy(){disposed=true;invalidate();worker.shutdownNow();}
}
