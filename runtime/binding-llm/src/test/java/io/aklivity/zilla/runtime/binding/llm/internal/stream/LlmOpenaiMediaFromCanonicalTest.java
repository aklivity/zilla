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

public class LlmOpenaiMediaFromCanonicalTest
{
    private final LlmOpenaiMediaFromCanonical image = LlmOpenaiMediaFromCanonical.image(true);
    private final LlmOpenaiMediaFromCanonical document = LlmOpenaiMediaFromCanonical.document(true);
    private final LlmOpenaiMediaFromCanonical audio = LlmOpenaiMediaFromCanonical.audio(true);
    private final LlmOpenaiMediaFromCanonical foreignImage = LlmOpenaiMediaFromCanonical.image(false);

    @Test
    public void shouldPassCanonicalImage()
    {
        assertTransforms(image,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD','detail':'high'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD','detail':'high'}}"));
    }

    @Test
    public void shouldPassCanonicalDocument()
    {
        assertTransforms(document,
            json("{'type':'file','file':{'filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}}"),
            json("{'type':'file','file':{'filename':'a.pdf','file_data':'data:application/pdf;base64,QUJD'}}"));
    }

    @Test
    public void shouldPassCanonicalAudio()
    {
        assertTransforms(audio,
            json("{'type':'input_audio','input_audio':{'data':'QUJD','format':'wav'}}"),
            json("{'type':'input_audio','input_audio':{'data':'QUJD','format':'wav'}}"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMembersAndStripOwn()
    {
        assertTransforms(image,
            json("{'anthropic:cache_control':{'type':'ephemeral'},'type':'image_url','openai:note':'x'," +
                "'image_url':{'anthropic:y':1,'url':'https://example.com/a.png','openai:z':true}}"),
            json("{'type':'image_url','note':'x','image_url':{'url':'https://example.com/a.png','z':true}}"));
    }

    @Test
    public void shouldRejectMediaThatIsNotAnObject()
    {
        assertRejects(image, json("['image']"));
    }

    @Test
    public void shouldDropOwnPrefixedMembersAcrossDialects()
    {
        assertTransforms(foreignImage,
            json("{'type':'image_url','openai:note':'x','image_url':{'url':'https://example.com/a.png','openai:z':true}}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldPassUnprefixedMembersAcrossDialects()
    {
        assertTransforms(foreignImage,
            json("{'type':'image_url','kept':1,'image_url':{'url':'https://example.com/a.png','detail':'high'}}"),
            json("{'type':'image_url','kept':1,'image_url':{'url':'https://example.com/a.png','detail':'high'}}"));
    }
}
