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
package io.aklivity.zilla.runtime.common.vector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class VectorsTest
{
    @Test
    public void shouldComputeSimilarityOfIdenticalVectors()
    {
        float[] vector = { 1.0f, 2.0f, 3.0f };

        assertEquals(1.0, Vectors.similarity(vector, vector), 0.000001);
    }

    @Test
    public void shouldComputeSimilarityOfOpposingVectors()
    {
        float[] a = { 1.0f, 0.0f };
        float[] b = { -1.0f, 0.0f };

        assertEquals(-1.0, Vectors.similarity(a, b), 0.000001);
    }

    @Test
    public void shouldComputeSimilarityOfOrthogonalVectors()
    {
        float[] a = { 1.0f, 0.0f };
        float[] b = { 0.0f, 1.0f };

        assertEquals(0.0, Vectors.similarity(a, b), 0.000001);
    }

    @Test
    public void shouldComputeZeroSimilarityWhenEitherVectorIsZero()
    {
        float[] zero = { 0.0f, 0.0f };
        float[] other = { 1.0f, 1.0f };

        assertEquals(0.0, Vectors.similarity(zero, other), 0.000001);
    }

    @Test
    public void shouldComputeSimilarityRegardlessOfNormalization()
    {
        float[] a = { 3.0f, 4.0f };
        float[] b = { 6.0f, 8.0f };

        assertEquals(Vectors.similarity(a, b), Vectors.similarity(Vectors.normalize(a), Vectors.normalize(b)), 0.000001);
    }

    @Test
    public void shouldNormalizeToUnitLength()
    {
        float[] normalized = Vectors.normalize(new float[] { 3.0f, 4.0f });

        assertArrayEquals(new float[] { 0.6f, 0.8f }, normalized, 0.000001f);
    }

    @Test
    public void shouldNormalizeZeroVectorToZero()
    {
        float[] normalized = Vectors.normalize(new float[] { 0.0f, 0.0f });

        assertArrayEquals(new float[] { 0.0f, 0.0f }, normalized, 0.0f);
    }
}
