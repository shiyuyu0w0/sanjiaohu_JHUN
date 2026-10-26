package cn.jhun.sanjiaohu;

/**
 * 切页方向的判定。
 *
 * 页码：0 首页 / 1 课程 / 2 个人 / 3 成绩 / 4 考试查询。底栏只有三格，
 * 成绩和考试查询都折回「首页」那一格。
 *
 * 旧版方向算成 `(to>from?1:-1)`，而 to/from 是折算过的标签下标，
 * 于是首页→成绩得到 from==to==0，方向是 0、位移也是 0 —— 看起来就是没有动画。
 * 这里锁住两件事：同层按标签顺序走，跨层按层级走（进二级页从右进、返回从左进）。
 */
public final class PageTransitionTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}

    public static void main(String[] args){
        // 底栏三格折算
        check(MainActivity.tabOf(0)==0);check(MainActivity.tabOf(1)==1);check(MainActivity.tabOf(2)==2);
        check(MainActivity.tabOf(3)==0);check(MainActivity.tabOf(4)==0);

        // 层级：成绩/考试查询是首页的下一层
        check(MainActivity.depthOf(0)==0);check(MainActivity.depthOf(1)==0);check(MainActivity.depthOf(2)==0);
        check(MainActivity.depthOf(3)==1);check(MainActivity.depthOf(4)==1);

        // 同层：按标签顺序决定左右
        check(MainActivity.directionBetween(0,1)==1);
        check(MainActivity.directionBetween(0,2)==1);
        check(MainActivity.directionBetween(1,2)==1);
        check(MainActivity.directionBetween(2,0)==-1);
        check(MainActivity.directionBetween(2,1)==-1);
        check(MainActivity.directionBetween(1,0)==-1);

        // 进入二级页：前进，从右边进来
        check(MainActivity.directionBetween(0,3)==1);
        check(MainActivity.directionBetween(0,4)==1);
        check(MainActivity.directionBetween(1,3)==1);
        check(MainActivity.directionBetween(2,4)==1);

        // 从二级页返回：从左边进来
        check(MainActivity.directionBetween(3,0)==-1);
        check(MainActivity.directionBetween(4,0)==-1);
        check(MainActivity.directionBetween(3,1)==-1);
        check(MainActivity.directionBetween(4,2)==-1);

        // 二级页互跳同层，方向不能是 0（否则位移为 0，等于没动画）
        check(MainActivity.directionBetween(3,4)==1);
        check(MainActivity.directionBetween(4,3)==-1);

        // 任意两页之间方向都必须是 ±1
        for(int from=0;from<=4;from++)for(int to=0;to<=4;to++){
            if(from==to)continue;
            int d=MainActivity.directionBetween(from,to);
            check(d==1||d==-1);
            check(d==-MainActivity.directionBetween(to,from));
        }
        System.out.println("Page transition: "+checks+" checks passed");
    }
}
