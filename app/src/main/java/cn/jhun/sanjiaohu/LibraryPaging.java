package cn.jhun.sanjiaohu;

/** Page numbers belong to a keyword, search field and catalogue route together. */
final class LibraryPaging {
    private LibraryPaging(){}
    static int requested(String keyword,String type,String previousKeyword,String previousType,int page){
        return keyword.equals(previousKeyword)&&type.equals(previousType)?Math.max(1,page):1;
    }
    static int forRoute(String previous,String current,int page){
        return previous!=null&&!previous.isEmpty()&&!previous.equals(current)?1:Math.max(1,page);
    }
}
