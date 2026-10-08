package de.rawinstinctai.apkdrop;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.*;
import java.util.regex.*;

/** Small, bounded native Markdown subset. No HTML, WebView or remote image requests. */
final class ReleaseNotes {
    private static final Pattern INLINE=Pattern.compile("(`[^`\\n]+`|\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|\\*[^*\\n]+\\*|!?\\[([^]\\n]*)\\]\\(([^)\\n]*)\\))");
    private ReleaseNotes() {}
    static SpannableStringBuilder render(String source) {
        SpannableStringBuilder out=new SpannableStringBuilder();
        boolean code=false;
        for(String raw:source.substring(0,Math.min(source.length(),12000)).replace("\r", "").split("\n",-1)) {
            String line=raw.trim();
            if(line.startsWith("```") || line.startsWith("~~~")) {code=!code;continue;}
            if(line.isEmpty()) {if(out.length()>0 && out.charAt(out.length()-1)=='\n') out.append('\n');continue;}
            int start=out.length(),level=0;
            if(!code) {
                Matcher heading=Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(line);
                if(heading.matches()) {level=heading.group(1).length();line=heading.group(2).replaceAll("\\s+#+$", "");}
                else if(line.matches("^[-*+]\\s+.*"))line="• "+line.substring(2).trim();
                else if(line.startsWith("> "))line="│ "+line.substring(2);
            }
            if(code)out.append(raw);else inline(out,line);
            int end=out.length();
            if(end>start && code)out.setSpan(new TypefaceSpan("monospace"),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(end>start && level>0) {
                out.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(level==1?1.35f:1.15f),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            out.append('\n');
        }
        while(out.length()>0 && out.charAt(out.length()-1)=='\n')out.delete(out.length()-1,out.length());
        return out;
    }
    private static void inline(SpannableStringBuilder out,String line) {
        Matcher match=INLINE.matcher(line);int cursor=0;
        while(match.find()) {
            out.append(line.substring(cursor,match.start()));String token=match.group();int start=out.length();
            if(token.startsWith("[")) {
                out.append(match.group(2));
                // Preserve destinations as text, never dispatch untrusted URI schemes.
                if(match.group(3).startsWith("https://"))out.append(" ("+match.group(3)+")");
            } else if(token.startsWith("!["))out.append(match.group(2));
            else {
                int trim=token.startsWith("**")||token.startsWith("__")?2:1;
                out.append(token.substring(trim,token.length()-trim));
                if(out.length()>start)out.setSpan(token.startsWith("`")?new TypefaceSpan("monospace"):
                        new StyleSpan(trim==2?Typeface.BOLD:Typeface.ITALIC),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            cursor=match.end();
        }
        out.append(line.substring(cursor));
    }
    static CharSequence preview(SpannableStringBuilder full) {
        int end=Math.min(full.length(),420),lines=0;
        for(int i=0;i<end;i++)if(full.charAt(i)=='\n'&&++lines==6){end=i;break;}
        if(end>=full.length())return full;
        // Avoid splitting UTF-16 surrogate pairs.
        if(end>0 && Character.isHighSurrogate(full.charAt(end-1)))end--;
        SpannableStringBuilder result=new SpannableStringBuilder(full.subSequence(0,end));
        return result.append(" …");
    }
}
