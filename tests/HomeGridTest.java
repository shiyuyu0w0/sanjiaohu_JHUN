package cn.jhun.sanjiaohu;

/**
 * 首页入口网格的排布检查。
 *
 * 首页每行放两个等宽的入口，靠 `for(int j=i;j<i+2;j++)` 取
 * titles/subtitles/icons/actions。这里覆盖两类曾经出过的错：
 *
 *   1. 入口数从 8（偶数）改成 9（奇数）后，内层循环缺 `j&lt;length` 边界，
 *      最后一次取到下标 9，越界抛 ArrayIndexOutOfBoundsException —— 一打开就闪退。
 *   2. 最后一行只剩一个入口时，权重 1 的格子会被拉成整行宽，
 *      比上一行的卡片宽一倍 —— 必须在末尾补一个同权重的占位格子。
 */
public final class HomeGridTest {
    static int checks;
    static void check(boolean result){checks++;if(!result)throw new AssertionError("check "+checks);}

    static final int PLACEHOLDER=-1;
    /** 每行两张卡片之间的水平间距，与 MainActivity.homeView 的 dp(12) 对应。 */
    static final int GAP=MainActivity.HOME_ENTRY_GAP;

    /**
     * 与 MainActivity.homeView() 一致的排布逻辑。
     * 每行补足两个格子，单独一个入口时第二格是占位。
     */
    static int[][] layout(int count){
        int rows=(count+1)/2;
        int[][] grid=new int[rows][];
        for(int i=0,row=0;i<count;i+=2,row++){
            int entries=0;
            for(int j=i;j<i+2&&j<count;j++)entries++;
            // 单独一个入口时补一个占位，保证两个格子等宽
            grid[row]=new int[entries==1?2:entries];
            for(int j=i,k=0;j<i+2&&j<count;j++,k++)grid[row][k]=j;
            if(entries==1)grid[row][1]=PLACEHOLDER;
        }
        return grid;
    }

    /**
     * 每行各格子的 leftMargin，必须与 homeView 一致：
     * 第 1 格 0，第 2 格 GAP —— 占位格也算第 2 格，同样要带间距。
     *
     * 少了这个间距，行内总间距就比满行少 12dp，
     * weight 会把每格撑成 rowWidth/2 而不是 (rowWidth-12)/2，
     * 卡片比上面每张宽 6dp，看起来就是"不等宽"。
     */
    static int[] margins(int count){
        int rows=(count+1)/2;
        int[] out=new int[rows];
        for(int i=0,row=0;i<count;i+=2,row++){
            int entries=0;
            for(int j=i;j<i+2&&j<count;j++)entries++;
            out[row]=GAP;   // 第 2 格（真实或占位）都带 GAP
        }
        return out;
    }

    /** 行内可用宽度按 weight 平分后的单格宽度（px）。 */
    static double cellWidth(double rowWidth,int cells,int gap){
        return (rowWidth-(cells-1)*gap)/cells;
    }

    public static void main(String[] args){
        // 四张表必须等长——长度不一致就是下标越界的根源
        String[] titles=MainActivity.HOME_TITLES;
        String[] subtitles=MainActivity.HOME_SUBTITLES;
        int[] icons=MainActivity.HOME_ICONS;
        check(titles.length==subtitles.length);
        check(titles.length==icons.length);

        // 每行恒为两个等宽格子，且每个入口恰好出现一次
        for(int count=1;count<=titles.length;count++){
            int[][] grid=layout(count);
            int seen=0;
            for(int[] line:grid){
                check(line.length==2);                   // 关键: 每行两个格子, 保证等宽
                for(int index:line){
                    if(index==PLACEHOLDER)continue;      // 占位格子不对应入口
                    check(index>=0&&index<count);        // 关键: 不得越界
                    check(index<subtitles.length);
                    check(index<icons.length);
                    seen++;
                }
            }
            check(seen==count);
        }

        // 本次回归：单独一个入口占半行，不能被拉成整行宽
        int[][] odd=layout(9);
        check(odd.length==5);
        check(odd[4].length==2);                         // 第 5 行仍是两个格子
        check(odd[4][0]==8);                             // 图书查询在第 1 格
        check(odd[4][1]==PLACEHOLDER);                   // 第 2 格是占位
        check(odd[0].length==2&&odd[0][0]==0&&odd[0][1]==1);   // 第 1 行两个入口

        // 每个满行的两个格子权重相同（都是 1），宽度才相等
        for(int[] line:layout(9))check(line.length==2);

        // 关键回归：图书馆卡片必须与上面每张卡片"严格等宽"。
        // 占位格少了 GAP 间距时，单格会被撑宽 6dp，肉眼可见不等宽。
        double rowWidth=1080-36;                         // 屏宽 1080px 减 content 左右各 6dp
        double fullCell=cellWidth(rowWidth,2,GAP);       // 上面满行每张
        double singleCell=cellWidth(rowWidth,2,margins(9)[4]);   // 最后一行
        check(Math.abs(fullCell-singleCell)<0.001);      // 必须完全相等
        int[] rowMargins=margins(9);
        for(int margin:rowMargins)check(margin==GAP);    // 每行第 2 格都带 GAP
        // 若占位格没有间距（gap=0），单格确实会变宽——确认这个断言有防护力
        check(cellWidth(rowWidth,2,0)>fullCell);

        // 图书馆入口必须存在且与图标对应
        int library=-1;
        for(int i=0;i<titles.length;i++)if("图书查询".equals(titles[i]))library=i;
        check(library>=0);
        check(icons[library]==23);                       // 23 = ic_ui_book

        // 行数与容量对得上
        check(layout(titles.length).length==(titles.length+1)/2);
        check(layout(8).length==4);                      // 偶数布局不受影响
        check(layout(8)[3].length==2);
        check(layout(8)[3][0]==6&&layout(8)[3][1]==7);
        check(layout(1).length==1&&layout(1)[0][0]==0);  // 单个入口也补成两格

        // A wrapped card must set the height for ALL rows, including the lone library card.
        int[] measured={112,112,112,112,112,112,120,140,112};
        HomeEntryGrid.Geometry actual=new HomeEntryGrid.Geometry(360,GAP,112,measured);
        check(actual.height==140);
        check(actual.width==174);
        check(actual.top(8)-(actual.top(6)+actual.height)==GAP);
        check(actual.totalHeight==5*140+4*GAP);
        check(actual.left(8)==actual.left(6)&&actual.left(8)==0);
        // Re-measuring on a wider screen can shrink every row back to the natural minimum.
        HomeEntryGrid.Geometry wider=new HomeEntryGrid.Geometry(720,GAP,112,new int[]{110,110,110,110,110,110,110,110,110});
        check(wider.height==112&&wider.width==354);
        for(int count=1;count<=titles.length;count++){
            HomeEntryGrid.Geometry layout=new HomeEntryGrid.Geometry(361,GAP,112,new int[count]);
            for(int index=0;index<count;index++){
                check(layout.width==174&&layout.height==112);
                check(layout.left(index)+layout.width<=361);
                if(index>=2)check(layout.top(index)-layout.top(index-2)-layout.height==GAP);
                if(index%2==1)check(layout.left(index)-layout.left(index-1)-layout.width==GAP);
            }
        }
        System.out.println(checks+" 项首页入口网格检查通过");
    }
}
