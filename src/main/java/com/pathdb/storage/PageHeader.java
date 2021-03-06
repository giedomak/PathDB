/**
 * Copyright (C) 2015-2017 - All rights reserved.
 * This file is part of the pathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.pathdb.storage;

public interface PageHeader
{
    /**
     * The Header of the block when stored as bytes is:
     * (1) Byte - Is this a leaf block?
     * (4) int - The size of the keys, only relevant if keys are the same length.
     * (4) int - the number of keys in this node. A slightly delicate topic in the internal nodes, since they also
     * contain child node ids and not just keys.
     * (8) long - the id to the next node, the sibling node.
     * (8) long - the id to the previous node, the preceding node.
     **/
    int BYTE_POSITION_NODE_TYPE = 0;
    int BYTE_POSITION_KEY_LENGTH = 1;
    int BYTE_POSITION_KEY_COUNT = 5;
    int BYTE_POSITION_SIBLING_ID = 9;
    int BYTE_POSITION_PRECEDING_ID = 17;
    int LEAF_FLAG = 1;
    int NODE_HEADER_LENGTH = 1 + 4 + 4 + 8 + 8;

    boolean isLeafNode();

    boolean isUninitializedNode();

    int getNumberOfKeys();

    void setNumberOfKeys(int numberOfKeys);

    int getKeyLength();

    void setKeyLength( int keyLength );

    void setNodeTypeLeaf();

    long getPrecedingID();

    long getSiblingID();

    void setFollowingID( long followingId );

    void setPrecedingId( long precedingId );

    void initializeLeafNode();

    void initializeInternalNode();
}
