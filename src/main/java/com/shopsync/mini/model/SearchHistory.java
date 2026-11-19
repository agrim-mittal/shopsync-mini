package com.shopsync.mini.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

/**
 * Recent searches, saved to disk between runs.
 *
 * Concepts: Serializable (Lecture 9), serialVersionUID, a transient field,
 * LinkedList used as a fixed-size "recent items" list (Lecture 7).
 */
public class SearchHistory implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int MAX_ENTRIES = 10;

    private final LinkedList<String> entries = new LinkedList<>();

    // transient: NOT written to the file. After loading it is false again.
    private transient boolean changedSinceSave;

    public void add(String query, String bestStore, double bestPrice) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"));
        entries.addFirst(String.format("%s | %s -> %s Rs.%,.0f", time, query, bestStore, bestPrice));
        if (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
        changedSinceSave = true;
    }

    public List<String> getEntries()     { return entries; }
    public boolean isChangedSinceSave()  { return changedSinceSave; }
    public void markSaved()              { changedSinceSave = false; }
}
