package cn.jhun.sanjiaohu;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Selection remains local until Save; tapping any day selects its week's Monday. */
final class CalibrationSheet {
    final MainActivity a;final UiSheet sheet;final String anchorKey;
    LocalDate selected;YearMonth month;int mode=0,yearStart;
    CalibrationSheet(MainActivity activity){
        a=activity;anchorKey=a.anchorKey();selected=a.anchor();if(selected==null)selected=monday(a.today());month=YearMonth.from(selected);yearStart=month.getYear()-5;
        sheet=new UiSheet(a,"周次校准",a.activeSchedule().term,.84f);
        sheet.actions(a,"保存校准",()->{a.prefs.edit().putString(anchorKey,selected.toString()).apply();a.selectedWeek=a.currentWeek();sheet.dialog.dismiss();a.render();});draw();a.showSheet(sheet);
    }
    static LocalDate monday(LocalDate day){return day.minusDays(day.getDayOfWeek().getValue()-1);}
    void draw(){
        LinearLayout body=sheet.body;body.removeAllViews();
        LinearLayout selection=a.column();selection.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(14));selection.setBackground(a.shape(a.palette.entrySurface,18));selection.addView(a.label("第 1 教学周 · 周一",12,a.palette.deepAccent,false));a.space(selection,6);selection.addView(a.label(selected.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")),22,a.INK,true));body.addView(selection);a.space(body,14);
        LinearLayout bar=a.row();bar.addView(a.button("‹",()->{if(mode==1)yearStart-=12;else if(mode==2)month=month.minusYears(1);else month=month.minusMonths(1);draw();}),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));
        String title=mode==1?yearStart+"—"+(yearStart+11):mode==2?month.getYear()+" 年":month.getYear()+" 年 "+month.getMonthValue()+" 月  ⌄";
        bar.addView(a.button(title,()->{if(mode==0){yearStart=month.getYear()-5;mode=1;}else mode=0;draw();}),new LinearLayout.LayoutParams(0,a.dp(44),1));bar.addView(a.button("›",()->{if(mode==1)yearStart+=12;else if(mode==2)month=month.plusYears(1);else month=month.plusMonths(1);draw();}),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));body.addView(bar);a.space(body,8);
        if(mode!=0){
            for(int r=0;r<4;r++){LinearLayout line=a.row();for(int col=0;col<3;col++){final int value=mode==1?yearStart+r*3+col:r*3+col+1;final boolean years=mode==1;
                TextView item=a.themedButton(value+(years?" 年":" 月"),()->{if(years){month=YearMonth.of(value,month.getMonthValue());mode=2;}else{month=YearMonth.of(month.getYear(),value);mode=0;}draw();},years?value==month.getYear():value==month.getMonthValue());LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,a.dp(44),1);if(col>0)size.leftMargin=a.dp(8);line.addView(item,size);
            }body.addView(line);a.space(body,8);}
        }else{
            LinearLayout weekdays=a.row();for(char day:"一二三四五六日".toCharArray()){TextView label=a.label(String.valueOf(day),11,a.MUTED,false);label.setGravity(Gravity.CENTER);weekdays.addView(label,new LinearLayout.LayoutParams(0,a.dp(28),1));}body.addView(weekdays);
            LocalDate first=monday(month.atDay(1));int weeks=(month.atDay(1).getDayOfWeek().getValue()-1+month.lengthOfMonth()+6)/7;
            for(int r=0;r<weeks;r++){LinearLayout line=a.row();LocalDate week=first.plusWeeks(r);boolean chosen=week.equals(selected);if(chosen)line.setBackground(a.shape(a.palette.entrySurface,12));
                for(int col=0;col<7;col++){LocalDate day=week.plusDays(col);TextView cell=a.button(String.valueOf(day.getDayOfMonth()),()->{selected=monday(day);draw();});cell.setPadding(0,0,0,0);cell.setIncludeFontPadding(false);cell.setTextSize(13);cell.setTextColor(YearMonth.from(day).equals(month)?a.INK:a.MUTED);cell.setSelected(chosen);cell.setContentDescription(day+"，选择所在周，周一为 "+week);
                    if(chosen&&col==0){cell.setBackground(a.shape(a.PRIMARY,12));cell.setTextColor(a.ON_PRIMARY);}else if(day.equals(a.today())){android.graphics.drawable.GradientDrawable border=a.shape(Color.TRANSPARENT,12);border.setStroke(a.dp(1),a.palette.deepAccent);cell.setBackground(border);}line.addView(cell,new LinearLayout.LayoutParams(0,a.dp(39),1));
                }body.addView(line);a.space(body,5);
            }
        }
        a.space(body,10);body.addView(a.label("点选开学第一周，自动取该周周一。点击年月可快速切换。",12,a.MUTED,false));a.space(body,8);body.addView(a.button("定位已选日期",()->{month=YearMonth.from(selected);mode=0;yearStart=month.getYear()-5;draw();}),new LinearLayout.LayoutParams(-1,a.dp(40)));
    }
}
