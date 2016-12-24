package com.trentwdb.mayushii.model;

import java.util.List;

/**
 * Created by mlaux on 12/23/16.
 */
public class SearchResults {
    public final List<SearchResult> items;

    public SearchResults(List<SearchResult> items) {
        this.items = items;
    }
}
