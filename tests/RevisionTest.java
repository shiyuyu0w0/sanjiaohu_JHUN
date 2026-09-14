package cn.jhun.sanjiaohu;
public final class RevisionTest {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(!CachePolicy.usable(1789135487474L,""),"discard bundled 1.0 snapshot on upgrade");
        check(CachePolicy.usable(1789135487475L,""),"preserve a real 1.0 synchronization");
        check(CachePolicy.usable(1789135487474L,"school-sync"),"explicit synchronization provenance wins");
        check(!CachePolicy.usable(0,""),"no seeded data on first launch");
        for(int width:new int[]{300,320,340,360,390,412,540,720,1080,1440}){
            for(int height:new int[]{240,360,460,600,900,1600}){
                GridGeometry g=new GridGeometry(width,height,25,32);
                check(g.x(7)==width,"Sunday must end within screen");check(g.y(12)==height,"period 12 must end within screen");
                for(int d=1;d<=7;d++){check(g.x(d)>g.x(d-1),"positive column width");check(g.x(d)<=width,"no horizontal overflow");}
                for(int p=1;p<=12;p++){check(g.y(p)>g.y(p-1),"positive period height");check(g.y(p)<=height,"no vertical overflow");}
                for(int day=1;day<=7;day++)for(int period=1;period<=12;period++){
                    int[] r=g.bounds(2,day,period,period,0,1);
                    check(r[2]>r[0]&&r[3]>r[1],"course measured size is positive");
                    check(r[0]>=0&&r[1]>=0&&r[2]<=width&&r[3]<=height,"course layout is within viewport");
                }
                int[] whole=g.bounds(2,7,9,12,0,1);
                check(whole[2]==width-1 && whole[3]==height-1,"last course is positioned without a second layout event");
            }
        }
        System.out.println("PASS: "+checks+" upgrade and viewport checks (60 viewport sizes)");
    }
}
