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

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public class HttpBindingConfigTest
{
    @Test
    public void shouldBeEqual1()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("hello", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldBeEqual2()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("hello", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldBeEqual3()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%68%65%6C%6C%6F", "hello");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldBeEqual4()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%68%65%6C%6C%6F", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, equalTo(0));
    }

    @Test
    public void shouldBeNegative1()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("ciao", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeNegative2()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("ciao", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeNegative3()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%63%69%61%6F", "hello");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBeNegative4()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%63%69%61%6F", "%68%65%6C%6C%6F");

        // THEN
        assertThat(result, lessThan(0));
    }

    @Test
    public void shouldBePositive1()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("hello", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBePositive2()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%68%65%6C%6C%6F", "ciao");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBePositive3()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("hello", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }

    @Test
    public void shouldBePositive4()
    {
        // GIVEN
        HttpBindingConfig binding = new HttpBindingConfig(
            BindingConfig.builder().type("http").kind(KindConfig.SERVER).build());

        // WHEN
        int result = binding.urlDecodedComparator("%68%65%6C%6C%6F", "%63%69%61%6F");

        // THEN
        assertThat(result, greaterThan(0));
    }
}
