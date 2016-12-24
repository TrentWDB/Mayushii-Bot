package com.trentwdb.mayushii.model;

/**
 * Created by mlaux on 12/23/16.
 */
public class SearchResult {
    public final String title;
    public final Pagemap pagemap;

    public SearchResult(String title, Pagemap pagemap) {
        this.title = title;
        this.pagemap = pagemap;
    }
}
