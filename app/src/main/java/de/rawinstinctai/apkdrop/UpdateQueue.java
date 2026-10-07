package de.rawinstinctai.apkdrop;

import java.util.*;

/** Only explicit update sessions. Failed/cancelled installs stay on the same item. */
final class UpdateQueue {
    private final List<String> remaining;
    UpdateQueue(List<String> slugs) {
        if(slugs.size()>50 || new HashSet<>(slugs).size()!=slugs.size()) throw new IllegalArgumentException("Ungültige Update-Liste.");
        for(String slug:slugs) if(slug==null || !slug.matches("[a-z0-9-]{3,40}")) throw new IllegalArgumentException("Ungültige App.");
        remaining=Collections.unmodifiableList(new ArrayList<>(slugs));
    }
    String current() { return remaining.isEmpty()?null:remaining.get(0); }
    int size() { return remaining.size(); }
    UpdateQueue next() { return remaining.isEmpty()?this:new UpdateQueue(remaining.subList(1,remaining.size())); }
    String encode() { return "1:"+String.join(",",remaining); }
    static UpdateQueue decode(String encoded) {
        if(encoded==null) return new UpdateQueue(Collections.emptyList());
        if(encoded.length()>2200 || !encoded.startsWith("1:")) throw new IllegalArgumentException("Ungültige Update-Liste.");
        return new UpdateQueue(encoded.length()==2?Collections.emptyList():Arrays.asList(encoded.substring(2).split(",",-1)));
    }
}
