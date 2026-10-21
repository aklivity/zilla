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

public class LlmAnthropicMediaFromCanonicalTest
{
    private static final String DATA = "QUJD".repeat(1250);

    private final LlmAnthropicMediaFromCanonical image = LlmAnthropicMediaFromCanonical.image(true);
    private final LlmAnthropicMediaFromCanonical document = LlmAnthropicMediaFromCanonical.document(true);
    private final LlmAnthropicMediaFromCanonical foreignImage = LlmAnthropicMediaFromCanonical.image(false);

    private final Consumer<LlmSectionAttributes> inlinePng = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_INLINE);
        attributes.mediaType("image/png");
    };
    private final Consumer<LlmSectionAttributes> inlinePdf = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_INLINE);
        attributes.mediaType("application/pdf");
    };
    private final Consumer<LlmSectionAttributes> url = attributes -> attributes.source(LlmSectionAttributes.SOURCE_URL);
    private final Consumer<LlmSectionAttributes> reference = attributes ->
        attributes.source(LlmSectionAttributes.SOURCE_REFERENCE);

    @Test
    public void shouldConvertDataUriImageToBase64Source()
    {
        assertTransforms(image, inlinePng, null,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"),
            json("{'type':'image','source':{'type':'base64','media_type':'image/png','data':'QUJD'}}"));
    }

    @Test
    public void shouldConvertDataUriImageLargerThanWindow()
    {
        assertTransforms(image, inlinePng, null,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"),
            json("{'type':'image','source':{'type':'base64','media_type':'image/png','data':'" + DATA + "'}}"));
    }

    @Test
    public void shouldConvertUrlImage()
    {
        assertTransforms(image, url, null,
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"),
            json("{'type':'image','source':{'type':'url','url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldConvertReferenceImage()
    {
        assertTransforms(image, reference, null,
            json("{'type':'image_url','image_url':{'file_id':'file_1'}}"),
            json("{'type':'image','source':{'type':'file','file_id':'file_1'}}"));
    }

    @Test
    public void shouldStripOwnPrefixAndDropOtherDialectMembers()
    {
        assertTransforms(image, url, null,
            json("{'type':'image_url','anthropic:cache_control':{'type':'ephemeral'},'openai:x':1," +
                "'image_url':{'url':'https://example.com/a.png'},'gemini:y':[1]}"),
            json("{'type':'image','cache_control':{'type':'ephemeral'}," +
                "'source':{'type':'url','url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldConvertDataUriDocumentToBase64Source()
    {
        assertTransforms(document, inlinePdf, null,
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD'}}"),
            json("{'type':'document','source':{'type':'base64','media_type':'application/pdf','data':'QUJD'}}"));
    }

    @Test
    public void shouldConvertUrlDocument()
    {
        assertTransforms(document, url, null,
            json("{'type':'file','file':{'file_url':'https://example.com/a.pdf'}}"),
            json("{'type':'document','source':{'type':'url','url':'https://example.com/a.pdf'}}"));
    }

    @Test
    public void shouldConvertReferenceDocumentAndDropFilename()
    {
        assertTransforms(document, reference, null,
            json("{'type':'file','file':{'filename':'a.pdf','file_id':'file_1'}}"),
            json("{'type':'document','source':{'type':'file','file_id':'file_1'}}"));
    }

    @Test
    public void shouldStripTitleAndContextPrefix()
    {
        assertTransforms(document, reference, null,
            json("{'type':'file','anthropic:title':'T','anthropic:context':'C','file':{'file_id':'file_1'}}"),
            json("{'type':'document','title':'T','context':'C','source':{'type':'file','file_id':'file_1'}}"));
    }

    @Test
    public void shouldRejectDataUriThatDoesNotMatchMediaType()
    {
        assertRejects(image, inlinePng, null,
            json("{'type':'image_url','image_url':{'url':'data:image/jpeg;base64,QUJD'}}"));
    }

    @Test
    public void shouldRejectInlineWithoutMediaType()
    {
        assertRejects(image, attributes -> attributes.source(LlmSectionAttributes.SOURCE_INLINE), null,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"));
    }

    @Test
    public void shouldRejectMediaThatIsNotAnObject()
    {
        assertRejects(image, inlinePng, null, json("['image']"));
    }

    @Test
    public void shouldDropOwnPrefixedMembersAcrossDialects()
    {
        assertTransforms(foreignImage, url, null,
            json("{'type':'image_url','anthropic:cache_control':{'type':'ephemeral'}," +
                "'image_url':{'url':'https://example.com/a.png'}}"),
            json("{'type':'image','source':{'type':'url','url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldPassUnprefixedMembersAcrossDialects()
    {
        assertTransforms(foreignImage, url, null,
            json("{'type':'image_url','kept':1,'image_url':{'url':'https://example.com/a.png'}}"),
            json("{'type':'image','kept':1,'source':{'type':'url','url':'https://example.com/a.png'}}"));
    }
}
