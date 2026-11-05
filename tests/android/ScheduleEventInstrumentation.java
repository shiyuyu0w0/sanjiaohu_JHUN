package cn.jhun.sanjiaohu;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/** Real empty-cell taps, editing, layout growth, date isolation and disk/lifecycle persistence. */
public final class ScheduleEventInstrumentation extends Instrumentation {
    static final String TERM="日程测试学期";
    final LocalDate monday=LocalDate.of(2026,11,2),date=monday.plusDays(1);
    MainActivity a;int checks,singleHeight,singleTop;String eventId;
    List<ScheduleEvent> original;String originalAnchor;
    byte[] customBefore,schoolBefore;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=-1;
        try{
            a=launch();original=new ScheduleEventStore(getTargetContext()).load();originalAnchor=a.prefs.getString("monday:"+TERM,null);
            customBefore=bytes("custom-courses.json");schoolBefore=bytes("schedule.json");
            onMain(()->fixture(2));settle();
            // Click the actual rendered blank cell, including through the empty timetable label.
            tapCell(date,3);settle();
            onMain(()->{
                check(find(sheet(),"添加日程")!=null,"blank tap opens event form");
                check(find(sheet(),"10:20")!=null&&find(sheet(),"11:05")!=null,"clock endpoints default to tapped cell");
                check(find(sheet(),"时长 45 分钟 · 08:20–21:40 内")!=null,"default real clock interval and supported range");
                check(find(sheet(),"教学周")==null&&find(sheet(),"适用学期")==null,"fixed date has no repeat controls");
                check(find(sheet(),ScheduleEventEditor.dateLabel(date)+" · 仅当天")!=null,"chosen date shown");
                find(sheet(),"保存日程").performClick();check(a.events.size()==original.size(),"empty title is rejected");
                ((EditText)find(sheet(),"日程标题")).setText("小组讨论");((EditText)find(sheet(),"日程备注")).setText("带上笔记\n准备提纲");
            });screenshot("event-editor");
            clickSheet("保存日程");settle();
            onMain(()->{
                ScheduleEvent event=event();eventId=event.id;check(event.date.equals(date)&&event.start==620&&event.end==665,"saved exact date and single interval");
                check(event.notes.equals("带上笔记\n准备提纲"),"multiline notes saved");
                View card=card("小组讨论");check(card!=null,"event visible in grid");singleHeight=card.getHeight();singleTop=card.getTop();
                View cell=find(grid(),cellDescription(date,3));check(cell==null,"occupied cell is no longer an add target");card.performClick();
                check(find(sheet(),"日程详情")!=null&&find(sheet(),"带上笔记\n准备提纲")!=null,"card opens full details");
                find(sheet(),"编辑日程").performClick();check(((EditText)find(sheet(),"日程标题")).getText().toString().equals("小组讨论"),"edit loads title");
                pick(0,635);pick(1,710);check(find(sheet(),"时长 1 小时 15 分钟 · 08:20–21:40 内")!=null,"arbitrary-minute duration updates immediately");
                find(sheet(),"保存日程").performClick();
            });settle();
            onMain(()->{check(event().id.equals(eventId)&&a.events.size()==original.size()+1,"editing replaces same event");check(card("小组讨论").getHeight()>singleHeight*1.4&&card("小组讨论").getTop()>singleTop,"minute interval grows and begins within a cell");});
            screenshot("event-timetable");
            swipeBlank();settle();onMain(()->check(a.selectedWeek==3&&a.activeSheet==null,"swipe over blank cells changes week without opening editor"));
            onMain(()->fixture(2));settle();
            onMain(()->{card("小组讨论").performClick();find(sheet(),"编辑日程").performClick();pick(1,665);find(sheet(),"保存日程").performClick();});settle();
            onMain(()->{check(card("小组讨论").getHeight()<singleHeight,"shortened 30-minute interval is smaller than one cell");card("小组讨论").performClick();find(sheet(),"编辑日程").performClick();pick(1,710);find(sheet(),"保存日程").performClick();});settle();
            onMain(()->{a.selectedWeek=1;a.render();});settle();onMain(()->check(card("小组讨论")==null,"not visible in preceding week"));
            onMain(()->{a.selectedWeek=3;a.render();});settle();onMain(()->check(card("小组讨论")==null,"not visible in following week"));
            onMain(()->{a.schedule=Schedule.local("其他学期");a.selectedWeek=2;a.render();});settle();onMain(()->check(card("小组讨论")==null,"does not leak into another term"));
            onMain(()->fixture(2));settle();
            // Adjusting calibration changes the grid dates, never the stored event date.
            onMain(()->{a.prefs.edit().putString("monday:"+TERM,monday.toString()).commit();a.selectedWeek=1;a.render();});settle();onMain(()->check(card("小组讨论")!=null&&event().date.equals(date),"calibration preserves original date"));
            onMain(()->{a.finish();});settle();a=launch();onMain(()->fixture(2));settle();
            onMain(()->{check(card("小组讨论")!=null&&event().end==710&&event().notes.contains("提纲"),"activity relaunch reloads event from disk");
                card("小组讨论").performClick();find(sheet(),"编辑日程").performClick();((EditText)find(sheet(),"日程标题")).setText("不应保存");find(sheet(),"取消").performClick();check(event().title.equals("小组讨论"),"cancel discards changes");
            });
            // An arbitrary interval can occupy lunch and overlaps are computed in minutes.
            onMain(()->{card("小组讨论").performClick();find(sheet(),"编辑日程").performClick();pick(1,852);find(sheet(),"保存日程").performClick();});settle();
            onMain(()->{check(cards("小组讨论")==1&&event().end==852,"cross-lunch interval remains one continuous event");
                a.schedule.courses.add(CustomCourses.create(null,TERM,"教务课程","老师","A101",2,4,4,"1-20"));a.render();
            });settle();onMain(()->{View event=card("小组讨论"),course=card("教务课程");check(event!=null&&course!=null&&event.getRight()<=course.getLeft(),"overlap uses separate lanes");course.performClick();check(find(sheet(),"课程详情")!=null,"occupied course opens course details");a.activeSheet.dismiss();});
            onMain(()->{card("小组讨论").performClick();find(sheet(),"删除日程").performClick();find(sheet(),"确认删除").performClick();});settle();
            onMain(()->{check(cards("小组讨论")==0&&a.events.size()==original.size(),"delete removes only chosen event");check(find(grid(),cellDescription(date,3))!=null,"deleted interval becomes tappable");});
            // The shared controls must retain the original course editor's behavior.
            onMain(()->{CourseEditor editor=new CourseEditor(a,null);check(editor.start==1&&editor.end==2,"course defaults unchanged");editor.changePeriod(1,1);check(editor.end==3&&editor.periodValue[1].getText().toString().equals("3 节"),"shared course stepper still works");editor.sheet.dialog.dismiss();});
            boundedHours();conflicts();migration();uncalibrated();
            check(Arrays.equals(customBefore,bytes("custom-courses.json"))&&Arrays.equals(schoolBefore,bytes("schedule.json")),"events never rewrite course files");
            result.putString("stream","PASS: "+checks+" event tap, editor, duration, date, persistence and isolation checks\n");
        }catch(Throwable failure){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(failure));code=0;}
        finally{
            try{if(original!=null)new ScheduleEventStore(getTargetContext()).save(original);
                if(a!=null)onMain(()->{if(originalAnchor==null)a.prefs.edit().remove("monday:"+TERM).commit();else a.prefs.edit().putString("monday:"+TERM,originalAnchor).commit();if(a.activeSheet!=null)a.activeSheet.dismiss();a.finish();});
            }catch(Throwable cleanup){result.putString("cleanup",cleanup.toString());code=0;}
        }
        finish(code,result);
    }
    void uncalibrated(){
        LocalDate currentMonday=a.today().minusDays(a.today().getDayOfWeek().getValue()-1),sunday=currentMonday.plusDays(6);
        onMain(()->{a.schedule=Schedule.local(TERM);a.prefs.edit().remove("monday:"+TERM).commit();a.selectedWeek=1;a.render();});settle();
        tapCell(sunday,12);settle();onMain(()->{check(find(sheet(),"20:55")!=null&&find(sheet(),"21:40")!=null,"last cell defaults to its actual time");((EditText)find(sheet(),"日程标题")).setText("周日安排");pick(1,1300);check(find(sheet(),"21:40")!=null,"time can end at the last supported minute");find(sheet(),"保存日程").performClick();});settle();
        onMain(()->{check(card("周日安排")!=null,"uncalibrated date entry visible");ScheduleEvent stored=null;for(ScheduleEvent e:a.events)if(e.title.equals("周日安排"))stored=e;check(stored!=null&&stored.date.equals(sunday),"uncalibrated event stores actual displayed date");
            a.prefs.edit().putString("monday:"+TERM,currentMonday.toString()).commit();a.selectedWeek=2;a.render();});settle();onMain(()->check(card("周日安排")==null,"uncalibrated event does not recur after calibration"));
    }
    void pick(int index,int minute){
        find(sheet(),index==0?"开始时间":"结束时间").performClick();
        TimeWheelSheet picker=a.eventEditor.timePicker;check(picker!=null&&picker.isShowing(),"themed wheel picker opens");
        picker.hours.setValue(minute/60);picker.minutes.setValue(minute%60);
        find(picker.sheet.dialog.getWindow().getDecorView(),"确定").performClick();
    }
    void boundedHours(){
        onMain(()->{
            List<ScheduleEvent> entries=new ArrayList<>(a.events);
            entries.add(ScheduleEvent.create(null,TERM,date,500,545,"晨间安排",""));entries.add(ScheduleEvent.create(null,TERM,date,1255,1300,"晚间安排",""));
            entries.add(ScheduleEvent.create(null,TERM,date,757,792,"午休安排",""));entries.add(ScheduleEvent.create(null,TERM,date,547,548,"课间一分钟",""));a.saveEvents(entries);
        });settle();
        onMain(()->{for(String title:new String[]{"晨间安排","晚间安排","午休安排","课间一分钟"}){View card=card(title);check(card!=null&&card.getHeight()>0&&card.getTop()>=0&&card.getBottom()<=grid().getHeight(),title+" is visible inside timetable");card.performClick();check(find(sheet(),"日程详情")!=null,"outside-class event remains clickable");a.activeSheet.dismiss();}
            card("午休安排").performClick();find(sheet(),"编辑日程").performClick();pick(1,750);find(sheet(),"保存日程").performClick();check(a.activeSheet!=null&&find(sheet(),"结束时间需晚于开始时间")!=null,"invalid time interval cannot save");find(sheet(),"取消").performClick();
        });
    }
    void conflicts()throws Exception{
        LocalDate thursday=monday.plusDays(3);int before=a.events.size();byte[] disk=bytes("schedule-events.json");
        onMain(()->{
            fixture(2);a.schedule.courses.add(CustomCourses.create(null,TERM,"冲突英语","","A101",4,4,4,"2"));
            a.localCourses.add(CustomCourses.create(null,TERM,"本地实验","","B202",4,6,6,"2"));a.render();
            a.editEvent(null,thursday,3);a.eventEditor.title.setText("跨格安排");a.eventEditor.notes.setText("冲突时保留草稿");pick(0,635);pick(1,710);
            find(sheet(),"保存日程").performClick();check(find(sheet(),"与「冲突英语」课程时间冲突，请调整日程时间")!=null,"school conflict is named and save is blocked");
            check(a.events.size()==before&&a.eventEditor.notes.getText().toString().equals("冲突时保留草稿"),"conflict keeps events and editor draft intact");
        });check(Arrays.equals(disk,bytes("schedule-events.json")),"rejected conflict does not write to disk");
        screenshot("event-conflict");
        onMain(()->{pick(1,675);find(sheet(),"保存日程").performClick();check(a.events.size()==before+1,"ending exactly at class start can save");});settle();
        onMain(()->{
            card("跨格安排").performClick();find(sheet(),"编辑日程").performClick();pick(1,710);find(sheet(),"保存日程").performClick();
            check(a.eventEditor.error.getVisibility()==View.VISIBLE,"editing into a class also blocks save");
            ScheduleEvent saved=null;for(ScheduleEvent e:a.events)if(e.title.equals("跨格安排"))saved=e;check(saved!=null&&saved.end==675,"rejected edit preserves saved interval");find(sheet(),"取消").performClick();
            a.editEvent(null,thursday,5);a.eventEditor.title.setText("实验前安排");pick(0,870);pick(1,930);find(sheet(),"保存日程").performClick();
            check(find(sheet(),"与「本地实验」课程时间冲突，请调整日程时间")!=null,"local course conflict also blocks save");
            pick(1,890);find(sheet(),"保存日程").performClick();check(a.events.size()==before+2,"touching local course endpoint is allowed");
            a.editEvent(null,thursday,1);a.eventEditor.title.setText("越界安排");a.eventEditor.start=499;find(sheet(),"保存日程").performClick();check(find(sheet(),"日程时间仅支持 08:20–21:40")!=null,"save independently rejects an early time");
            a.eventEditor.start=500;a.eventEditor.end=1301;find(sheet(),"保存日程").performClick();check(a.events.size()==before+2&&a.eventEditor.error.getVisibility()==View.VISIBLE,"save independently rejects a late time");find(sheet(),"取消").performClick();
            a.prefs.edit().remove("monday:"+TERM).commit();a.editEvent(null,thursday,3);a.eventEditor.title.setText("未校准安排");pick(1,710);find(sheet(),"保存日程").performClick();check(find(sheet(),"与「冲突英语」课程时间冲突，请调整日程时间")!=null,"uncalibrated conflict follows visible courses");find(sheet(),"取消").performClick();
        });
    }
    void migration()throws Exception{
        final File directory=Files.createTempDirectory(getTargetContext().getCacheDir().toPath(),"event-migration-").toFile();
        android.content.Context context=new android.content.ContextWrapper(getTargetContext()){@Override public File getFilesDir(){return directory;}@Override public android.content.Context getApplicationContext(){return this;}};
        File file=new File(directory,"schedule-events.json");File cached=new File(directory,"schedule-"+Term.key("2026-2027学年第一学期")+".json");
        try{
            String json="{\"version\":1,\"events\":[{\"id\":\"old\",\"term\":\"2026-2027学年第一学期\",\"date\":\"2026-11-03\",\"start\":3,\"end\":4,\"title\":\"旧日程\",\"notes\":\"保留备注\"}]}";
            Files.write(file.toPath(),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));ScheduleEventStore store=new ScheduleEventStore(context);List<ScheduleEvent> loaded=store.load();
            check(loaded.get(0).start==620&&loaded.get(0).end==720&&loaded.get(0).notes.equals("保留备注"),"v1 period data migrates to original clock times");store.save(loaded);check(new org.json.JSONObject(new String(Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8)).getInt("version")==2,"save writes minute schema");check(store.load().get(0).start==620,"v2 does not migrate minutes twice");
            Files.write(file.toPath(),json.replace("2026-2027学年第一学期","自定义学期").getBytes(java.nio.charset.StandardCharsets.UTF_8));check(new ScheduleEventStore(context).load().get(0).start==620,"local-only v1 events also migrate");
            org.json.JSONArray rows=new org.json.JSONArray();Schedule bell=Schedule.local("2026-2027学年第一学期");bell.starts[3]="10:25";bell.ends[4]="12:07";
            for(int p=1;p<=12;p++){org.json.JSONArray days=new org.json.JSONArray();for(int d=0;d<7;d++)days.put("");rows.put(new org.json.JSONObject().put("period",p+"("+bell.starts[p]+"-"+bell.ends[p]+")").put("days",days));}
            org.json.JSONObject schedule=new org.json.JSONObject().put("version",1).put("term","2026-2027学年第一学期").put("maxWeek",20).put("source","school-sync").put("rows",rows);
            Files.write(cached.toPath(),schedule.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(file.toPath(),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            loaded=new ScheduleEventStore(context).load();check(loaded.get(0).start==625&&loaded.get(0).end==727,"migration uses the active cached school's bell times");
            String legacy="{\"version\":2,\"events\":[{\"id\":\"wide\",\"term\":\"自定义学期\",\"date\":\"2026-11-03\",\"start\":0,\"end\":1439,\"title\":\"旧全天日程\",\"notes\":\"保留\"}]}";
            Files.write(file.toPath(),legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));loaded=store.load();check(loaded.size()==1&&loaded.get(0).start==0&&loaded.get(0).end==1439,"old out-of-range events remain readable");
        }finally{new android.util.AtomicFile(file).delete();new android.util.AtomicFile(cached).delete();directory.delete();}
    }
    MainActivity launch(){MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();return activity;}
    void fixture(int week){a.schedule=Schedule.local(TERM);a.selectedTerm=TERM;a.localCourses=new ArrayList<>();a.page=1;a.prefs.edit().putString("monday:"+TERM,monday.minusWeeks(1).toString()).commit();a.selectedWeek=week;a.render();}
    ScheduleEvent event(){for(ScheduleEvent e:a.events)if(e.term.equals(TERM)&&e.date.equals(date))return e;throw new AssertionError("event missing");}
    byte[] bytes(String name)throws Exception{File file=new File(getTargetContext().getFilesDir(),name);return file.exists()?Files.readAllBytes(file.toPath()):null;}
    ViewGroup grid(){return (ViewGroup)a.weekHost.getChildAt(0);}
    View sheet(){check(a.activeSheet!=null&&a.activeSheet.isShowing(),"sheet showing");return a.activeSheet.getWindow().getDecorView();}
    void clickSheet(String text){onMain(()->{View target=find(sheet(),text);check(target!=null,"button: "+text);target.performClick();});}
    String cellDescription(LocalDate date,int period){return date+"，第 "+period+" 节，添加日程";}
    void tapCell(LocalDate date,int period){onMain(()->{
        View cell=find(grid(),cellDescription(date,period));check(cell!=null,"empty cell target exists");
        ViewGroup g=grid();float x=(cell.getLeft()+cell.getRight())/2f,y=(cell.getTop()+cell.getBottom())/2f;long now=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
        try{g.dispatchTouchEvent(down);g.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}
    });}
    void swipeBlank(){onMain(()->{
        WeekSwipeLayout host=a.weekHost;long time=SystemClock.uptimeMillis();float y=host.getHeight()*.6f;
        int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};float[] positions={.85f,.7f,.25f,.15f};
        for(int i=0;i<actions.length;i++){MotionEvent e=MotionEvent.obtain(time,time+i*60,actions[i],host.getWidth()*positions[i],y,0);try{host.dispatchTouchEvent(e);}finally{e.recycle();}}
    });}
    View card(String name){ViewGroup grid=grid();for(int i=0;i<grid.getChildCount();i++){View v=grid.getChildAt(i);if(v instanceof CourseCardView&&((TextView)v).getText().toString().startsWith(name+"\n"))return v;}return null;}
    int cards(String name){int count=0;ViewGroup grid=grid();for(int i=0;i<grid.getChildCount();i++){View v=grid.getChildAt(i);if(v instanceof CourseCardView&&((TextView)v).getText().toString().startsWith(name+"\n"))count++;}return count;}
    int countText(View view,String text){int count=view instanceof TextView&&text.contentEquals(((TextView)view).getText())?1:0;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)count+=countText(((ViewGroup)view).getChildAt(i),text);return count;}
    View find(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View child=find(((ViewGroup)view).getChildAt(i),text);if(child!=null)return child;}return null;}
    void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    void onMain(Runnable work){final Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError("UI failure",error[0]);}
    void settle(){waitForIdleSync();SystemClock.sleep(400);waitForIdleSync();}
    void screenshot(String name)throws Exception{settle();Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
}
