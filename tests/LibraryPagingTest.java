package cn.jhun.sanjiaohu;

import java.io.IOException;
import java.util.*;

public final class LibraryPagingTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static LibraryApi.Page page(int requested,int pages,String marker){
        LibraryApi.Page p=new LibraryApi.Page();p.page=requested;p.pages=pages;p.total=pages*20;
        LibraryApi.Item item=new LibraryApi.Item();item.title=marker;p.items.add(item);return p;
    }
    public static void main(String[] args)throws Exception{
        check(LibraryPaging.requested("红楼梦","author","红楼梦","title",3)==1);
        check(LibraryPaging.requested("高等数学","title","红楼梦","title",3)==1);
        check(LibraryPaging.requested("红楼梦","title","红楼梦","title",3)==3);
        check(LibraryPaging.requested("红楼梦","title","红楼梦","title",0)==1);
        List<Integer> mobileCalls=new ArrayList<>(),desktopCalls=new ArrayList<>();
        LibraryRoutes.Search failingDesktop=(k,t,n)->{desktopCalls.add(n);throw new IOException("offline");};
        LibraryRoutes.Search mobile=(k,t,n)->{mobileCalls.add(n);return page(n,28,"mobile "+n);};
        LibraryRoutes.reset();
        LibraryApi.Page fallback=LibraryRoutes.search("红楼梦","title",29,LibraryRoutes.OPAC,failingDesktop,mobile);
        check(desktopCalls.equals(Arrays.asList(29)));
        check(mobileCalls.equals(Arrays.asList(1)));
        check(fallback.page==1&&fallback.pages==28&&fallback.pageReset);
        check(fallback.route.equals(LibraryRoutes.MOB));
        check(fallback.items.get(0).title.equals("mobile 1"));
        // The existing route retains its page; recovery to another catalogue resets it.
        mobileCalls.clear();
        LibraryApi.Page next=LibraryRoutes.search("红楼梦","title",2,LibraryRoutes.MOB,failingDesktop,mobile);
        check(mobileCalls.equals(Arrays.asList(2))&&!next.pageReset);
        LibraryRoutes.force(LibraryRoutes.OPAC);
        desktopCalls.clear();
        LibraryRoutes.Search desktop=(k,t,n)->{desktopCalls.add(n);return page(n,38,"desktop "+n);};
        LibraryApi.Page recovered=LibraryRoutes.search("红楼梦","title",3,LibraryRoutes.MOB,desktop,mobile);
        check(desktopCalls.equals(Arrays.asList(1))&&recovered.pageReset);
        // Same route, smaller catalogue: obtain real first-page data instead of relabeling the last page.
        LibraryRoutes.force(LibraryRoutes.MOB);mobileCalls.clear();
        LibraryApi.Page shrunk=LibraryRoutes.search("红楼梦","title",29,LibraryRoutes.MOB,desktop,mobile);
        check(mobileCalls.equals(Arrays.asList(29,1)));
        check(shrunk.page==1&&shrunk.items.get(0).title.equals("mobile 1"));
        LibraryRoutes.force(LibraryRoutes.OPAC);mobileCalls.clear();
        try{
            LibraryRoutes.search("红楼梦","title",2,LibraryRoutes.OPAC,(k,t,n)->{throw new LibraryApi.Cancelled();},mobile);
            throw new AssertionError("Cancellation must propagate");
        }catch(LibraryApi.Cancelled expected){check(mobileCalls.isEmpty());}
        LibraryRoutes.reset();
        detailRoutes();
        System.out.println("PASS: "+checks+" library query, route and paging regressions");
    }

    static LibraryApi.Detail detail(String marker){
        LibraryApi.Detail detail=new LibraryApi.Detail();detail.title=marker;
        detail.holdings.add(new LibraryApi.Holding());return detail;
    }

    /** Exercise real fallback decisions without relying on a live library server. */
    static void detailRoutes()throws Exception{
        List<String> calls=new ArrayList<>();
        LibraryRoutes.DetailSource primary=target->{calls.add("primary");return detail("primary data");};
        LibraryRoutes.DetailSource secondary=target->{calls.add("secondary");return detail("secondary data");};
        LibraryRoutes.DetailSource empty=target->{calls.add("primary empty");return new LibraryApi.Detail();};
        LibraryRoutes.DetailSource offline=target->{calls.add("primary offline");throw new IOException("offline");};
        LibraryRoutes.reset();
        LibraryApi.Detail direct=LibraryRoutes.detail("book",LibraryRoutes.OPAC,primary,secondary);
        check(direct.route.equals(LibraryRoutes.OPAC)&&direct.title.equals("primary data"));
        check(calls.equals(Arrays.asList("primary")));

        // An empty primary detail falls back, while the search list keeps its own source.
        calls.clear();
        LibraryApi.Detail fallback=LibraryRoutes.detail("book",LibraryRoutes.OPAC,empty,secondary);
        check(calls.equals(Arrays.asList("primary empty","secondary")));
        check(fallback.route.equals(LibraryRoutes.MOB)&&fallback.title.equals("secondary data"));
        check(LibraryRoutes.lastRoute().equals(LibraryRoutes.OPAC));
        check(LibraryActivity.routeSubtitle(LibraryRoutes.routeIndicator(LibraryRoutes.lastRoute())).contains("搜索：主线路"));

        // A network failure has the same source tracking, including during the quiet period.
        calls.clear();
        LibraryApi.Detail failedOver=LibraryRoutes.detail("book",LibraryRoutes.OPAC,offline,secondary);
        check(calls.equals(Arrays.asList("primary offline","secondary")));
        check(failedOver.route.equals(LibraryRoutes.MOB));
        check(LibraryRoutes.lastRoute().equals(LibraryRoutes.OPAC));
        calls.clear();
        LibraryApi.Detail quiet=LibraryRoutes.detail("book",LibraryRoutes.OPAC,primary,secondary);
        check(calls.equals(Arrays.asList("secondary"))&&quiet.route.equals(LibraryRoutes.MOB));

        // Use the passed-in page source, even if another search changed global state.
        LibraryRoutes.force(LibraryRoutes.OPAC);calls.clear();
        LibraryApi.Detail mobile=LibraryRoutes.detail("book",LibraryRoutes.MOB,primary,secondary);
        check(calls.equals(Arrays.asList("secondary"))&&mobile.route.equals(LibraryRoutes.MOB));

        calls.clear();
        try{
            LibraryRoutes.detail("book",LibraryRoutes.OPAC,target->{throw new LibraryApi.Cancelled();},secondary);
            throw new AssertionError("Primary detail cancellation must propagate");
        }catch(LibraryApi.Cancelled expected){check(calls.isEmpty());}
        try{
            LibraryRoutes.detail("book",LibraryRoutes.OPAC,empty,target->{throw new LibraryApi.Cancelled();});
            throw new AssertionError("Fallback detail cancellation must propagate");
        }catch(LibraryApi.Cancelled expected){check(true);}

        IOException unavailable=new IOException("secondary offline");
        try{
            LibraryRoutes.detail("book",LibraryRoutes.OPAC,offline,target->{throw unavailable;});
            throw new AssertionError("A failed fallback must not return a detail source");
        }catch(IOException expected){check(expected.getCause()==unavailable);}
        check(LibraryRoutes.lastRoute().equals(LibraryRoutes.OPAC));
        check(LibraryActivity.routeSubtitle("").equals("江汉大学图书馆 · 馆藏与位置"));
        LibraryRoutes.reset();
    }
}
