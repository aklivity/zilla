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
package io.aklivity.zilla.runtime.classifier.semantic.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class SemanticVectorCodec
{
    private static final String VECTOR_DELIMITER = ";";
    private static final String COMPONENT_DELIMITER = ",";

    static String digest(
        List<String> phrases)
    {
        StringBuilder canonical = new StringBuilder();
        for (String phrase : phrases)
        {
            canonical.append(phrase.length()).append(':').append(phrase);
        }

        try
        {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    static boolean valid(
        float[][] vectors,
        int count)
    {
        boolean valid = vectors != null && vectors.length == count && count > 0;

        for (int i = 0; valid && i < vectors.length; i++)
        {
            float[] vector = vectors[i];
            valid = vector != null && vector.length > 0 && vector.length == vectors[0].length && finite(vector);
        }

        return valid;
    }

    static String encode(
        float[][] vectors)
    {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < vectors.length; i++)
        {
            if (i > 0)
            {
                encoded.append(VECTOR_DELIMITER);
            }

            float[] vector = vectors[i];
            for (int j = 0; j < vector.length; j++)
            {
                if (j > 0)
                {
                    encoded.append(COMPONENT_DELIMITER);
                }
                encoded.append(vector[j]);
            }
        }
        return encoded.toString();
    }

    static float[][] decode(
        String encoded,
        int count)
    {
        float[][] vectors = null;

        if (encoded != null)
        {
            String[] parts = encoded.split(VECTOR_DELIMITER, -1);

            try
            {
                float[][] candidate = new float[parts.length][];
                for (int i = 0; i < parts.length; i++)
                {
                    candidate[i] = asVector(parts[i]);
                }
                vectors = valid(candidate, count) ? candidate : null;
            }
            catch (NumberFormatException ex)
            {
                vectors = null;
            }
        }

        return vectors;
    }

    private static float[] asVector(
        String part)
    {
        String[] components = part.split(COMPONENT_DELIMITER, -1);
        float[] vector = new float[components.length];
        for (int i = 0; i < components.length; i++)
        {
            vector[i] = Float.parseFloat(components[i]);
        }
        return vector;
    }

    private static boolean finite(
        float[] vector)
    {
        boolean finite = true;

        for (int i = 0; finite && i < vector.length; i++)
        {
            finite = Float.isFinite(vector[i]);
        }

        return finite;
    }

    private SemanticVectorCodec()
    {
    }
}
