/**
 * Copyright (C) 2015-2017 - All rights reserved.
 * This file is part of the pathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.pathdb.util;

import com.pathdb.pathIndex.tree.IndexInsertion;
import com.pathdb.pathIndex.tree.IndexTree;
import com.pathdb.pathIndex.tree.TreeNodeIDManager;
import com.pathdb.storage.DiskCache;
import com.pathdb.storage.PersistedPageHeader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;


public class IndexBulkLoader
{
    private DiskCache disk;
    public int keySize;
    public long finalLeafPage;
    public int MAX_PAIRS;
    private int RESERVED_CHILDREN_SPACE;
    private int currentPair = 0;
    private long currentParent;
    private int currentOffset = 0;
    private long previousLeaf = -1;
    private ParentBufferWriter parentWriter;
    public PageProxyCursor cursor;
    public IndexTree tree;

    public IndexBulkLoader( DiskCache disk, long finalPage, int keySize ) throws IOException
    {
        this.disk = disk;
        this.finalLeafPage = finalPage;
        TreeNodeIDManager.currentID = finalLeafPage + 1;
        this.tree = new IndexTree( keySize, 0, this.disk );
        this.keySize = keySize;
        this.MAX_PAIRS = ((DiskCache.PAGE_SIZE - PersistedPageHeader.NODE_HEADER_LENGTH) / ((keySize + 1) * 8)) - 1;
        this.RESERVED_CHILDREN_SPACE = (MAX_PAIRS + 1) * 8;
        parentWriter = new ParentBufferWriter();
    }

    public IndexTree run() throws IOException
    {
        long root;
        PageProxyCursor cursor = this.disk.getCursor( 0 );
        long firstInternalNode = IndexTree.acquireNewInternalNode( cursor );
        cursor.goToPage( firstInternalNode );
        PersistedPageHeader.setKeyLength( cursor, keySize );
        this.currentParent = firstInternalNode;

        for ( int i = 0; i < finalLeafPage; i++ )
        {
            addLeafToParent( cursor, i );
        }
        cursor.goToPage( currentParent );
        cursor.setOffset( PersistedPageHeader.NODE_HEADER_LENGTH );
        byte[] children = parentWriter.getChildren();
        cursor.putBytes( children );
        byte[] keys = parentWriter.getKeys();
        cursor.putBytes( keys );
        PersistedPageHeader.setNumberOfKeys( cursor, ((keys.length / keySize) / 8) );
        //Leaf row and one parent row made.
        //Build tree above internal nodes.
        root = buildUpperLeaves( cursor, firstInternalNode );
        tree.rootNodeId = root;
        return tree;
    }

    private void addLeafToParent( PageProxyCursor cursor, long leaf ) throws IOException
    {
        if ( currentPair > MAX_PAIRS )
        {
            cursor.goToPage( this.currentParent );
            cursor.deferWriting();
            cursor.setOffset( PersistedPageHeader.NODE_HEADER_LENGTH );
            cursor.putBytes( parentWriter.getChildren() );
            cursor.putBytes( parentWriter.getKeys() );
            PersistedPageHeader.setNumberOfKeys( cursor, MAX_PAIRS );
            cursor.resumeWriting();
            long newParent = IndexTree.acquireNewInternalNode( cursor );
            cursor.goToPage( newParent );
            PersistedPageHeader.setKeyLength( cursor, keySize );
            IndexTree.updateSiblingAndFollowingIdsInsertion( cursor, this.currentParent, newParent );
            this.currentParent = newParent;
            this.currentOffset = 0;
            this.currentPair = 0;
        }
        if ( this.currentOffset == 0 )
        {
            parentWriter.addChild( leaf );
        }
        else
        {
            cursor.goToPage( leaf );
            parentWriter.addChild( leaf );
            parentWriter.addKey( IndexInsertion.getFirstKeyInNodeAsBytes( cursor ) );
        }
        this.currentPair++;
        this.currentOffset += 8;

    }

    private long buildUpperLeaves( PageProxyCursor cursor, long leftMostNode ) throws IOException
    {
        long firstParent = IndexTree.acquireNewInternalNode( cursor );
        cursor.goToPage( firstParent );
        PersistedPageHeader.setKeyLength( cursor, keySize );
        this.currentParent = firstParent;
        this.currentOffset = 0;
        this.currentPair = 0;
        long currentNode = leftMostNode;
        cursor.goToPage( leftMostNode );
        long nextNode = PersistedPageHeader.getSiblingID( cursor );

        while ( nextNode != -1l )
        {
            copyUpLeafToParent( cursor, currentNode );
            currentNode = nextNode;
            cursor.goToPage( nextNode );
            nextNode = PersistedPageHeader.getSiblingID( cursor );
        }
        copyUpLeafToParent( cursor, currentNode );
        cursor.goToPage( currentParent );
        cursor.deferWriting();
        cursor.setOffset( PersistedPageHeader.NODE_HEADER_LENGTH );
        cursor.putBytes( parentWriter.getChildren() );
        byte[] keys = parentWriter.getKeys();
        cursor.putBytes( keys );
        PersistedPageHeader.setNumberOfKeys( cursor, ((keys.length / keySize) / 8) );
        cursor.resumeWriting();

        if ( firstParent != this.currentParent )
        {
            return buildUpperLeaves( cursor, firstParent );
        }

        else
        {
            return firstParent;
        }
    }

    private void copyUpLeafToParent( PageProxyCursor cursor, long leaf ) throws IOException
    {
        if ( currentPair > MAX_PAIRS )
        {
            cursor.goToPage( this.currentParent );
            cursor.deferWriting();
            cursor.setOffset( PersistedPageHeader.NODE_HEADER_LENGTH );
            cursor.putBytes( parentWriter.getChildren() );
            cursor.putBytes( parentWriter.getKeys() );
            PersistedPageHeader.setNumberOfKeys( cursor, MAX_PAIRS );
            cursor.resumeWriting();
            long newParent = IndexTree.acquireNewInternalNode( cursor );
            cursor.goToPage( newParent );
            PersistedPageHeader.setKeyLength( cursor, keySize );
            IndexTree.updateSiblingAndFollowingIdsInsertion( cursor, this.currentParent, newParent );
            this.currentParent = newParent;
            this.currentOffset = 0;
            this.currentPair = 0;
        }
        if ( this.currentOffset == 0 )
        {
            parentWriter.addChild( leaf );
        }
        else
        {
            cursor.goToPage( leaf );
            parentWriter.addChild( leaf );
            parentWriter.addKey( traverseToFindFirstKeyInLeafAsBytes( cursor ) );
        }
        this.currentPair++;
        this.currentOffset += 8;
    }

    public byte[] traverseToFindFirstKeyInLeafAsBytes( PageProxyCursor cursor ) throws IOException
    {
        if ( PersistedPageHeader.isLeafNode( cursor ) )
        {
            return IndexInsertion.getFirstKeyInNodeAsBytes( cursor );
        }
        else
        {
            long leftMostChild = tree.getChildIdAtIndex( cursor, 0 );
            cursor.goToPage( leftMostChild );
            return traverseToFindFirstKeyInLeafAsBytes( cursor );
        }
    }


    private class ParentBufferWriter
    {
        byte[] children = new byte[RESERVED_CHILDREN_SPACE];
        byte[] keys = new byte[MAX_PAIRS * keySize * 8];
        ByteBuffer cb = ByteBuffer.wrap( children );
        LongBuffer cBuffer = cb.asLongBuffer();
        ByteBuffer kb = ByteBuffer.wrap( keys );

        void addChild( long child )
        {
            cBuffer.put( child );
        }

        void addKey( byte[] key )
        {
            kb.put( key );
        }

        byte[] getChildren()
        {
            int index = cBuffer.position();
            cBuffer.position( 0 );
            if ( index != cBuffer.limit() )
            {
                byte[] partialBytes = new byte[index * 8];
                System.arraycopy( children, 0, partialBytes, 0, partialBytes.length );
                return partialBytes;
            }
            return children;
        }

        byte[] getKeys()
        {
            int index = kb.position();
            kb.position( 0 );
            if ( index != kb.limit() )
            {
                byte[] partialBytes = new byte[index];
                System.arraycopy( keys, 0, partialBytes, 0, partialBytes.length );
                return partialBytes;
            }
            return keys;
        }
    }
}
