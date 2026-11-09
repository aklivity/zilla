/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.binding.a2a.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import org.junit.Test;

import io.aklivity.zilla.config.binding.a2a.A2aKeywordSearchIndexConfig;
import io.aklivity.zilla.config.binding.a2a.A2aOptionsConfig;
import io.aklivity.zilla.config.binding.a2a.A2aReferencingSearchIndexConfig;

public class A2aOptionsConfigRefsTest
{
    @Test
    public void shouldExposeReferencesOfDiscoverySearchIndexes()
    {
        final A2aReferencingSearchIndexConfig index = new A2aReferencingSearchIndexConfig();

        final A2aOptionsConfig options = A2aOptionsConfig.builder()
            .discovery()
                .search()
                    .index(A2aKeywordSearchIndexConfig.builder().build())
                    .index(index)
                    .build()
                .build()
            .build();

        assertThat(options.refs(), contains(index.reference));
    }

    @Test
    public void shouldExposeNoReferencesWithoutDiscovery()
    {
        assertThat(A2aOptionsConfig.builder().build().refs(), empty());
    }

    @Test
    public void shouldExposeNoReferencesForKeywordIndex()
    {
        final A2aOptionsConfig options = A2aOptionsConfig.builder()
            .discovery()
                .search()
                    .index(A2aKeywordSearchIndexConfig.builder().build())
                    .build()
                .build()
            .build();

        assertThat(options.refs(), empty());
    }
}
