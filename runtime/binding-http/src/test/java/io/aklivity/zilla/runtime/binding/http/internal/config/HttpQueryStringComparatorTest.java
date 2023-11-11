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
import static org.hamcrest.comparator.ComparatorMatcherBuilder.comparedBy;

import org.junit.Test;

public class HttpQueryStringComparatorTest
{
    private final HttpQueryStringComparator comparator = new HttpQueryStringComparator();

    @Test
    public void shouldCompareSameUnencodedEqualsToUnencoded()
    {
        assertThat("hello", comparedBy(comparator).comparesEqualTo("hello"));
    }

    @Test
    public void shouldCompareSameUnencodedEqualsToEncoded()
    {
        assertThat("hello", comparedBy(comparator).comparesEqualTo("%68%65%6C%6C%6F"));
    }

    @Test
    public void shouldCompareSameEncodedEqualsToUnencoded()
    {
        assertThat("%68%65%6C%6C%6F", comparedBy(comparator).comparesEqualTo("hello"));
    }

    @Test
    public void shouldCompareSameEncodedEqualsToEncoded()
    {
        assertThat("%68%65%6c%6c%6f", comparedBy(comparator).comparesEqualTo("%68%65%6C%6C%6F"));
    }

    @Test
    public void shouldCompareSmallerUnencodedLessThanLargerUnencoded()
    {
        assertThat("ciao", comparedBy(comparator).lessThan("hello"));
    }

    @Test
    public void shouldCompareSmallerUnencodedLessThanLargerEncoded()
    {
        assertThat("ciao", comparedBy(comparator).lessThan("%68%65%6C%6C%6F"));
    }

    @Test
    public void shouldCompareSmallerEncodedLessThanLargerUnencoded()
    {
        assertThat("%63%69%61%6F", comparedBy(comparator).lessThan("hello"));
    }

    @Test
    public void shouldCompareSmallerEncodedLessThanLargerEncoded()
    {
        assertThat("%63%69%61%6F", comparedBy(comparator).lessThan("%68%65%6c%6c%6f"));
    }

    @Test
    public void shouldCompareShorterUnencodedLessThanLongerUnencoded()
    {
        assertThat("hello", comparedBy(comparator).lessThan("hello1"));
    }

    @Test
    public void shouldCompareShorterUnencodedLessThanLongerEncoded()
    {
        assertThat("hello", comparedBy(comparator).lessThan("%68%65%6C%6C%6F%49"));
    }

    @Test
    public void shouldCompareShorterEncodedLessThanLongerUnencoded()
    {
        assertThat("%68%65%6C%6C%6F", comparedBy(comparator).lessThan("hello1"));
    }

    @Test
    public void shouldCompareShorterEncodedLessThanLongerEncoded()
    {
        assertThat("%68%65%6C%6C%6F", comparedBy(comparator).lessThan("%68%65%6C%6C%6F%49"));
    }

    @Test
    public void shouldCompareLargerUnencodedGreaterThanSmallerUnencoded()
    {
        assertThat("hello", comparedBy(comparator).greaterThan("ciao"));
    }

    @Test
    public void shouldCompareLargerUnencodedGreaterThanSmallerEncoded()
    {
        assertThat("hello", comparedBy(comparator).greaterThan("%63%69%61%6F"));
    }

    @Test
    public void shouldCompareLargerEncodedGreaterThanSmallerUnencoded()
    {
        assertThat("%68%65%6C%6C%6F", comparedBy(comparator).greaterThan("ciao"));
    }

    @Test
    public void shouldCompareLargerEncodedGreaterThanSmallerEncoded()
    {
        assertThat("%68%65%6C%6C%6F", comparedBy(comparator).greaterThan("%63%69%61%6F"));
    }

    @Test
    public void shouldCompareLongerUnencodedGreaterThanShorterUnencoded()
    {
        assertThat("hello1", comparedBy(comparator).greaterThan("hello"));
    }

    @Test
    public void shouldCompareLongerUnencodedGreaterThanShorterEncoded()
    {
        assertThat("hello1", comparedBy(comparator).greaterThan("%68%65%6C%6C%6F"));
    }

    @Test
    public void shouldCompareLongerEncodedGreaterThanShorterUnencoded()
    {
        assertThat("%68%65%6C%6C%6F%49", comparedBy(comparator).greaterThan("hello"));
    }

    @Test
    public void shouldCompareLongerEncodedGreaterThanShorterEncoded()
    {
        assertThat("%68%65%6C%6C%6F%49", comparedBy(comparator).greaterThan("%68%65%6C%6C%6F"));
    }
}
