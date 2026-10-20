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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmGeminiMediaCanonicalTest
{
    private static final String DATA = "QUJD".repeat(1250);

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
    private final Consumer<LlmSectionAttributes> inlineWav = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_INLINE);
        attributes.mediaType("audio/wav");
    };
    private final Consumer<LlmSectionAttributes> inlineMpeg = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_INLINE);
        attributes.mediaType("audio/mpeg");
    };
    private final Consumer<LlmSectionAttributes> urlMp4 = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_URL);
        attributes.mediaType("video/mp4");
    };
    private final Consumer<LlmSectionAttributes> url = attributes -> attributes.source(LlmSectionAttributes.SOURCE_URL);
    private final Consumer<LlmSectionAttributes> reference = attributes ->
    {
        attributes.source(LlmSectionAttributes.SOURCE_REFERENCE);
        attributes.mediaType("image/png");
    };

    @Test
    public void shouldCanonicalizeInlineImageAsDataUri()
    {
        assertTransforms(LlmGeminiMediaToCanonical.image(), inlinePng, null,
            json("{'inlineData':{'mimeType':'image/png','data':'QUJD'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"));
    }

    @Test
    public void shouldCanonicalizeInlineImageWithDataFirst()
    {
        assertTransforms(LlmGeminiMediaToCanonical.image(), inlinePng, null,
            json("{'inline_data':{'data':'QUJD','mime_type':'image/png'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"));
    }

    @Test
    public void shouldCanonicalizeInlineImageLargerThanWindow()
    {
        assertTransforms(LlmGeminiMediaToCanonical.image(), inlinePng, null,
            json("{'inlineData':{'mimeType':'image/png','data':'" + DATA + "'}}"),
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"));
    }

    @Test
    public void shouldCanonicalizeUrlImage()
    {
        assertTransforms(LlmGeminiMediaToCanonical.image(), url, null,
            json("{'fileData':{'fileUri':'https://example.com/a.png'}}"),
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldCanonicalizeReferenceImage()
    {
        assertTransforms(LlmGeminiMediaToCanonical.image(), reference, null,
            json("{'fileData':{'mimeType':'image/png','file_uri':'gs://bucket/a.png'}}"),
            json("{'type':'image_url','image_url':{'file_id':'gs://bucket/a.png'}}"));
    }

    @Test
    public void shouldCanonicalizeInlineDocument()
    {
        assertTransforms(LlmGeminiMediaToCanonical.document(), inlinePdf, null,
            json("{'inlineData':{'mimeType':'application/pdf','data':'QUJD'}}"),
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD'}}"));
    }

    @Test
    public void shouldCanonicalizeUrlDocument()
    {
        assertTransforms(LlmGeminiMediaToCanonical.document(), url, null,
            json("{'fileData':{'fileUri':'https://example.com/a.pdf'}}"),
            json("{'type':'file','file':{'file_url':'https://example.com/a.pdf'}}"));
    }

    @Test
    public void shouldCanonicalizeInlineAudioWithFormat()
    {
        assertTransforms(LlmGeminiMediaToCanonical.audio(), inlineWav, null,
            json("{'inlineData':{'mimeType':'audio/wav','data':'QUJD'}}"),
            json("{'type':'input_audio','input_audio':{'data':'QUJD','format':'wav'}}"));
    }

    @Test
    public void shouldCanonicalizeMpegAudioAsMp3Format()
    {
        assertTransforms(LlmGeminiMediaToCanonical.audio(), inlineMpeg, null,
            json("{'inlineData':{'mimeType':'audio/mpeg','data':'QUJD'}}"),
            json("{'type':'input_audio','input_audio':{'data':'QUJD','format':'mp3'}}"));
    }

    @Test
    public void shouldCanonicalizeUrlVideoWithFormat()
    {
        assertTransforms(LlmGeminiMediaToCanonical.video(), urlMp4, null,
            json("{'fileData':{'mimeType':'video/mp4','fileUri':'https://example.com/a.mp4'}}"),
            json("{'type':'input_video','input_video':{'url':'https://example.com/a.mp4','format':'mp4'}}"));
    }

    @Test
    public void shouldCarryNativeMembersWithPrefix()
    {
        assertTransforms(LlmGeminiMediaToCanonical.video(), urlMp4, null,
            json("{'fileData':{'mimeType':'video/mp4','fileUri':'https://example.com/a.mp4','displayName':'clip'}," +
                "'videoMetadata':{'startOffset':'1s'}}"),
            json("{'type':'input_video','input_video':{'url':'https://example.com/a.mp4','gemini:displayName':'clip'," +
                "'format':'mp4'},'gemini:videoMetadata':{'startOffset':'1s'}}"));
    }

    @Test
    public void shouldRejectInlineImageWithoutMediaType()
    {
        final Consumer<LlmSectionAttributes> inline = attributes -> attributes.source(LlmSectionAttributes.SOURCE_INLINE);

        assertRejects(LlmGeminiMediaToCanonical.image(), inline, null, json("{'inlineData':{'data':'QUJD'}}"));
    }

    @Test
    public void shouldWriteInlineImageFromDataUri()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.image(true), inlinePng, null,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64,QUJD'}}"),
            json("{'inlineData':{'mimeType':'image/png','data':'QUJD'}}"));
    }

    @Test
    public void shouldWriteInlineImageLargerThanWindow()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.image(true), inlinePng, null,
            json("{'type':'image_url','image_url':{'url':'data:image/png;base64," + DATA + "'}}"),
            json("{'inlineData':{'mimeType':'image/png','data':'" + DATA + "'}}"));
    }

    @Test
    public void shouldWriteUrlImageWithoutMediaType()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.image(true), url, null,
            json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"),
            json("{'fileData':{'fileUri':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldWriteReferenceImageAsFileUri()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.image(true), reference, null,
            json("{'type':'image_url','image_url':{'file_id':'files/abc'}}"),
            json("{'fileData':{'mimeType':'image/png','fileUri':'files/abc'}}"));
    }

    @Test
    public void shouldWriteInlineDocument()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.document(true), inlinePdf, null,
            json("{'type':'file','file':{'file_data':'data:application/pdf;base64,QUJD'}}"),
            json("{'inlineData':{'mimeType':'application/pdf','data':'QUJD'}}"));
    }

    @Test
    public void shouldWriteInlineAudioWithoutFormat()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.audio(true), inlineWav, null,
            json("{'type':'input_audio','input_audio':{'data':'QUJD','format':'wav'}}"),
            json("{'inlineData':{'mimeType':'audio/wav','data':'QUJD'}}"));
    }

    @Test
    public void shouldWriteUrlVideo()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.video(true), urlMp4, null,
            json("{'type':'input_video','input_video':{'url':'https://example.com/a.mp4','format':'mp4'}}"),
            json("{'fileData':{'mimeType':'video/mp4','fileUri':'https://example.com/a.mp4'}}"));
    }

    @Test
    public void shouldRestoreOwnPrefixAndDropOtherMembers()
    {
        assertTransforms(LlmGeminiMediaFromCanonical.image(true), inlinePng, null,
            json("{'type':'image_url','gemini:videoMetadata':{'startOffset':'1s'},'openai:x':1," +
                "'image_url':{'url':'data:image/png;base64,QUJD','detail':'high','gemini:displayName':'cat','anthropic:y':2}}"),
            json("{'videoMetadata':{'startOffset':'1s'},'inlineData':{'mimeType':'image/png','data':'QUJD'," +
                "'displayName':'cat'}}"));
    }

    @Test
    public void shouldRejectMediaWithoutSource()
    {
        assertRejects(LlmGeminiMediaFromCanonical.image(true), attributes ->
        {
        }, null, json("{'type':'image_url','image_url':{'url':'https://example.com/a.png'}}"));
    }

    @Test
    public void shouldDeriveFormatFromMediaType()
    {
        assertThat(LlmGeminiMediaFormat.formatOf("audio/wav"), equalTo("wav"));
        assertThat(LlmGeminiMediaFormat.formatOf("audio/mpeg"), equalTo("mp3"));
        assertThat(LlmGeminiMediaFormat.formatOf("video/mp4; codecs=avc1"), equalTo("mp4"));
        assertThat(LlmGeminiMediaFormat.formatOf("invalid"), nullValue());
        assertThat(LlmGeminiMediaFormat.formatOf(null), nullValue());
    }
}
