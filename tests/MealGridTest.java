package cn.jhun.sanjiaohu;
public final class MealGridTest {
    static int checks;
    static void check(boolean condition){checks++;if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        for(int w:new int[]{300,320,360,390,412,540,720,1080})for(int h:new int[]{240,300,420,600,900,1600}){
            GridGeometry grid=new GridGeometry(w,h,33,32,14);
            check(grid.y(12)==h);check(grid.periodBottom(12)==h);
            for(int p=1;p<=12;p++){
                check(grid.periodBottom(p)>grid.periodTop(p));
                for(int day=1;day<=7;day++){
                    int[] card=grid.bounds(2,day,p,p,0,1),time=grid.bounds(1,day,p,p,0,1);
                    check(card[2]>card[0]&&card[3]>card[1]);check(card[0]>=0&&card[2]<=w&&card[1]>=0&&card[3]<=h);
                    check(time[1]<=card[1]&&time[3]>=card[3]);
                }
            }
            for(int after:new int[]{4,8}){
                int[] gap=grid.bounds(4,1,after,after,0,1),before=grid.bounds(2,1,after-1,after,0,1),next=grid.bounds(2,1,after+1,after+2,0,1);
                check(gap[0]==0&&gap[2]==w);check(gap[3]-gap[1]==grid.breakHeight);
                check(before[3]<gap[1]&&next[1]>gap[3]);check(grid.periodTop(after+1)-grid.periodBottom(after)==grid.breakHeight);
            }
        }
        System.out.println(checks+" meal separator and time gutter checks passed");
    }
}
