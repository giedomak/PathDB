/**
 * Copyright (C) 2015-2017 - All rights reserved.
 * This file is part of the pathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.pathdb.pathIndex;

import org.junit.Test;
import com.pathdb.pathIndex.tree.KeyImpl;

import java.util.Random;

public class KeyTest
{
    private static final Random random = new Random();
    private static long sequentialNumber = 0l;



    @Test
    public void testKeyComparison()
    {
        Long[] keyA = new Long[]{1l, 1l, 1l};
        Long[] keyB = new Long[]{1l, 1l, 1l, 1l};
        Long[] keyC = new Long[]{2l, 2l, 2l, 2l};
        Long[] keyD = new Long[]{2l, 3l, 4l, 5l};
        Long[] keyD1 = new Long[]{2l, 3l, 4l, 5l, 6l, 7l};
        Long[] keyD2 = new Long[]{2l, 3l, 4l, 5l, 6l, 7l, 8l};
        KeyImpl comparator = KeyImpl.getComparator();
        assert ((comparator.compare( keyA, keyB )) < 0);
        assert ((comparator.compare( keyB, keyC )) < 0);
        assert ((comparator.compare( keyC, keyD )) < 0);
        assert ((comparator.compare( keyA, keyD )) < 0);
        assert ((comparator.compare( keyB, keyA )) > 0);
        assert ((comparator.compare( keyC, keyB )) > 0);
        assert ((comparator.compare( keyD, keyC )) > 0);
        assert ((comparator.compare( keyD, keyA )) > 0);
        assert (comparator.validPrefix( keyD, keyD1 ));
        assert (comparator.validPrefix( keyD1, keyD2 ));
        assert (comparator.validPrefix( keyD, keyD2 ));
        assert (!comparator.validPrefix( keyD2, keyD1 ));
    }


}
