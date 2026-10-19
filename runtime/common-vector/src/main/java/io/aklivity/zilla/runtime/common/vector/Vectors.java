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

/**
 * Operations on fixed-size numeric vectors, such as the embedding vectors produced by an
 * {@code EmbeddingHandler}.
 * <p>
 * {@link #similarity(float[], float[])} does not require its inputs to be pre-normalized —
 * it divides by the product of both vectors' magnitudes internally, so it is correct whether
 * or not a caller has normalized either vector. {@link #normalize(float[])} is therefore an
 * independent, optional transform: a caller comparing one vector against many others may
 * normalize once up front and use a plain dot product instead of repeatedly computing
 * {@link #similarity(float[], float[])}, but nothing here requires that choice.
 * </p>
 */
public final class Vectors
{
    private Vectors()
    {
    }

    /**
     * Returns the cosine similarity of {@code a} and {@code b}, in the range {@code [-1.0, 1.0]}.
     * <p>
     * Compares only the leading {@code min(a.length, b.length)} elements. Returns {@code 0.0}
     * if either vector has zero magnitude.
     * </p>
     *
     * @param a  the first vector
     * @param b  the second vector
     * @return the cosine similarity of {@code a} and {@code b}
     */
    public static double similarity(
        float[] a,
        float[] b)
    {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length && i < b.length; i++)
        {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return normA == 0.0 || normB == 0.0 ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Returns a new vector with the same direction as {@code vector}, scaled to unit (L2) length.
     * <p>
     * Returns a zero-filled copy if {@code vector} has zero magnitude.
     * </p>
     *
     * @param vector  the vector to normalize
     * @return a new, L2-normalized copy of {@code vector}
     */
    public static float[] normalize(
        float[] vector)
    {
        double sumOfSquares = 0.0;
        for (int i = 0; i < vector.length; i++)
        {
            sumOfSquares += vector[i] * vector[i];
        }

        float[] normalized = new float[vector.length];
        if (sumOfSquares != 0.0)
        {
            double magnitude = Math.sqrt(sumOfSquares);
            for (int i = 0; i < vector.length; i++)
            {
                normalized[i] = (float)(vector[i] / magnitude);
            }
        }

        return normalized;
    }
}
