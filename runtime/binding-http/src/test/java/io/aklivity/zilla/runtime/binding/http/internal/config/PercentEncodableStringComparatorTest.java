/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

import org.junit.Test;

public class PercentEncodableStringComparatorTest
{
    @Test
    public void shouldEqualToZeroWhenComparesIdentical()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("hello", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldEqualToZeroWhenComparesIdenticalWhereSecondIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("hello", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldEqualToZeroWhenComparesIdenticalWhereFirstIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldEqualToZeroWhenComparesIdenticalWhereBothAreEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldBeLessThanZeroWhenComparesSmallerAndLarger()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("ciao", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeLessThanZeroWhenComparesSmallerAndLargerWhereSecondIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("ciao", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeLessThanZeroWhenComparesSmallerAndLargerWhereFirstIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%63%69%61%6F", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeLessThanZeroWhenComparesSmallerAndLargerWhereBothAreEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%63%69%61%6F", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeGreaterThanZeroWhenComparesLargerAndSmaller()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("hello", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBeGreaterThanZeroWhenComparesLargerAndSmallerWhereSecondIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("hello", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBeGreaterThanZeroWhenComparesLargerAndSmallerWhereFirstIsEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBeGreaterThanZeroWhenComparesLargerAndSmallerWhereBothAreEncoded()
    {
        // GIVEN
        PercentEncodableStringComparator comparator = new PercentEncodableStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }
}
