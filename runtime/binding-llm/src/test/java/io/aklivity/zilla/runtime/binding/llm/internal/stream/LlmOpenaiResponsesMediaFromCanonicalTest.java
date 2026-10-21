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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmOpenaiResponsesMediaFromCanonicalTest
{
    private static final String DATA = "QUJD".repeat(1250);

    private final LlmOpenaiResponsesMediaFromCanonical image = LlmOpenaiResponsesMediaFromCanonical.image(true);
    private final LlmOpenaiResponsesMediaFromCanonical document = LlmOpenaiResponsesMediaFromCanonical.document(true);
    private final LlmOpenaiResponsesMediaFromCanonical foreignDocument = LlmOpenaiResponsesMediaFromCanonical.document(false);

    @Test
    public void shouldFlattenUrlImage()
    {
        assertTransforms(image,
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png','detail':'low'}}"),
            json("{'type':'input_image','image_url':'https://example.com/a.png','detail':'low'}"));
    }

    @Test
    public void shouldFlattenReferenceImage()
    {
        assertTransforms(image,
            json("{'type':'image_url','image_url':{'file_id':'file_1'}}"),
            json("{'type':'input_image','file_id':'file_1'}"));
    }

    @Test
    public void shouldFlattenInlineImageLargerThanWindow()
    {
        assertTransforms(image,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"),
            json("{'type':'input_image','image_url':'data:image/png;base64," + DATA + "'}"));
    }

    @Test
    public void shouldFlattenDocument()
    {
        assertTransforms(document,
            json("{'type':'file','file':{'filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}}"),
            json("{'type':'input_file','filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}"));
    }

    @Test
    public void shouldRestoreOwnPrefixedMembers()
    {
        assertTransforms(document,
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD','openai:detail':'high'}}"),
            json("{'type':'input_file','file_data':'data:application/pdf;base64,QUJD','detail':'high'}"));
    }

    @Test
    public void shouldDropOtherDialectMembers()
    {
        assertTransforms(foreignDocument,
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD','openai:detail':'high'}," +
                "'anthropic:title':'T'}"),
            json("{'type':'input_file','file_data':'data:application/pdf;base64,QUJD'}"));
    }

    @Test
    public void shouldRoundTripImage()
    {
        assertRoundTrips(LlmOpenaiResponsesMediaToCanonical.image(), image,
            json("{'type':'input_image','detail':'high','image_url':'https://example.com/a.png','extra':1}"));
    }

    @Test
    public void shouldRoundTripDocument()
    {
        assertRoundTrips(LlmOpenaiResponsesMediaToCanonical.document(), document,
            json("{'type':'input_file','filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD','detail':'low'}"));
    }
}
