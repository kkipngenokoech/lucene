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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.mockito.Mockito;

public class TestReaderUtilPartitionWithOrdinals extends LuceneTestCase {

  private List<LeafReaderContext> createMockLeaves(int... maxDocs) {
    List<LeafReaderContext> leaves = new ArrayList<>();
    int docBase = 0;
    
    for (int maxDoc : maxDocs) {
      LeafReader reader = Mockito.mock(LeafReader.class);
      Mockito.when(reader.maxDoc()).thenReturn(maxDoc);
      
      LeafReaderContext context = Mockito.mock(LeafReaderContext.class);
      Mockito.when(context.reader()).thenReturn(reader);
      Mockito.when(context.docBase).thenReturn(docBase);
      
      leaves.add(context);
      docBase += maxDoc;
    }
    
    return leaves;
  }

  public void testPartitionByLeafWithOrdinalsEmpty() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    int[] globalDocIds = {};
    
    ReaderUtil.PartitionedHits result = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    assertEquals(3, result.docIdsByLeaf().length);
    assertEquals(0, result.ordinals().length);
    
    for (int[] leafDocs : result.docIdsByLeaf()) {
      assertNull(leafDocs);
    }
  }

  public void testPartitionByLeafWithOrdinalsSingleLeaf() {
    List<LeafReaderContext> leaves = createMockLeaves(100);
    int[] globalDocIds = {5, 15, 25, 35};
    
    ReaderUtil.PartitionedHits result = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    assertEquals(1, result.docIdsByLeaf().length);
    assertNotNull(result.docIdsByLeaf()[0]);
    assertArrayEquals(new int[]{5, 15, 25, 35}, result.docIdsByLeaf()[0]);
    assertArrayEquals(new int[]{0, 1, 2, 3}, result.ordinals());
  }

  public void testPartitionByLeafWithOrdinalsMultipleLeaves() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    // Global doc IDs: leaf0=[0-9], leaf1=[10-29], leaf2=[30-59]
    int[] globalDocIds = {5, 15, 35, 25, 8}; // Mixed order across leaves
    
    ReaderUtil.PartitionedHits result = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    assertEquals(3, result.docIdsByLeaf().length);
    
    // Leaf 0: should contain docs 5, 8 (converted to local doc IDs)
    assertNotNull(result.docIdsByLeaf()[0]);
    assertArrayEquals(new int[]{5, 8}, result.docIdsByLeaf()[0]);
    
    // Leaf 1: should contain docs 15, 25 (converted to local doc IDs 5, 15)
    assertNotNull(result.docIdsByLeaf()[1]);
    assertArrayEquals(new int[]{5, 15}, result.docIdsByLeaf()[1]);
    
    // Leaf 2: should contain doc 35 (converted to local doc ID 5)
    assertNotNull(result.docIdsByLeaf()[2]);
    assertArrayEquals(new int[]{5}, result.docIdsByLeaf()[2]);
    
    // Ordinals should map back to original positions
    assertEquals(5, result.ordinals().length);
    // The ordinals correspond to the sorted order: [5->0, 8->4, 15->1, 25->3, 35->2]
    assertArrayEquals(new int[]{0, 4, 1, 3, 2}, result.ordinals());
  }

  public void testPartitionByLeafWithOrdinalsGatherPattern() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    int[] globalDocIds = {35, 5, 25, 15}; // Out of order
    
    ReaderUtil.PartitionedHits result = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    // Simulate gathering results back to original order
    String[] values = new String[globalDocIds.length];
    String[] leafResults = {"leaf0_doc5", "leaf0_doc8", "leaf1_doc5", "leaf1_doc15", "leaf2_doc5"};
    
    // The ordinals tell us where each result should go in the original array
    int resultIndex = 0;
    for (int leafIndex = 0; leafIndex < result.docIdsByLeaf().length; leafIndex++) {
      int[] leafDocs = result.docIdsByLeaf()[leafIndex];
      if (leafDocs != null) {
        for (int i = 0; i < leafDocs.length; i++) {
          int originalPosition = result.ordinals()[resultIndex++];
          values[originalPosition] = "result_for_doc_" + globalDocIds[originalPosition];
        }
      }
    }
    
    // Verify results are in original input order
    assertEquals("result_for_doc_35", values[0]);
    assertEquals("result_for_doc_5", values[1]);
    assertEquals("result_for_doc_25", values[2]);
    assertEquals("result_for_doc_15", values[3]);
  }

  public void testPartitionByLeafWithOrdinalsDuplicateDocIds() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    int[] globalDocIds = {5, 15, 5, 25}; // Duplicate doc ID 5
    
    ReaderUtil.PartitionedHits result = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    // Leaf 0: should contain both instances of doc 5
    assertNotNull(result.docIdsByLeaf()[0]);
    assertArrayEquals(new int[]{5, 5}, result.docIdsByLeaf()[0]);
    
    // Leaf 1: should contain docs 15, 25
    assertNotNull(result.docIdsByLeaf()[1]);
    assertArrayEquals(new int[]{5, 15}, result.docIdsByLeaf()[1]);
    
    // Ordinals should handle duplicates correctly
    assertEquals(4, result.ordinals().length);
    // Sorted order: [5->0, 5->2, 15->1, 25->3]
    assertArrayEquals(new int[]{0, 2, 1, 3}, result.ordinals());
  }

  public void testPartitionByLeafCompatibilityWithScoreDoc() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    
    // Test that both methods produce the same partitioning for the same doc IDs
    ScoreDoc[] scoreDocs = {
        new ScoreDoc(5, 1.0f),
        new ScoreDoc(15, 2.0f),
        new ScoreDoc(35, 3.0f)
    };
    
    int[] globalDocIds = {5, 15, 35};
    
    int[][] scoreDocResult = ReaderUtil.partitionByLeaf(scoreDocs, leaves);
    ReaderUtil.PartitionedHits ordinalResult = ReaderUtil.partitionByLeafWithOrdinals(globalDocIds, leaves);
    
    assertEquals(scoreDocResult.length, ordinalResult.docIdsByLeaf().length);
    
    for (int i = 0; i < scoreDocResult.length; i++) {
      if (scoreDocResult[i] == null) {
        assertNull(ordinalResult.docIdsByLeaf()[i]);
      } else {
        assertArrayEquals(scoreDocResult[i], ordinalResult.docIdsByLeaf()[i]);
      }
    }
  }

  public void testPartitionByLeafEmptyScoreDoc() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    ScoreDoc[] scoreDocs = {};
    
    int[][] result = ReaderUtil.partitionByLeaf(scoreDocs, leaves);
    
    assertEquals(3, result.length);
    for (int[] leafDocs : result) {
      assertNull(leafDocs);
    }
  }

  public void testPartitionByLeafScoreDocOutOfBounds() {
    List<LeafReaderContext> leaves = createMockLeaves(10, 20, 30);
    ScoreDoc[] scoreDocs = {
        new ScoreDoc(5, 1.0f),
        new ScoreDoc(65, 2.0f) // Out of bounds (total maxDoc is 60)
    };
    
    int[][] result = ReaderUtil.partitionByLeaf(scoreDocs, leaves);
    
    // Doc 5 should be in leaf 0
    assertNotNull(result[0]);
    assertArrayEquals(new int[]{5}, result[0]);
    
    // Doc 65 is out of bounds, should not appear in any leaf
    assertNull(result[1]);
    assertNull(result[2]);
  }
}