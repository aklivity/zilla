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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmOpenaiResponsesMediaToCanonicalTest
{
    private static final String DATA = "QUJD".repeat(1250);

    private final LlmOpenaiResponsesMediaToCanonical image = LlmOpenaiResponsesMediaToCanonical.image();
    private final LlmOpenaiResponsesMediaToCanonical document = LlmOpenaiResponsesMediaToCanonical.document();

    @Test
    public void shouldNestUrlImageWithDetail()
    {
        assertTransforms(image,
            json("{'type':'input_image','image_url':'https://example.com/a.png','detail':'low'}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png','detail':'low'}}"));
    }

    @Test
    public void shouldKeepMemberOrder()
    {
        assertTransforms(image,
            json("{'type':'input_image','detail':'high','image_url':'https://example.com/a.png'}"),
            json("{'type':'image_url','image_url':{'detail':'high','url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldNestReferenceImage()
    {
        assertTransforms(image,
            json("{'type':'input_image','file_id':'file_1'}"),
            json("{'type':'image_url','image_url':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldDropNullMembers()
    {
        assertTransforms(image,
            json("{'type':'input_image','file_id':null,'image_url':'https://example.com/a.png','detail':null}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldNestInlineImageLargerThanWindow()
    {
        assertTransforms(image,
            json("{'type':'input_image','image_url':'data:image/png;base64," + DATA + "'}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"));
    }

    @Test
    public void shouldPrefixUnknownMembersAtTheirPeers()
    {
        assertTransforms(image,
            json("{'type':'input_image','image_url':'https://example.com/a.png','extra':[1,{'a':null}]}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png','openai/rs:extra':[1,{'a':null}]}}"));
    }

    @Test
    public void shouldNestInlineDocument()
    {
        assertTransforms(document,
            json("{'type':'input_file','filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}"),
            json("{'type':'file','file':{'filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}}"));
    }

    @Test
    public void shouldNestUrlDocument()
    {
        assertTransforms(document,
            json("{'type':'input_file','file_url':'https://example.com/a.pdf'}"),
            json("{'type':'file','file':{'file_url':'https://example.com/a.pdf'}}"));
    }

    @Test
    public void shouldNestReferenceDocument()
    {
        assertTransforms(document,
            json("{'type':'input_file','file_id':'file_1'}"),
            json("{'type':'file','file':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldPrefixDocumentDetail()
    {
        assertTransforms(document,
            json("{'type':'input_file','file_data':'data:application/pdf;base64,QUJD','detail':'high'}"),
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD','openai/rs:detail':'high'}}"));
    }

    @Test
    public void shouldRejectMediaThatIsNotAnObject()
    {
        assertRejects(image, json("['input_image']"));
    }

    @Test
    public void shouldRejectNonStringUrl()
    {
        assertTransforms(image,
            json("{'type':'input_image','image_url':null}"),
            json("{'type':'image_url','image_url':{}}"));
    }
}
