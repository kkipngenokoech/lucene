/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.util;

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreDoc;

/**
 * Utility methods for working with IndexReaders.
 */
public final class ReaderUtil {

  private ReaderUtil() {} // no instance

  /**
   * Result of partitioning doc IDs by leaf with ordinal tracking.
   * 
   * @param docIdsByLeaf Array where docIdsByLeaf[i] contains the doc IDs for leaf i
   * @param ordinals Array mapping each doc ID to its original position in the input array
   */
  public record PartitionedHits(int[][] docIdsByLeaf, int[] ordinals) {}

  /**
   * Partitions the given ScoreDoc array by leaf reader.
   * 
   * @param hits the ScoreDoc array to partition
   * @param leaves the leaf reader contexts
   * @return array where result[i] contains the doc IDs for leaf i
   */
  public static int[][] partitionByLeaf(ScoreDoc[] hits, List<LeafReaderContext> leaves) {
    if (hits.length == 0) {
      return new int[leaves.size()][];
    }

    // Extract doc IDs and sort them
    int[] docIds = new int[hits.length];
    for (int i = 0; i < hits.length; i++) {
      docIds[i] = hits[i].doc;
    }
    Arrays.sort(docIds);

    return partitionSortedDocIds(docIds, leaves);
  }

  /**
   * Partitions the given global doc ID array by leaf reader with ordinal tracking.
   * This method is designed for scatter/gather patterns where results need to be
   * reassembled in the original input order.
   * 
   * @param globalDocIds the global doc IDs to partition
   * @param leaves the leaf reader contexts
   * @return PartitionedHits containing partitioned doc IDs and ordinals for reassembly
   */
  public static PartitionedHits partitionByLeafWithOrdinals(int[] globalDocIds, List<LeafReaderContext> leaves) {
    if (globalDocIds.length == 0) {
      return new PartitionedHits(new int[leaves.size()][], new int[0]);
    }

    // Create array of (docId, originalIndex) pairs
    int[][] docIdWithIndex = new int[globalDocIds.length][2];
    for (int i = 0; i < globalDocIds.length; i++) {
      docIdWithIndex[i][0] = globalDocIds[i];
      docIdWithIndex[i][1] = i;
    }

    // Sort by doc ID
    Arrays.sort(docIdWithIndex, (a, b) -> Integer.compare(a[0], b[0]));

    // Extract sorted doc IDs
    int[] sortedDocIds = new int[globalDocIds.length];
    for (int i = 0; i < globalDocIds.length; i++) {
      sortedDocIds[i] = docIdWithIndex[i][0];
    }

    // Partition the sorted doc IDs
    int[][] docIdsByLeaf = partitionSortedDocIds(sortedDocIds, leaves);

    // Build ordinals array that maps each partitioned doc ID back to its original position
    int[] ordinals = new int[globalDocIds.length];
    int ordinalIndex = 0;
    for (int leafIndex = 0; leafIndex < docIdsByLeaf.length; leafIndex++) {
      int[] leafDocIds = docIdsByLeaf[leafIndex];
      if (leafDocIds != null) {
        for (int i = 0; i < leafDocIds.length; i++) {
          // Find the original index for this doc ID
          int docId = leafDocIds[i];
          for (int j = 0; j < docIdWithIndex.length; j++) {
            if (docIdWithIndex[j][0] == docId) {
              ordinals[ordinalIndex++] = docIdWithIndex[j][1];
              // Mark as used to handle duplicates correctly
              docIdWithIndex[j][0] = -1;
              break;
            }
          }
        }
      }
    }

    return new PartitionedHits(docIdsByLeaf, ordinals);
  }

  /**
   * Partitions sorted doc IDs by leaf reader.
   * 
   * @param sortedDocIds the sorted doc IDs to partition
   * @param leaves the leaf reader contexts
   * @return array where result[i] contains the doc IDs for leaf i
   */
  private static int[][] partitionSortedDocIds(int[] sortedDocIds, List<LeafReaderContext> leaves) {
    int[][] result = new int[leaves.size()][];
    
    if (sortedDocIds.length == 0) {
      return result;
    }

    // Count docs per leaf
    int[] counts = new int[leaves.size()];
    int docIndex = 0;
    
    for (int leafIndex = 0; leafIndex < leaves.size(); leafIndex++) {
      LeafReaderContext leaf = leaves.get(leafIndex);
      int leafStart = leaf.docBase;
      int leafEnd = leafStart + leaf.reader().maxDoc();
      
      while (docIndex < sortedDocIds.length && sortedDocIds[docIndex] < leafEnd) {
        if (sortedDocIds[docIndex] >= leafStart) {
          counts[leafIndex]++;
        }
        docIndex++;
      }
    }

    // Allocate arrays
    for (int i = 0; i < leaves.size(); i++) {
      if (counts[i] > 0) {
        result[i] = new int[counts[i]];
      }
    }

    // Fill arrays
    docIndex = 0;
    int[] positions = new int[leaves.size()];
    
    for (int leafIndex = 0; leafIndex < leaves.size(); leafIndex++) {
      LeafReaderContext leaf = leaves.get(leafIndex);
      int leafStart = leaf.docBase;
      int leafEnd = leafStart + leaf.reader().maxDoc();
      
      while (docIndex < sortedDocIds.length && sortedDocIds[docIndex] < leafEnd) {
        if (sortedDocIds[docIndex] >= leafStart && result[leafIndex] != null) {
          result[leafIndex][positions[leafIndex]++] = sortedDocIds[docIndex] - leafStart;
        }
        docIndex++;
      }
    }

    return result;
  }
}