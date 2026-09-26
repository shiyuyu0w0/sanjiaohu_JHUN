package org.json;

import java.util.*;

/**
 * Minimal real org.json implementation for LOCAL TEST RUNS ONLY.
 *
 * Android's android.jar ships org.json as compile-only stubs that throw
 * RuntimeException("Stub!") at runtime, so ElectricityApiTest and any test
 * exercising ElectricityApi cannot run on a desktop JVM without this shim.
 * It is test scaffolding: it lives under tests/ (not app/) and is never
 * packaged into the APK. It implements only what the electricity tests use.
 */
public class JSONObject {
    private final Map<String,Object> map=new LinkedHashMap<>();

    public JSONObject(){}
    public JSONObject(String source)throws JSONException{parse(source);}

    public JSONObject put(String key,Object value)throws JSONException{
        map.put(key,value);return this;
    }
    public Object opt(String key){return map.get(key);}
    public Object get(String key)throws JSONException{
        if(!map.containsKey(key))throw new JSONException("missing key: "+key);
        return map.get(key);
    }
    public Object remove(String key){return map.remove(key);}
    public JSONObject getJSONObject(String key)throws JSONException{
        Object v=get(key);
        if(!(v instanceof JSONObject))throw new JSONException("not an object: "+key);
        return (JSONObject)v;
    }
    public Set<String> keySet(){return map.keySet();}
    public Iterator<String> keys(){return map.keySet().iterator();}
    public static String quote(String value){return '"'+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+'"';}
    public String optString(String key){return optString(key,"");}
    public String optString(String key,String fallback){
        Object v=map.get(key);return v==null?fallback:String.valueOf(v);
    }
    public boolean optBoolean(String key){return optBoolean(key,false);}
    public boolean optBoolean(String key,boolean fallback){
        Object v=map.get(key);
        return v instanceof Boolean?(Boolean)v:fallback;
    }
    public JSONObject optJSONObject(String key){
        Object v=map.get(key);return v instanceof JSONObject?(JSONObject)v:null;
    }
    public JSONArray optJSONArray(String key){
        Object v=map.get(key);return v instanceof JSONArray?(JSONArray)v:null;
    }
    public JSONArray getJSONArray(String key)throws JSONException{
        Object v=map.get(key);
        if(!(v instanceof JSONArray))throw new JSONException("not an array: "+key);
        return (JSONArray)v;
    }

    @Override public String toString(){
        StringBuilder sb=new StringBuilder("{");
        boolean first=true;
        for(Map.Entry<String,Object> e:map.entrySet()){
            if(!first)sb.append(',');
            first=false;
            sb.append('"').append(e.getKey()).append("\":").append(value(e.getValue()));
        }
        return sb.append('}').toString();
    }
    static String value(Object v){
        if(v==null)return "null";
        if(v instanceof String)return quote((String)v);
        if(v instanceof JSONObject||v instanceof JSONArray)return v.toString();
        return String.valueOf(v);
    }

    // --- tiny recursive-descent parser, enough for the test payloads ---
    private int at;
    private String src;
    private void parse(String s)throws JSONException{
        src=s;at=0;ws();
        if(at>=src.length()||src.charAt(at)!='{')throw new JSONException("expected object");
        readObject(this);
    }
    private void ws(){while(at<src.length()&&Character.isWhitespace(src.charAt(at)))at++;}
    private String string()throws JSONException{
        if(src.charAt(at)!='"')throw new JSONException("expected string at "+at);
        at++;StringBuilder sb=new StringBuilder();
        while(at<src.length()){
            char c=src.charAt(at++);
            if(c=='"')return sb.toString();
            if(c=='\\'){
                char n=src.charAt(at++);
                switch(n){
                    case 'n':sb.append('\n');break;
                    case 't':sb.append('\t');break;
                    case 'r':sb.append('\r');break;
                    case 'b':sb.append('\b');break;
                    case 'f':sb.append('\f');break;
                    case 'u':sb.append((char)Integer.parseInt(src.substring(at,at+4),16));at+=4;break;
                    default:sb.append(n);
                }
            } else sb.append(c);
        }
        throw new JSONException("unterminated string");
    }
    private void readObject(JSONObject target)throws JSONException{
        at++;ws();
        if(at<src.length()&&src.charAt(at)=='}'){at++;return;}
        while(true){
            ws();String key=string();ws();
            if(src.charAt(at++)!=':')throw new JSONException("expected :");
            ws();target.map.put(key,readValue());ws();
            char c=src.charAt(at++);
            if(c=='}')return;
            if(c!=',')throw new JSONException("expected , or } at "+at);
        }
    }
    private JSONArray readArray()throws JSONException{
        JSONArray arr=new JSONArray();at++;ws();
        if(at<src.length()&&src.charAt(at)==']'){at++;return arr;}
        while(true){
            ws();arr.add(readValue());ws();
            char c=src.charAt(at++);
            if(c==']')return arr;
            if(c!=',')throw new JSONException("expected , or ] at "+at);
        }
    }
    private Object readValue()throws JSONException{
        char c=src.charAt(at);
        if(c=='{'){JSONObject o=new JSONObject();readObject(o);return o;}
        if(c=='[')return readArray();
        if(c=='"')return string();
        if(src.startsWith("true",at)){at+=4;return Boolean.TRUE;}
        if(src.startsWith("false",at)){at+=5;return Boolean.FALSE;}
        if(src.startsWith("null",at)){at+=4;return null;}
        int start=at;
        while(at<src.length()&&"-+.eE0123456789".indexOf(src.charAt(at))>=0)at++;
        String num=src.substring(start,at);
        if(num.isEmpty())throw new JSONException("bad value at "+start);
        if(num.indexOf('.')<0&&num.indexOf('e')<0&&num.indexOf('E')<0){
            try{return Long.valueOf(num);}catch(NumberFormatException ignored){}
        }
        return Double.valueOf(num);
    }
}
