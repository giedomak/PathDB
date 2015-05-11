package bptree.impl;


import org.neo4j.io.pagecache.PageCursor;
import org.neo4j.io.pagecache.PagedFile;

import java.io.IOException;

/**
 * Created by max on 5/8/15.
 */
public class NodeDeletion {

    public static RemoveResultProxy remove(long[] key){
        RemoveResultProxy result = null;
        try (PageCursor cursor = NodeTree.pagedFile.io(NodeTree.rootNodeId, PagedFile.PF_EXCLUSIVE_LOCK)) {
            if (cursor.next()) {
                do {
                    if(NodeHeader.isLeafNode(cursor)){
                        result = removeKeyFromLeafNode(cursor, cursor.getCurrentPageId(), key);
                    } else{
                        int index = NodeSearch.search(cursor, key)[0];
                        long child = NodeTree.getChildIdAtIndex(cursor, index);
                        long id = cursor.getCurrentPageId();
                        cursor.next(child);
                        result = remove(cursor, key);
                        if(result != null){
                            cursor.next(id);
                            result = handleRemovedChildren(cursor, id, result);
                        }
                    }
                }
                while (cursor.shouldRetry());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
    private static RemoveResultProxy remove(PageCursor cursor, long[] key) throws IOException {
        RemoveResultProxy result = null;
        if(NodeHeader.isLeafNode(cursor)){
            result = removeKeyFromLeafNode(cursor, cursor.getCurrentPageId(), key);
        }
        else{
            int index = NodeSearch.search(cursor, key)[0];
            long child = NodeTree.getChildIdAtIndex(cursor, index);
            long id = cursor.getCurrentPageId();
            cursor.next(child);
            result = remove(cursor, key);
            if(result != null){
                cursor.next(id);
                result = handleRemovedChildren(cursor, id, result);
            }
        }
        return result;
    }

    public static RemoveResultProxy handleRemovedChildren(PageCursor cursor, long id, RemoveResultProxy result){
        int index = NodeTree.getIndexOfChild(cursor, result.removedNodeId);
        int numberOfKeys = NodeHeader.getNumberOfKeys(cursor);
        int numberOfChildren = numberOfKeys + 1;
        if(result.isLeaf){
            //Delete Child Pointer to deleted child
            //DELETE the key which divides/divided deleted child and mergedIntoChild
            if(index == numberOfKeys && numberOfKeys > 0 && numberOfChildren > 1){
                removeKeyAtIndex(cursor, (index -1)); //More than 1 child, but the right-most child is deleted.
            }
            else if(index < numberOfKeys){ // There exists a key to delete
                removeKeyAtIndex(cursor, index);
            }
            removeChildAtIndex(cursor, index);
        }
        else{//Internal Nodes
            //Delete Child Pointer to deleted child
            //DRAG (MOVE) the key which divides/divided deleted child and mergedIntoChild into mergedIntoChild.
            if(index == numberOfKeys && numberOfKeys > 0 && numberOfChildren > 1){
                removeKeyAtIndex(cursor, index -1); //More than 1 child, but the right-most child is deleted.
            }
            else if(index < numberOfKeys){ // There exists a key to delete
                removeKeyAtIndex(cursor, index);
            }
            removeChildAtIndex(cursor, index);
        }
        if(NodeHeader.getNumberOfKeys(cursor) == -1){
            result.removedNodeId = cursor.getCurrentPageId();
            result.siblingNodeID = NodeHeader.getSiblingID(cursor);
            result.isLeaf = false;
        }
        else{
            result = null;
        }
        return result;
    }

    public static RemoveResultProxy removeKeyAndChildFromInternalNode(PageCursor cursor, long nodeId, long[] key, long child) throws IOException {
        RemoveResultProxy result = null;
        if(NodeHeader.getNumberOfKeys(cursor) == 1){
            result = new RemoveResultProxy(cursor.getCurrentPageId(), NodeHeader.getSiblingID(cursor), true);
            NodeTree.updateSiblingAndFollowingIdsDeletion(cursor, nodeId);
        }
        else{
            int[] searchResult = NodeSearch.search(cursor, key);
            removeKeyAtOffset(cursor, searchResult[1], key);
            removeChildAtIndex(cursor, searchResult[0]);
        }
        return result;
    }

    public static RemoveResultProxy removeKeyFromLeafNode(long nodeId, long[] key){
        RemoveResultProxy result = null;
        try (PageCursor cursor = NodeTree.pagedFile.io(nodeId, PagedFile.PF_EXCLUSIVE_LOCK)) {
            if (cursor.next()) {
                do {
                    result = removeKeyFromLeafNode(cursor, nodeId, key);
                }
                while (cursor.shouldRetry());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static RemoveResultProxy removeKeyFromLeafNode(PageCursor cursor, long nodeId, long[] key) throws IOException {
        RemoveResultProxy result = null;
        if(NodeHeader.getNumberOfKeys(cursor) == 1){
            result = new RemoveResultProxy(cursor.getCurrentPageId(), NodeHeader.getSiblingID(cursor), true);
            NodeTree.updateSiblingAndFollowingIdsDeletion(cursor, nodeId);
        }
        else{
            int[] searchResult = NodeSearch.search(cursor, key);
            removeKeyAtOffset(cursor, searchResult[1], key);
        }
        return result;
    }

    private static void removeKeyAtOffset(PageCursor cursor, int offset, long[] key){
        byte[] tmp_bytes;
        if(NodeHeader.isNodeWithSameLengthKeys(cursor)) {
            tmp_bytes = new byte[DiskCache.PAGE_SIZE - offset - key.length * 8];
            cursor.setOffset(offset + (key.length * 8));
        }
        else{
            tmp_bytes = new byte[DiskCache.PAGE_SIZE - offset - (key.length + 1) * 8];
            cursor.setOffset(offset);
            long tmp = cursor.getLong();
            while(tmp != -1l){
                cursor.getLong();
            }
        }

        cursor.getBytes(tmp_bytes);
        cursor.setOffset(offset);

        cursor.putBytes(tmp_bytes);

        NodeHeader.setNumberOfKeys(cursor, NodeHeader.getNumberOfKeys(cursor) - 1);

    }

    private static void removeKeyAtIndex(PageCursor cursor, int index){
        byte[] tmp_bytes;
        int offset;
        int nodeHeaderOffset = NodeHeader.NODE_HEADER_LENGTH + (NodeHeader.isLeafNode(cursor) ? 0 : (NodeHeader.getNumberOfKeys(cursor) + 1) * 8);
        int keyLength = NodeHeader.getKeyLength(cursor);
        if(NodeHeader.isNodeWithSameLengthKeys(cursor)) {
            offset = nodeHeaderOffset + (index * (keyLength * 8));
            tmp_bytes = new byte[DiskCache.PAGE_SIZE - offset - keyLength * 8];
            cursor.setOffset(offset + (keyLength * 8));
        }
        else{
            cursor.setOffset(nodeHeaderOffset);
            for(int i = 0; i < index; i++) {
                long tmp = cursor.getLong();
                while (tmp != -1l) {
                    cursor.getLong();
                }
            }
            offset = cursor.getOffset();
            tmp_bytes = new byte[DiskCache.PAGE_SIZE - offset - (keyLength + 1) * 8];
            long tmp = cursor.getLong();
            while (tmp != -1l) {
                cursor.getLong();
            }
        }

        cursor.getBytes(tmp_bytes);
        cursor.setOffset(offset);

        cursor.putBytes(tmp_bytes);

        NodeHeader.setNumberOfKeys(cursor, NodeHeader.getNumberOfKeys(cursor) - 1);

    }

    public static void removeChildAtIndex(PageCursor cursor, int index){
        byte[] tmp_bytes;
        int offset = NodeHeader.NODE_HEADER_LENGTH + (index * 8);
        tmp_bytes = new byte[DiskCache.PAGE_SIZE - offset - 8];
        cursor.setOffset(offset + 8);

        cursor.getBytes(tmp_bytes);
        cursor.setOffset(offset);

        cursor.putBytes(tmp_bytes);
    }

    /*public static void addChildToSiblingNode(PageCursor cursor, RemoveResultProxy result, long childId) throws IOException {

        cursor.next(result.removedNodeId);
        long siblingId = NodeHeader.getSiblingID(cursor);
        long precedingNode = NodeHeader.getPrecedingID(cursor);

        if(siblingId != -1){//put it in sibling node
            cursor.next(siblingId);
            NodeTree.getChildIdAtIndex(cursor, 0);
            long[] firstKey =

        }
        else{//put it in preceding node

        }

    }
*/
}