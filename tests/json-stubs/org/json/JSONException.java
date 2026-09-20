package org.json;

/** Minimal org.json JSONException for LOCAL TEST RUNS ONLY. */
public class JSONException extends RuntimeException {
    public JSONException(String message){super(message);}
    public JSONException(String message,Throwable cause){super(message,cause);}
}
