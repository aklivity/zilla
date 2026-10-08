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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

public class SemanticVectorCodecTest
{
    @Test
    public void shouldRoundTripVectors()
    {
        float[][] vectors = {{1.0f, -0.5f, 0.25f}, {0.0f, 2.5f, 3.75f}};

        float[][] decoded = SemanticVectorCodec.decode(SemanticVectorCodec.encode(vectors), 2);

        assertThat(decoded, equalTo(vectors));
    }

    @Test
    public void shouldNotDecodeWhenVectorCountDiffers()
    {
        String encoded = SemanticVectorCodec.encode(new float[][] {{1.0f}, {2.0f}});

        assertThat(SemanticVectorCodec.decode(encoded, 3), nullValue());
    }

    @Test
    public void shouldNotDecodeNullValue()
    {
        assertThat(SemanticVectorCodec.decode(null, 1), nullValue());
    }

    @Test
    public void shouldNotDecodeNullVectorToken()
    {
        assertThat(SemanticVectorCodec.decode("1.0,2.0;-", 2), nullValue());
    }

    @Test
    public void shouldNotDecodeMalformedComponent()
    {
        assertThat(SemanticVectorCodec.decode("1.0,abc", 1), nullValue());
    }

    @Test
    public void shouldNotDecodeNonFiniteComponent()
    {
        assertThat(SemanticVectorCodec.decode("1.0,NaN", 1), nullValue());
        assertThat(SemanticVectorCodec.decode("Infinity,1.0", 1), nullValue());
    }

    @Test
    public void shouldNotDecodeEmptyVector()
    {
        assertThat(SemanticVectorCodec.decode("", 1), nullValue());
    }

    @Test
    public void shouldNotDecodeVectorsOfDifferentDimensions()
    {
        assertThat(SemanticVectorCodec.decode("1.0,2.0;1.0", 2), nullValue());
    }

    @Test
    public void shouldDigestPhrasesDeterministically()
    {
        assertThat(SemanticVectorCodec.digest(List.of("a", "b")), equalTo(SemanticVectorCodec.digest(List.of("a", "b"))));
    }

    @Test
    public void shouldDigestDifferentPhrasesDifferently()
    {
        String digest = SemanticVectorCodec.digest(List.of("a", "b"));

        assertThat(SemanticVectorCodec.digest(List.of("b", "a")), not(equalTo(digest)));
        assertThat(SemanticVectorCodec.digest(List.of("ab")), not(equalTo(digest)));
    }
}
