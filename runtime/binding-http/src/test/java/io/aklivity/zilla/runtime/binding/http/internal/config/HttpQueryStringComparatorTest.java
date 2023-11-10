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

public class HttpQueryStringComparatorTest
{
    @Test
    public void shouldCompareSameUnencodedEqualsToUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldCompareSameUnencodedEqualsToEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldCompareSameEncodedEqualsToUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldCompareSameEncodedEqualsToEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6c%6c%6f", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldCompareSmallerUnencodedLessThanGreaterUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("ciao", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareSmallerUnencodedLessThanGreaterEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("ciao", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareSmallerEncodedLessThanGreaterUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%63%69%61%6F", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareSmallerEncodedLessThanGreaterEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%63%69%61%6f", "%68%65%6c%6c%6f");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareShorterUnencodedLessThanLongerUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "hello1");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareShorterUnencodedLessThanLongerEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "%68%65%6C%6C%6F%49");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareShorterEncodedLessThanLongerUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "hello1");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareShorterEncodedLessThanLongerEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "%68%65%6C%6C%6F%49");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldCompareLargerUnencodedGreaterThanSmallerUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLargerUnencodedGreaterThanSmallerEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLargerEncodedGreaterThanSmallerUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLargerEncodedGreaterThanSmallerEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLongerUnencodedGreaterThanShorterUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello1", "hello");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLongerUnencodedGreaterThanShorterEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("hello1", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLongerEncodedGreaterThanShorterUnencoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F%49", "hello");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldCompareLongerEncodedGreaterThanShorterEncoded()
    {
        // GIVEN
        HttpQueryStringComparator comparator = new HttpQueryStringComparator();

        // WHEN
        int result = comparator.compare("%68%65%6C%6C%6F%49", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }
}
