/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

class FeatureFilterTest
{
    @Test
    void shouldReportIsIncubatorEnabledConsistentlyWithFeatureEnabled()
    {
        boolean incubatorEnabled = FeatureFilter.isIncubatorEnabled();

        assertEquals(incubatorEnabled, FeatureFilter.featureEnabled(IncubatingFeature.class));
        assertTrue(FeatureFilter.featureEnabled(StableFeature.class));
    }

    @Test
    void shouldFilterProvidersConsistentlyWithIsIncubatorEnabled()
    {
        List<Object> providers = List.of(new StableFeature(), new IncubatingFeature());

        long filtered = StreamSupport.stream(FeatureFilter.filter(providers).spliterator(), false).count();

        assertEquals(FeatureFilter.isIncubatorEnabled() ? 2L : 1L, filtered);
    }

    @Test
    void shouldReportIsInternalEnabledConsistentlyWithFeatureEnabled()
    {
        boolean internalEnabled = FeatureFilter.isInternalEnabled();

        assertEquals(internalEnabled, FeatureFilter.featureEnabled(InternalFeature.class));
        assertTrue(FeatureFilter.featureEnabled(StableFeature.class));
    }

    @Test
    void shouldFilterInternalConsistentlyWithIsInternalEnabled()
    {
        List<Object> providers = List.of(new StableFeature(), new InternalFeature());

        long filtered = StreamSupport.stream(FeatureFilter.filter(providers).spliterator(), false).count();

        assertEquals(FeatureFilter.isInternalEnabled() ? 2L : 1L, filtered);
    }

    @Incubating
    private static final class IncubatingFeature
    {
    }

    @Internal
    private static final class InternalFeature
    {
    }

    private static final class StableFeature
    {
    }
}
