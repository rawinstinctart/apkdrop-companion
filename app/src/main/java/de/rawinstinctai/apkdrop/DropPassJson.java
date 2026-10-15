package de.rawinstinctai.apkdrop;

import java.util.*;

/** Strict JSON subset: safe integers only; duplicate keys, trailing data and excessive depth fail closed. */
final class DropPassJson {
    private final String input;
    private int pos;
    private DropPassJson(String input) { this.input=input; }
    static Object parse(String input) {
        if(input==null || input.length()>262144)throw new IllegalArgumentException("JSON size");
        DropPassJson p=new DropPassJson(input);
        Object value=p.value(0); p.space();
        if(p.pos!=input.length())throw new IllegalArgumentException("Trailing JSON");
        return value;
    }
    private void space() { while(pos<input.length() && " \r\n\t".indexOf(input.charAt(pos))>=0)pos++; }
    private char take() { if(pos>=input.length())throw new IllegalArgumentException("Incomplete JSON"); return input.charAt(pos++); }
    private Object value(int depth) {
        if(depth>48)throw new IllegalArgumentException("JSON depth");
        space(); char c=take();
        if(c=='"')return string();
        if(c=='{') {
            Map<String,Object> map=new TreeMap<>(); space();
            if(pos<input.length() && input.charAt(pos)=='}'){pos++;return map;}
            while(true) {
                space(); if(take()!='"')throw new IllegalArgumentException("Object key");
                String key=string();
                // JS object enumeration reorders array-index keys after sorting. Reject that ambiguity.
                if(key.matches("0|[1-9][0-9]{0,9}") && Long.parseLong(key)<4294967295L)throw new IllegalArgumentException("Array-index object key");
                space(); if(take()!=':' || map.containsKey(key))throw new IllegalArgumentException("Duplicate/key");
                map.put(key,value(depth+1)); space(); char end=take(); if(end=='}')return map;
                if(end!=',')throw new IllegalArgumentException("Object separator");
            }
        }
        if(c=='[') {
            List<Object> list=new ArrayList<>(); space();
            if(pos<input.length() && input.charAt(pos)==']'){pos++;return list;}
            while(true) { list.add(value(depth+1));space();char end=take();if(end==']')return list;if(end!=',')throw new IllegalArgumentException("Array separator"); }
        }
        int start=pos-1;
        if(c=='t' || c=='f' || c=='n') {
            String token=c=='t'?"true":c=='f'?"false":"null";
            if(!input.startsWith(token,start))throw new IllegalArgumentException("Literal");
            pos=start+token.length(); return c=='n'?null:c=='t';
        }
        while(pos<input.length() && ",]} \r\n\t".indexOf(input.charAt(pos))<0)pos++;
        String number=input.substring(start,pos);
        if(!number.matches("-?(0|[1-9][0-9]*)") || number.equals("-0"))throw new IllegalArgumentException("Ambiguous number");
        long n=Long.parseLong(number);
        if(n>9007199254740991L || n< -9007199254740991L)throw new IllegalArgumentException("Unsafe integer");
        return n;
    }
    private String string() {
        StringBuilder b=new StringBuilder();
        while(true) {
            char c=take(); if(c=='"')return b.toString();
            if(c<32)throw new IllegalArgumentException("Control in string");
            if(c=='\\') {
                c=take();
                switch(c) {
                    case '"':case '\\':case '/':break;
                    case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
                    case 'u':
                        int n=0;for(int i=0;i<4;i++){char hex=take();int digit="0123456789abcdef".indexOf(Character.toLowerCase(hex));if(digit<0)throw new IllegalArgumentException("Unicode");n=n*16+digit;}c=(char)n;break;
                    default:throw new IllegalArgumentException("Escape");
                }
            }
            b.append(c);
        }
    }
    static String canonical(Object value) {
        if(value==null)return "null";
        if(value instanceof Boolean || value instanceof Long)return value.toString();
        if(value instanceof String) {
            String s=(String)value;StringBuilder b=new StringBuilder("\"");
            for(int i=0;i<s.length();i++) {
                char c=s.charAt(i);
                switch(c) {
                    case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;
                    case '\b':b.append("\\b");break;case '\f':b.append("\\f");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;
                    default:
                        boolean paired=Character.isHighSurrogate(c) && i+1<s.length() && Character.isLowSurrogate(s.charAt(i+1))
                                || Character.isLowSurrogate(c) && i>0 && Character.isHighSurrogate(s.charAt(i-1));
                        if(c<32 || Character.isSurrogate(c) && !paired)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);
                }
            }
            return b.append('"').toString();
        }
        List<String> parts=new ArrayList<>();
        if(value instanceof Map) {
            Map<String,Object> map=object(value);
            for(String key:new TreeSet<>(map.keySet()))parts.add(canonical(key)+":"+canonical(map.get(key)));
            return "{"+String.join(",",parts)+"}";
        }
        if(value instanceof List) {for(Object item:(List<?>)value)parts.add(canonical(item));return "["+String.join(",",parts)+"]";}
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object value) {
        if(!(value instanceof Map))throw new IllegalArgumentException("Expected object");return (Map<String,Object>)value;
    }
    static String text(Map<String,Object> map,String key) {
        Object value=map.get(key);if(!(value instanceof String) || ((String)value).isEmpty())throw new IllegalArgumentException("Missing "+key);return (String)value;
    }
}
