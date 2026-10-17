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

import java.util.function.Consumer;

import org.junit.Test;

public class LlmAnthropicMediaToCanonicalTest
{
    private static final String DATA = "QUJD".repeat(1250);

    private final LlmAnthropicMediaToCanonical image = LlmAnthropicMediaToCanonical.image();
    private final LlmAnthropicMediaToCanonical document = LlmAnthropicMediaToCanonical.document();

    private final Consumer<LlmSectionAttributes> png = attributes -> attributes.mediaType("image/png");
    private final Consumer<LlmSectionAttributes> pdf = attributes -> attributes.mediaType("application/pdf");

    @Test
    public void shouldConvertInlineImageToDataUri()
    {
        assertTransforms(image, png, null,
            json("{'type':'image','source':{'type':'base64','media_type':'image/png','data':'QUJD'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"));
    }

    @Test
    public void shouldConvertInlineImageLargerThanWindow()
    {
        assertTransforms(image, png, null,
            json("{'type':'image','source':{'type':'base64','media_type':'image/png','data':'" + DATA + "'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"));
    }

    @Test
    public void shouldConvertUrlImage()
    {
        assertTransforms(image, null, null,
            json("{'type':'image','source':{'type':'url','url':'https://example.com/a.png'}}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldConvertReferenceImage()
    {
        assertTransforms(image, null, null,
            json("{'type':'image','source':{'type':'file','file_id':'file_1'}}"),
            json("{'type':'image_url','image_url':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldPrefixNativeOnlyMembersAtTheirPeers()
    {
        assertTransforms(image, null, null,
            json("{'cache_control':{'type':'ephemeral'},'type':'image'," +
                "'source':{'type':'url','url':'https://example.com/a.png'},'extra':[1,{'a':null}]}"),
            json("{'type':'image_url','anthropic:cache_control':{'type':'ephemeral'}," +
                "'image_url':{'url':'https://example.com/a.png'},'anthropic:extra':[1,{'a':null}]}"));
    }

    @Test
    public void shouldConvertInlineDocumentToDataUri()
    {
        assertTransforms(document, pdf, null,
            json("{'type':'document','source':{'type':'base64','media_type':'application/pdf','data':'QUJD'}}"),
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD'}}"));
    }

    @Test
    public void shouldConvertUrlDocument()
    {
        assertTransforms(document, null, null,
            json("{'type':'document','source':{'type':'url','url':'https://example.com/a.pdf'}}"),
            json("{'type':'file','file':{'file_url':'https://example.com/a.pdf'}}"));
    }

    @Test
    public void shouldConvertReferenceDocument()
    {
        assertTransforms(document, null, null,
            json("{'type':'document','source':{'type':'file','file_id':'file_1'}}"),
            json("{'type':'file','file':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldPrefixTitleAndContext()
    {
        assertTransforms(document, null, null,
            json("{'type':'document','title':'T','context':'C','source':{'type':'file','file_id':'file_1'}}"),
            json("{'type':'file','anthropic:title':'T','anthropic:context':'C','file':{'file_id':'file_1'}}"));
    }

    @Test
    public void shouldRejectInlineWithoutMediaType()
    {
        assertRejects(image, json("{'type':'image','source':{'type':'base64','media_type':'image/png','data':'QUJD'}}"));
    }

    @Test
    public void shouldRejectMediaThatIsNotAnObject()
    {
        assertRejects(image, json("['image']"));
    }
}
