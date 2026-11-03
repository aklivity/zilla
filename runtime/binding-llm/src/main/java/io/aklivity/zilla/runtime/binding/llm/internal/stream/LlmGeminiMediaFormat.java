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

final class LlmGeminiMediaFormat
{
    private static final String MEDIA_TYPE_MPEG_AUDIO = "audio/mpeg";
    private static final String FORMAT_MP3 = "mp3";

    static String formatOf(
        String mediaType)
    {
        final int slash = mediaType != null ? mediaType.indexOf('/') : -1;
        String format = null;

        if (slash != -1)
        {
            final int semicolon = mediaType.indexOf(';', slash);

            format = MEDIA_TYPE_MPEG_AUDIO.equals(mediaType) ? FORMAT_MP3
                : mediaType.substring(slash + 1, semicolon != -1 ? semicolon : mediaType.length());
        }

        return format;
    }

    private LlmGeminiMediaFormat()
    {
    }
}
