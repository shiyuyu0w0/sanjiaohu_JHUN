package org.json;

import java.util.*;

/**
 * Minimal real org.json JSONArray for LOCAL TEST RUNS ONLY.
 * See JSONObject.java in this directory for why this shim exists.
 */
public class JSONArray {
    private final List<Object> list=new ArrayList<>();

    public JSONArray(){}
    public JSONArray(String source)throws JSONException{
        JSONObject probe=new JSONObject("{\"a\":"+source+"}");
        Object v=probe.opt("a");
        if(v instanceof JSONArray)list.addAll(((JSONArray)v).list);
        else throw new JSONException("not an array");
    }

    public JSONArray put(Object value){list.add(value);return this;}
    public void add(Object value){list.add(value);}
    public int length(){return list.size();}
    public Object opt(int index){return index>=0&&index<list.size()?list.get(index):null;}
    public JSONObject optJSONObject(int index){
        Object v=opt(index);return v instanceof JSONObject?(JSONObject)v:null;
    }
    public JSONObject getJSONObject(int index)throws JSONException{
        Object v=opt(index);
        if(!(v instanceof JSONObject))throw new JSONException("not an object at "+index);
        return (JSONObject)v;
    }
    public String optString(int index){Object v=opt(index);return v==null?"":String.valueOf(v);}

    @Override public String toString(){
        StringBuilder sb=new StringBuilder("[");
        for(int i=0;i<list.size();i++){
            if(i>0)sb.append(',');
            sb.append(JSONObject.value(list.get(i)));
        }
        return sb.append(']').toString();
    }
}
