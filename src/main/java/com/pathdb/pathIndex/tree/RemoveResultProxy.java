/**
 * Copyright (C) 2015-2017 - All rights reserved.
 * This file is part of the pathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.pathdb.pathIndex.tree;

public class RemoveResultProxy
{
    public long removedNodeId;
    public long siblingNodeID;
    public boolean isLeaf;

    public RemoveResultProxy( long removedNodeId, long siblingNodeID, boolean isLeaf )
    {
        this.removedNodeId = removedNodeId;
        this.siblingNodeID = siblingNodeID;
        this.isLeaf = isLeaf;
    }
}

