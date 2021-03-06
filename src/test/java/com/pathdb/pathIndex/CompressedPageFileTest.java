/**
 * Copyright (C) 2015-2017 - All rights reserved.
 * This file is part of the pathdb project which is released under the GPLv3 license.
 * See file LICENSE.txt or go to http://www.gnu.org/licenses/gpl.txt for full license details.
 * You may use, distribute and modify this code under the terms of the GPLv3 license.
 */

package com.pathdb.pathIndex;

import org.junit.Test;

import java.util.LinkedList;

public class CompressedPageFileTest
{

    @Test
    public void insertTest(){
        LinkedList<Long[]> keys = new LinkedList<>();
        for(long i = 0; i < 1200; i++){
            keys.add(new Long[]{i,i,i,i});
        }
    }

}
