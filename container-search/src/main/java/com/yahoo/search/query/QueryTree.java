// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.query.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The root node of a query tree. This is always present above the actual semantic root to ease query manipulation,
 * especially replacing the actual semantic root, but does not have any search semantics on its own.
 *
 * <p>To ease recursive manipulation of the query tree, this is a composite having one child, which is the actual root.
 * <ul>
 * <li>Setting the root item (at position 0, either directly or though the iterator of this, works as expected.
 * Setting at any other position is disallowed.
 * <li>Removing the root is allowed and causes this to be a null query.
 * <li>Adding an item is only allowed if this is currently a null query (having no root)
 * </ul>
 *
 * <p>This is also the home of accessor methods which eases querying into and manipulation of the query tree.</p>
 *
 * @author Arne Bergene Fossaa
 */
public class QueryTree extends CompositeItem {

    public QueryTree(Item root) {
        setRoot(root);
    }

    public void setIndexName(String index) {
        if (getRoot() != null)
            getRoot().setIndexName(index);
    }

    public ItemType getItemType() {
        throw new RuntimeException("Packet type access attempted. " +
                "A query tree has no packet code. This is probably a misbehaving searcher.");
    }

    public String getName() { return "ROOT"; }

    public int encode(ByteBuffer buffer) {
        if (getRoot() == null) return 0;
        return getRoot().encode(buffer);
    }

    //Lets not pollute toString() by adding "ROOT"
    protected void appendHeadingString(StringBuilder sb) {
    }

    /** Returns the query root. This is null if this is a null query. */
    public Item getRoot() {
        if (getItemCount()==0) return null;
        return getItem(0);
    }

    public final void setRoot(Item root) {
        if (root==this) throw new IllegalArgumentException("Cannot make a root point at itself");
        if (root == null) throw new IllegalArgumentException("Root must not be null, use NullItem instead.");
        if (root instanceof QueryTree) throw new IllegalArgumentException("Do not use a new QueryTree instance as a root.");
        if (this.getItemCount()==0) // initializing
            super.addItem(root);
        else
            setItem(0,root); // replacing
    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof QueryTree)) return false;
        return super.equals(o);
    }

    /** Returns a deep copy of this */
    @Override
    public QueryTree clone() {
        QueryTree clone = (QueryTree) super.clone();
        fixClonedConnectivityReferences(clone);
        return clone;
    }

    private void fixClonedConnectivityReferences(QueryTree clone) {
        // TODO!
    }

    @Override
    public void addItem(Item item) {
        if (getItemCount()==0)
            super.addItem(item);
        else
            throw new RuntimeException("Programming error: Cannot add multiple roots");
    }

    @Override
    public void addItem(int index, Item item) {
        if (getItemCount()==0 && index==0)
            super.addItem(index,item);
        else
            throw new RuntimeException("Programming error: Cannot add multiple roots, have '" + getRoot() + "'");
    }

    /** Returns true if this represents the null query */
    public boolean isEmpty() {
        return getRoot() instanceof NullItem;
    }

    // -------------- Facade

    /** Modifies this query to become the current query AND the given item */
    // TODO: Make sure this is complete, unit test and make it public
    private void and(Item item) {
        if (isEmpty()) {
            setRoot(item);
        }
        else if (getRoot() instanceof NotItem && item instanceof NotItem) {
            throw new IllegalArgumentException("Can't AND two NOTs"); // TODO: Complete
        }
        else if (getRoot() instanceof NotItem){
            NotItem notItem = (NotItem)getRoot();
            notItem.addPositiveItem(item);
        }
        else if (item instanceof NotItem){
            NotItem notItem = (NotItem)item;
            notItem.addPositiveItem(getRoot());
            setRoot(notItem);
        }
        else {
            AndItem andItem = new AndItem();
            andItem.addItem(getRoot());
            andItem.addItem(item);
            setRoot(andItem);
        }
    }

    /** Returns a flattened list of all positive query terms under the given item */
    public static List<IndexedItem> getPositiveTerms(Item item) {
        List<IndexedItem> items = new ArrayList<>();
        getPositiveTerms(item,items);
        return items;
    }

    private static void getPositiveTerms(Item item, List<IndexedItem> terms) {
        if (item instanceof NotItem) {
            getPositiveTerms(((NotItem) item).getPositiveItem(), terms);
        } else if (item instanceof PhraseItem) {
            PhraseItem pItem = (PhraseItem)item;
            terms.add(pItem);
        } else if (item instanceof CompositeItem) {
            for (Iterator<Item> i = ((CompositeItem) item).getItemIterator(); i.hasNext();) {
                getPositiveTerms(i.next(), terms);
            }
        } else if (item instanceof TermItem) {
            terms.add((TermItem)item);
        }
    }

}
