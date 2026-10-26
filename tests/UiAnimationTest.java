package cn.jhun.sanjiaohu;

/** Android layout sentinels must not suppress real card entrance animations. */
public final class UiAnimationTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        for(int density=1;density<=4;density++){
            check(!Ui.isSpacer(-1,-2,density)); // full-width, natural-height card
            check(!Ui.isSpacer(-2,-2,density)); // natural-size widget
            check(!Ui.isSpacer(-1,0,density));  // weighted container
            check(!Ui.isSpacer(160*density,108*density,density));
            check(Ui.isSpacer(1,12*density,density)); // vertical spacing
            check(Ui.isSpacer(12*density,1,density)); // horizontal spacing
        }
        System.out.println("UI animation layout: "+checks+" checks passed");
    }
}
