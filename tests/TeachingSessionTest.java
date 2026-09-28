package cn.jhun.sanjiaohu;
public final class TeachingSessionTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Session policy check "+checks);}
    public static void main(String[] args){
        String id="0123456789ABCDEF0123456789ABCDEF";
        check(id.equals(TeachingSessionStore.sessionId("JSESSIONID="+id)));
        check(id.equals(TeachingSessionStore.sessionId("other=public; JSESSIONID="+id+"; login_name=test")));
        check(id.equals(TeachingSessionStore.sessionId("JSESSIONID="+id+"; JSESSIONID="+id)));
        check(TeachingSessionStore.sessionId("JSESSIONID="+id+"; JSESSIONID="+id+"X")==null);
        for(String bad:new String[]{null,"","JSESSIONID=short","JSESSIONID="+id+"\r\nCookie: other=bad","JSESSIONID="+id+"; Domain=outside.invalid; JSESSIONID=bad","jsessionid="+id})check(TeachingSessionStore.sessionId(bad)==null);
        check(TeachingSessionStore.valid(id+".node_1"));
        check(!TeachingSessionStore.valid(id+"; Path=/cas"));
        check(!TeachingSessionStore.valid(id+"\n"));
        System.out.println("Teaching session cookie policy: "+checks+" checks passed");
    }
}
