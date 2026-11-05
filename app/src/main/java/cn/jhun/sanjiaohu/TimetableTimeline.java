package cn.jhun.sanjiaohu;

import java.util.*;

/** Clock times map into the existing twelve cells; events never resize the timetable. */
final class TimetableTimeline {
    static final class Band {
        final int start,end;final String label;
        Band(int start,int end,String label){this.start=start;this.end=end;this.label=label;}
    }
    final List<Band> bands=new ArrayList<>();
    final int[] starts=new int[13],ends=new int[13];
    TimetableTimeline(Schedule schedule){
        for(int p=1;p<=12;p++){starts[p]=ScheduleEvent.minute(schedule.starts[p]);ends[p]=ScheduleEvent.minute(schedule.ends[p]);}
        bands.add(new Band(ends[4],starts[5],"午休"));bands.add(new Band(ends[8],starts[9],"晚餐"));
    }
    int y(int minute,GridGeometry g){
        if(minute<=starts[1])return g.header;
        for(int p=1;p<=12;p++){
            if(minute<starts[p])return interpolate(minute,ends[p-1],starts[p],g.periodBottom(p-1),g.periodTop(p));
            if(minute<=ends[p])return interpolate(minute,starts[p],ends[p],g.periodTop(p),g.periodBottom(p));
        }
        return g.height;
    }
    int interpolate(int minute,int start,int end,int top,int bottom){return top+(int)Math.round((bottom-top)*(minute-start)/(double)Math.max(1,end-start));}
    int[] bounds(GridGeometry g,int kind,int day,int start,int end,int lane,int lanes){
        if(kind==WeekGridView.HEADER||kind==WeekGridView.EMPTY||kind==WeekGridView.TIME||kind==WeekGridView.CELL)return g.bounds(kind,day,start,end,lane,lanes);
        int from=kind==WeekGridView.TIME||kind==WeekGridView.CELL?starts[start]:start;
        int to=kind==WeekGridView.TIME||kind==WeekGridView.CELL?ends[end]:end;
        int top=y(from,g),bottom=y(to,g);
        if(kind==WeekGridView.TIME||kind==WeekGridView.CLOCK)return new int[]{0,top,g.time,bottom};
        if(kind==WeekGridView.BAND)return new int[]{0,top,g.width,bottom};
        int span=g.x(day)-g.x(day-1),left=g.x(day-1)+span*lane/lanes+1,right=g.x(day-1)+span*(lane+1)/lanes-1;
        // Give entries a visible hit strip, but never stretch empty time cells into adjacent rows.
        int min=kind==WeekGridView.EVENT?Math.max(3,(g.height-g.header)/120):0;
        int b=Math.min(g.height,Math.max(top+1+min,bottom-1)),t=Math.min(top+1,Math.max(g.header,b-min));
        return new int[]{left,t,Math.max(left,right),Math.max(t,b)};
    }
}
