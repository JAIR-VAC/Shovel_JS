package com.kve.client.gui;

import com.kve.client.gui.recipe.RecipeBrowserScreen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public class VirtualListWidget<T> {

    /** Resolves the two strings this class ever looks at for a given row. */
    public interface Descriptor<T> {
        String id(T item);
        String displayName(T item);
    }

    /** Optional domain hook: does {@code item} carry the tag named by {@code tagToken} (without the leading '#')? */
    public interface TagMatcher<T> {
        boolean hasTag(T item, String tagToken);
    }

    private final Descriptor<T> descriptor;
    private TagMatcher<T> tagMatcher;

    private final List<T> all = new ArrayList<>();
    private final List<T> filtered = new ArrayList<>();
    private final TreeMap<String, List<T>> wordIndex = new TreeMap<>();
    private final Map<T, Integer> originalOrder = new IdentityHashMap<>();

    public VirtualListWidget(Descriptor<T> descriptor) {
        this.descriptor = descriptor;
    }

    public void setTagMatcher(TagMatcher<T> tagMatcher) {
        this.tagMatcher = tagMatcher;
    }

    /**
     * Replaces the backing list and rebuilds the word index. Call once per
     * data refresh (e.g. RecipeBrowserScreen's buildRecipeList()), never per
     * keystroke - that's the whole point.
     */
    public void setItems(List<T> items) {
        all.clear();
        all.addAll(items);
        wordIndex.clear();
        originalOrder.clear();
        for (int i = 0; i < all.size(); i++) {
            T item = all.get(i);
            originalOrder.put(item, i);
            for (String w : wordsOf(item)) {
                wordIndex.computeIfAbsent(w, k -> new ArrayList<>()).add(item);
            }
        }
    }

    public List<T> items() { return all; }

    /** Result of the most recent {@link #filter} call. Same list instance every time - copy out if you need a snapshot. */
    public List<T> filtered() { return filtered; }

    /**
     * Re-filters against the current index. {@code extraFilter} is applied
     * as a separate pass on top of the text query (e.g. RecipeBrowserScreen's
     * SourceFilter ALL/VANILLA/MODDED/KVE/REMOVED) - same two-independent-
     * passes contract the old rebuildFilter() already had. Pass null for no
     * extra filter.
     */
    public void filter(String rawQuery, Predicate<T> extraFilter) {
        filtered.clear();
        List<SearchFilter.Token> tokens = SearchFilter.tokenize(rawQuery);

        if (tokens.isEmpty()) {
            for (T item : all) if (extraFilter == null || extraFilter.test(item)) filtered.add(item);
            return;
        }

        LinkedHashSet<T> candidates = null;
        List<SearchFilter.Token> negatives = new ArrayList<>(2);
        List<SearchFilter.Token> positives = new ArrayList<>(tokens.size());
        for (SearchFilter.Token t : tokens) (t.negated() ? negatives : positives).add(t);

        for (SearchFilter.Token t : positives) {
            LinkedHashSet<T> termMatches = matchTerm(t);
            candidates = (candidates == null) ? termMatches : intersect(candidates, termMatches);
            if (candidates.isEmpty()) break;
        }
        if (candidates == null) candidates = new LinkedHashSet<>(all);

        if (!candidates.isEmpty() && !negatives.isEmpty()) {
            candidates.removeIf(item -> {
                for (SearchFilter.Token t : negatives) if (hits(item, t)) return true;
                return false;
            });
        }

        List<T> out = new ArrayList<>(candidates.size());
        for (T item : candidates) if (extraFilter == null || extraFilter.test(item)) out.add(item);

        if (positives.isEmpty() || out.size() > RANK_LIMIT) {
            out.sort((a, b) -> Integer.compare(orderOf(a), orderOf(b)));
        } else {
            Map<T, Integer> scores = new IdentityHashMap<>(out.size());
            for (T item : out) scores.put(item, score(item, positives));
            out.sort((a, b) -> {
                int byScore = Integer.compare(scores.get(b), scores.get(a));
                return byScore != 0 ? byScore : Integer.compare(orderOf(a), orderOf(b));
            });
        }
        filtered.addAll(out);
    }

    private static final int RANK_LIMIT = 1500;

    private int orderOf(T item) {
        Integer i = originalOrder.get(item);
        return i == null ? Integer.MAX_VALUE : i;
    }

    private int score(T item, List<SearchFilter.Token> positives) {
        String id = descriptor.id(item);
        String name = descriptor.displayName(item);
        int total = 0;
        for (SearchFilter.Token t : positives) {
            total += t.kind() == SearchFilter.Kind.TEXT
                    ? SearchFilter.scoreToken(id, name, t)
                    : 100;
        }
        return total;
    }

    private LinkedHashSet<T> intersect(LinkedHashSet<T> a, LinkedHashSet<T> b) {
        a.retainAll(b);
        return a;
    }

    private boolean hits(T item, SearchFilter.Token t) {
        if (t.kind() == SearchFilter.Kind.TAG && tagMatcher != null) {
            return tagMatcher.hasTag(item, t.raw());
        }
        return SearchFilter.tokenMatches(descriptor.id(item), descriptor.displayName(item), t);
    }

    private LinkedHashSet<T> matchTerm(SearchFilter.Token t) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        String needle = t.norm();

        if (t.kind() == SearchFilter.Kind.TAG) {
            if (tagMatcher == null) {
                for (T item : all) if (hits(item, t)) out.add(item);
                return out;
            }
            for (T item : all) if (tagMatcher.hasTag(item, t.raw())) out.add(item);
            return out;
        }

        for (Map.Entry<String, List<T>> e : wordIndex.tailMap(needle).entrySet()) {
            if (!e.getKey().startsWith(needle)) break;
            out.addAll(e.getValue());
        }
        if (t.kind() == SearchFilter.Kind.NAMESPACE) {
            out.removeIf(item -> !SearchFilter.tokenMatches(descriptor.id(item), null, t));
            return out;
        }

        if (out.isEmpty()) {
            for (T item : all) if (hits(item, t)) out.add(item);
        }
        return out;
    }

    private List<String> wordsOf(T item) {
        List<String> words = new ArrayList<>();
        String id = descriptor.id(item);
        if (id != null) {
            int colon = id.indexOf(':');
            if (colon >= 0) {
                addWords(id.substring(0, colon), words);
                addWords(id.substring(colon + 1), words);
            } else {
                addWords(id, words);
            }
        }
        addWords(descriptor.displayName(item), words);
        return words;
    }

    private void addWords(String text, List<String> out) {
        if (text == null) return;
        for (String w : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!w.isEmpty()) out.add(w);
        }
    }
}
