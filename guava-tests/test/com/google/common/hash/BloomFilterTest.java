/*
 * Copyright (C) 2011 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.hash;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.BloomFilterStrategies.LockFreeBitArray;
import com.google.common.math.LongMath;
import com.google.common.primitives.Ints;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.testing.SerializableTester;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import junit.framework.TestCase;

/**
 * Tests for SimpleGenericBloomFilter and derived BloomFilter views.
 *
 * @author Dimitris Andreou
 */
public class BloomFilterTest extends TestCase {
  private static int NUM_PUTS = 1_000_000;
  private static int GOLDEN_PRESENT_KEY =
      ThreadLocalRandom.current().nextInt();

  @AndroidIncompatible // OutOfMemoryError
  public void testLargeBloomFilterDoesntOverflow() {
    long numBits = Integer.MAX_VALUE;
    numBits++;

    LockFreeBitArray lockFreeBitArray = new LockFreeBitArray(numBits);
    assertTrue(
        "LockFreeBitArray.bitSize() must return a positive number, but was " + lockFreeBitArray
            .bitSize(),
        lockFreeBitArray.bitSize() > 0);

    // Ideally we would also test the bitSize() overflow of this BF, but it runs out of heap space
    // BloomFilter.create(Funnels.unencodedCharsFunnel(), 244412641, 1e-11);
  }

  /**
   * Asserts that {@link BloomFilter#approximateCount} is within 1 percent of the expected value.
   */
  private static void assertApproximateCountGuess(BloomFilter<?> bf, int sizeGuess) {
    assertThat(bf.approximateCount()).isAtLeast((long) (sizeGuess * 0.99));
    assertThat(bf.approximateCount()).isAtMost((long) (sizeGuess * 1.01));
  }

  public void testCreateAndCheckMitz32BloomFilterWithKnownFalsePositives() {
    int numInsertions = 1000000;
    BloomFilter<String> bf = BloomFilter.create(
        Funnels.unencodedCharsFunnel(), numInsertions, 0.03,
        BloomFilterStrategies.MURMUR128_MITZ_32);

    // Insert "numInsertions" even numbers into the BF.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      bf.put(Integer.toString(i));
    }
    assertApproximateCountGuess(bf, numInsertions);

    // Assert that the BF "might" have all of the even numbers.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      assertTrue(bf.mightContain(Integer.toString(i)));
    }

    // Now we check for known false positives using a set of known false positives.
    // (These are all of the false positives under 900.)
    ImmutableSet<Integer> falsePositives = ImmutableSet.of(
        49, 51, 59, 163, 199, 321, 325, 363, 367, 469, 545, 561, 727, 769, 773, 781);
    for (int i = 1; i < 900; i += 2) {
      if (!falsePositives.contains(i)) {
        assertFalse("BF should not contain " + i, bf.mightContain(Integer.toString(i)));
      }
    }

    // Check that there are exactly 29824 false positives for this BF.
    int knownNumberOfFalsePositives = 29824;
    int numFpp = 0;
    for (int i = 1; i < numInsertions * 2; i += 2) {
      if (bf.mightContain(Integer.toString(i))) {
        numFpp++;
      }
    }
    assertEquals(knownNumberOfFalsePositives, numFpp);
    double actualFpp = (double) knownNumberOfFalsePositives / numInsertions;
    double expectedFpp = bf.expectedFpp();
    // The normal order of (expected, actual) is reversed here on purpose.
    assertEquals(actualFpp, expectedFpp, 0.00015);
  }

  public void testCreateAndCheckBloomFilterWithKnownFalsePositives64() {
    int numInsertions = 1000000;
    BloomFilter<String> bf = BloomFilter.create(
        Funnels.unencodedCharsFunnel(), numInsertions, 0.03,
        BloomFilterStrategies.MURMUR128_MITZ_64);

    // Insert "numInsertions" even numbers into the BF.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      bf.put(Integer.toString(i));
    }
    assertApproximateCountGuess(bf, numInsertions);

    // Assert that the BF "might" have all of the even numbers.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      assertTrue(bf.mightContain(Integer.toString(i)));
    }

    // Now we check for known false positives using a set of known false positives.
    // (These are all of the false positives under 900.)
    ImmutableSet<Integer> falsePositives = ImmutableSet.of(
        15, 25, 287, 319, 381, 399, 421, 465, 529, 697, 767, 857);
    for (int i = 1; i < 900; i += 2) {
      if (!falsePositives.contains(i)) {
        assertFalse("BF should not contain " + i, bf.mightContain(Integer.toString(i)));
      }
    }

    // Check that there are exactly 30104 false positives for this BF.
    int knownNumberOfFalsePositives = 30104;
    int numFpp = 0;
    for (int i = 1; i < numInsertions * 2; i += 2) {
      if (bf.mightContain(Integer.toString(i))) {
        numFpp++;
      }
    }
    assertEquals(knownNumberOfFalsePositives, numFpp);
    double actualFpp = (double) knownNumberOfFalsePositives / numInsertions;
    double expectedFpp = bf.expectedFpp();
    // The normal order of (expected, actual) is reversed here on purpose.
    assertEquals(actualFpp, expectedFpp, 0.00033);
  }

  public void testCreateAndCheckBloomFilterWithKnownUtf8FalsePositives64() {
    int numInsertions = 1000000;
    BloomFilter<String> bf = BloomFilter.create(
        Funnels.stringFunnel(UTF_8), numInsertions, 0.03,
        BloomFilterStrategies.MURMUR128_MITZ_64);

    // Insert "numInsertions" even numbers into the BF.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      bf.put(Integer.toString(i));
    }
    assertApproximateCountGuess(bf, numInsertions);

    // Assert that the BF "might" have all of the even numbers.
    for (int i = 0; i < numInsertions * 2; i += 2) {
      assertTrue(bf.mightContain(Integer.toString(i)));
    }

    // Now we check for known false positives using a set of known false positives.
    // (These are all of the false positives under 900.)
    ImmutableSet<Integer> falsePositives =
        ImmutableSet.of(129, 471, 723, 89, 751, 835, 871);
    for (int i = 1; i < 900; i += 2) {
      if (!falsePositives.contains(i)) {
        assertFalse("BF should not contain " + i, bf.mightContain(Integer.toString(i)));
      }
    }

    // Check that there are exactly 29763 false positives for this BF.
    int knownNumberOfFalsePositives = 29763;
    int numFpp = 0;
    for (int i = 1; i < numInsertions * 2; i += 2) {
      if (bf.mightContain(Integer.toString(i))) {
        numFpp++;
      }
    }
    assertEquals(knownNumberOfFalsePositives, numFpp);
    double actualFpp = (double) knownNumberOfFalsePositives / numInsertions;
    double expectedFpp = bf.expectedFpp();
    // The normal order of (expected, actual) is reversed here on purpose.
    assertEquals(actualFpp, expectedFpp, 0.00033);
  }

  /**
   * Sanity checking with many combinations of false positive rates and expected insertions
   */
  public void testBasic() {
    for (double fpr = 0.0000001; fpr < 0.1; fpr *= 10) {
      for (int expectedInsertions = 1; expectedInsertions <= 10000; expectedInsertions *= 10) {
        checkSanity(BloomFilter.create(HashTestUtils.BAD_FUNNEL, expectedInsertions, fpr));
      }
    }
  }

  public void testPreconditions() {
    try {
      BloomFilter.create(Funnels.unencodedCharsFunnel(), -1);
      fail();
    } catch (IllegalArgumentException expected) {}
    try {
      BloomFilter.create(Funnels.unencodedCharsFunnel(), -1, 0.03);
      fail();
    } catch (IllegalArgumentException expected) {}
    try {
      BloomFilter.create(Funnels.unencodedCharsFunnel(), 1, 0.0);
      fail();
    } catch (IllegalArgumentException expected) {}
    try {
      BloomFilter.create(Funnels.unencodedCharsFunnel(), 1, 1.0);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testFailureWhenMoreThan255HashFunctionsAreNeeded() {
    try {
      int n = 1000;
      double p = 0.00000000000000000000000000000000000000000000000000000000000000000000000000000001;
      BloomFilter.create(Funnels.unencodedCharsFunnel(), n, p);
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  public void testNullPointers() {
    NullPointerTester tester = new NullPointerTester();
    tester.testAllPublicInstanceMethods(BloomFilter.create(Funnels.unencodedCharsFunnel(), 100));
    tester.testAllPublicStaticMethods(BloomFilter.class);
  }

  /**
   * Tests that we never get an optimal hashes number of zero.
   */
  public void testOptimalHashes() {
    for (int n = 1; n < 1000; n++) {
      for (int m = 0; m < 1000; m++) {
        assertTrue(BloomFilter.optimalNumOfHashFunctions(n, m) > 0);
      }
    }
  }

  // https://code.google.com/p/guava-libraries/issues/detail?id=1781
  public void testOptimalNumOfHashFunctionsRounding() {
    assertEquals(7, BloomFilter.optimalNumOfHashFunctions(319, 3072));
  }

  /**
   * Tests that we always get a non-negative optimal size.
   */
  public void testOptimalSize() {
    for (int n = 1; n < 1000; n++) {
      for (double fpp = Double.MIN_VALUE; fpp < 1.0; fpp += 0.001) {
        assertTrue(BloomFilter.optimalNumOfBits(n, fpp) >= 0);
      }
    }

    // some random values
    Random random = new Random(0);
    for (int repeats = 0; repeats < 10000; repeats++) {
      assertTrue(BloomFilter.optimalNumOfBits(random.nextInt(1 << 16), random.nextDouble()) >= 0);
    }

    // and some crazy values (this used to be capped to Integer.MAX_VALUE, now it can go bigger
    assertEquals(3327428144502L, BloomFilter.optimalNumOfBits(
        Integer.MAX_VALUE, Double.MIN_VALUE));
    try {
      BloomFilter<String> unused =
          BloomFilter.create(HashTestUtils.BAD_FUNNEL, Integer.MAX_VALUE, Double.MIN_VALUE);
      fail("we can't represent such a large BF!");
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessage("Could not create BloomFilter of 3327428144502 bits");
    }
  }

  @AndroidIncompatible // OutOfMemoryError
  public void testLargeNumberOfInsertions() {
    // We use horrible FPPs here to keep Java from OOM'ing
    BloomFilter<String> unused =
        BloomFilter.create(Funnels.unencodedCharsFunnel(), Integer.MAX_VALUE / 2, 0.28);
    unused = BloomFilter.create(Funnels.unencodedCharsFunnel(), 45L * Integer.MAX_VALUE, 0.99);
  }

  private static void checkSanity(BloomFilter<Object> bf) {
    assertFalse(bf.mightContain(new Object()));
    assertFalse(bf.apply(new Object()));
    for (int i = 0; i < 100; i++) {
      Object o = new Object();
      bf.put(o);
      assertTrue(bf.mightContain(o));
      assertTrue(bf.apply(o));
    }
  }

  public void testCopy() {
    BloomFilter<String> original = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100);
    BloomFilter<String> copy = original.copy();
    assertNotSame(original, copy);
    assertEquals(original, copy);
  }

  public void testExpectedFpp() {
    BloomFilter<Object> bf = BloomFilter.create(HashTestUtils.BAD_FUNNEL, 10, 0.03);
    double fpp = bf.expectedFpp();
    assertEquals(0.0, fpp);
    // usually completed in less than 200 iterations
    while (fpp != 1.0) {
      boolean changed = bf.put(new Object());
      double newFpp = bf.expectedFpp();
      // if changed, the new fpp is strictly higher, otherwise it is the same
      assertTrue(changed ? newFpp > fpp : newFpp == fpp);
      fpp = newFpp;
    }
  }

  @AndroidIncompatible // slow
  public void testBitSize() {
    double fpp = 0.03;
    for (int i = 1; i < 10000; i++) {
      long numBits = BloomFilter.optimalNumOfBits(i, fpp);
      int arraySize = Ints.checkedCast(LongMath.divide(numBits, 64, RoundingMode.CEILING));
      assertEquals(
          arraySize * Long.SIZE,
          BloomFilter.create(Funnels.unencodedCharsFunnel(), i, fpp).bitSize());
    }
  }

  public void testApproximateCount() {
    int numInsertions = 1000;
    BloomFilter<Integer> bf = BloomFilter.create(Funnels.integerFunnel(), numInsertions);
    bf.put(-1);
    for (int i = 0; i < numInsertions; i++) {
      bf.put(i);
    }
    assertApproximateCountGuess(bf, numInsertions);
  }

  public void testEquals_empty() {
    new EqualsTester()
        .addEqualityGroup(BloomFilter.create(Funnels.byteArrayFunnel(), 100, 0.01))
        .addEqualityGroup(BloomFilter.create(Funnels.byteArrayFunnel(), 100, 0.02))
        .addEqualityGroup(BloomFilter.create(Funnels.byteArrayFunnel(), 200, 0.01))
        .addEqualityGroup(BloomFilter.create(Funnels.byteArrayFunnel(), 200, 0.02))
        .addEqualityGroup(BloomFilter.create(Funnels.unencodedCharsFunnel(), 100, 0.01))
        .addEqualityGroup(BloomFilter.create(Funnels.unencodedCharsFunnel(), 100, 0.02))
        .addEqualityGroup(BloomFilter.create(Funnels.unencodedCharsFunnel(), 200, 0.01))
        .addEqualityGroup(BloomFilter.create(Funnels.unencodedCharsFunnel(), 200, 0.02))
        .testEquals();
  }

  public void testEquals() {
    BloomFilter<String> bf1 = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100);
    bf1.put("1");
    bf1.put("2");

    BloomFilter<String> bf2 = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100);
    bf2.put("1");
    bf2.put("2");

    new EqualsTester()
        .addEqualityGroup(bf1, bf2)
        .testEquals();

    bf2.put("3");

    new EqualsTester()
        .addEqualityGroup(bf1)
        .addEqualityGroup(bf2)
        .testEquals();
  }

  public void testEqualsWithCustomFunnel() {
    BloomFilter<Long> bf1 = BloomFilter.create(new CustomFunnel(), 100);
    BloomFilter<Long> bf2 = BloomFilter.create(new CustomFunnel(), 100);
    assertEquals(bf1, bf2);
  }

  public void testSerializationWithCustomFunnel() {
    SerializableTester.reserializeAndAssert(BloomFilter.create(new CustomFunnel(), 100));
  }

  private static final class CustomFunnel implements Funnel<Long> {
    @Override
    public void funnel(Long value, PrimitiveSink into) {
      into.putLong(value);
    }
    @Override
    public boolean equals(@Nullable Object object) {
      return (object instanceof CustomFunnel);
    }
    @Override
    public int hashCode() {
      return 42;
    }
  }

  public void testPutReturnValue() {
    for (int i = 0; i < 10; i++) {
      BloomFilter<String> bf = BloomFilter.create(Funnels.unencodedCharsFunnel(), 100);
      for (int j = 0; j < 10; j++) {
        String value = new Object().toString();
        boolean mightContain = bf.mightContain(value);
        boolean put = bf.put(value);
        assertTrue(mightContain != put);
      }
    }
  }

  public void testPutAll() {
    int element1 = 1;
    int element2 = 2;

    BloomFilter<Integer> bf1 = BloomFilter.create(Funnels.integerFunnel(), 100);
    bf1.put(element1);
    assertTrue(bf1.mightContain(element1));
    assertFalse(bf1.mightContain(element2));

    BloomFilter<Integer> bf2 = BloomFilter.create(Funnels.integerFunnel(), 100);
    bf2.put(element2);
    assertFalse(bf2.mightContain(element1));
    assertTrue(bf2.mightContain(element2));

    assertTrue(bf1.isCompatible(bf2));
    bf1.putAll(bf2);
    assertTrue(bf1.mightContain(element1));
    assertTrue(bf1.mightContain(element2));
    assertFalse(bf2.mightContain(element1));
    assertTrue(bf2.mightContain(element2));
  }

  public void testPutAllDifferentSizes() {
    BloomFilter<Integer> bf1 = BloomFilter.create(Funnels.integerFunnel(), 1);
    BloomFilter<Integer> bf2 = BloomFilter.create(Funnels.integerFunnel(), 10);

    try {
      assertFalse(bf1.isCompatible(bf2));
      bf1.putAll(bf2);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      assertFalse(bf2.isCompatible(bf1));
      bf2.putAll(bf1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testPutAllWithSelf() {
    BloomFilter<Integer> bf1 = BloomFilter.create(Funnels.integerFunnel(), 1);
    try {
      assertFalse(bf1.isCompatible(bf1));
      bf1.putAll(bf1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testJavaSerialization() {
    BloomFilter<byte[]> bf = BloomFilter.create(Funnels.byteArrayFunnel(), 100);
    for (int i = 0; i < 10; i++) {
      bf.put(Ints.toByteArray(i));
    }

    BloomFilter<byte[]> copy = SerializableTester.reserialize(bf);
    for (int i = 0; i < 10; i++) {
      assertTrue(copy.mightContain(Ints.toByteArray(i)));
    }
    assertEquals(bf.expectedFpp(), copy.expectedFpp());

    SerializableTester.reserializeAndAssert(bf);
  }

  public void testCustomSerialization() throws Exception {
    Funnel<byte[]> funnel = Funnels.byteArrayFunnel();
    BloomFilter<byte[]> bf = BloomFilter.create(funnel, 100);
    for (int i = 0; i < 100; i++) {
      bf.put(Ints.toByteArray(i));
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    bf.writeTo(out);

    assertEquals(bf, BloomFilter.readFrom(new ByteArrayInputStream(out.toByteArray()), funnel));
  }

  /**
   * This test will fail whenever someone updates/reorders the BloomFilterStrategies constants.
   * Only appending a new constant is allowed.
   */
  public void testBloomFilterStrategies() {
    assertThat(BloomFilterStrategies.values()).hasLength(2);
    assertEquals(BloomFilterStrategies.MURMUR128_MITZ_32, BloomFilterStrategies.values()[0]);
    assertEquals(BloomFilterStrategies.MURMUR128_MITZ_64, BloomFilterStrategies.values()[1]);
  }

  public void testNoRaceConditions() throws Exception {
    // We use a 1s run time for the automated test suite, but if you make
    // any changes to LockFreeBitArray, you'll probably want to run this
    // manually for a longer time; leaving it overnight (with different
    // params so that it doesn't saturate so quickly) is not a bad idea.
    Duration desiredRunTime = Duration.ofSeconds(1);
    BloomFilter<Integer> bloomFilter =
        BloomFilter.create(Funnels.integerFunnel(), 150_000_000, 0.01);

    // This check has to be BEFORE the loop because the random insertions can
    // flip GOLDEN_PRESENT_KEY to true even if it wasn't explicitly inserted
    // (false positive).
    assertThat(bloomFilter.mightContain(GOLDEN_PRESENT_KEY)).isFalse();
    for (int i = 0; i < NUM_PUTS; i++) {
      bloomFilter.put(getNonGoldenRandomKey());
    }
    bloomFilter.put(GOLDEN_PRESENT_KEY);

    int numThreads = 12;
    double safetyFalsePositiveRate = 0.1;
    Instant startTime = Instant.now();

    Runnable task = () -> {
      do {
        // We can't have a GOLDEN_NOT_PRESENT_KEY because false positives are
        // possible! It's false negatives that can't happen.
        assertThat(bloomFilter.mightContain(GOLDEN_PRESENT_KEY)).isTrue();

        int key = getNonGoldenRandomKey();
        // We can't check that the key is mightContain() == false before the
        // put() because the key could have already been generated *or* the
        // bloom filter might say true even when it's not there (false
        // positive).
        bloomFilter.put(key);
        // False negative should *never* happen.
        assertThat(bloomFilter.mightContain(key)).isTrue();

        // If this check ever fails, that means we need to either bump the
        // number of expected insertions or don't run the test for so long.
        // Don't forget, the bloom filter slowly saturates over time and the
        // expected false positive probability goes up!
        assertThat(bloomFilter.expectedFpp()).isLessThan(safetyFalsePositiveRate);
      } while (Duration.between(startTime, Instant.now())
                       .compareTo(desiredRunTime) < 0);
    };

    List<Throwable> exceptions =
        runThreadsAndReturnExceptions(numThreads, task);

    assertThat(exceptions).isEmpty();
  }

  private static List<Throwable> runThreadsAndReturnExceptions(int numThreads,
                                                               Runnable task) {
    List<Thread> threads = new ArrayList<>(numThreads);
    List<Throwable> exceptions = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      Thread thread = new Thread(task);
      thread.setUncaughtExceptionHandler(
          (unused, exception) -> exceptions.add(exception));
      threads.add(thread);
    }
    threads.forEach(Thread::start);
    threads.forEach(thread -> {
      try {
        thread.join();
      } catch (InterruptedException e) {
        // Do nothing
      }
    });
    return exceptions;
  }

  private static int getNonGoldenRandomKey() {
    int key;
    do {
      key = ThreadLocalRandom.current().nextInt();
    } while (key == GOLDEN_PRESENT_KEY);
    return key;
  }
}
