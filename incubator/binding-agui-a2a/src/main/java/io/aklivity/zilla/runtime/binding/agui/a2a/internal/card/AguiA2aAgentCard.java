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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.card;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AguiA2aAgentCard
{
    private static final String MEDIA_TYPE_JSON = "application/json";

    private final boolean streaming;
    private final List<String> inputModes;
    private final List<AguiA2aExtension> extensions;

    AguiA2aAgentCard(
        boolean streaming,
        List<String> inputModes,
        List<AguiA2aExtension> extensions)
    {
        this.streaming = streaming;
        this.inputModes = inputModes;
        this.extensions = extensions;
    }

    public AguiA2aExtension extension()
    {
        AguiA2aExtension negotiated = null;

        for (int i = 0; negotiated == null && i < AguiA2aExtension.IMPLEMENTED.size(); i++)
        {
            final String uri = AguiA2aExtension.IMPLEMENTED.get(i);

            for (AguiA2aExtension declared : extensions)
            {
                if (negotiated == null && uri.equals(declared.uri()))
                {
                    final Set<String> features = new HashSet<>(declared.features());
                    features.retainAll(AguiA2aExtension.FEATURES);
                    negotiated = new AguiA2aExtension(uri, declared.required(), features, declared.history());
                }
            }
        }

        return negotiated;
    }

    public boolean refused()
    {
        boolean refused = false;

        if (extension() == null)
        {
            for (AguiA2aExtension declared : extensions)
            {
                refused |= declared.required() && declared.uri().startsWith(AguiA2aExtension.URI_PREFIX);
            }
        }

        return refused;
    }

    public AguiA2aOperation operation()
    {
        return streaming ? AguiA2aOperation.MESSAGE_STREAM : AguiA2aOperation.MESSAGE_SEND;
    }

    public boolean acceptsInput(
        String mediaType)
    {
        final String requested = normalize(mediaType);
        boolean accepted = false;

        if (requested != null)
        {
            for (int i = 0; !accepted && i < inputModes.size(); i++)
            {
                accepted = matches(inputModes.get(i), requested);
            }
        }

        return accepted;
    }

    public boolean acceptsAdvisory()
    {
        return acceptsInput(MEDIA_TYPE_JSON);
    }

    private static boolean matches(
        String mode,
        String requested)
    {
        final int slash = mode.indexOf('/');
        final boolean wildcardSubtype = slash > 0 && mode.indexOf('*', slash) == slash + 1 && mode.length() == slash + 2;

        return mode.equals(requested) ||
            "*/*".equals(mode) ||
            wildcardSubtype && requested.regionMatches(0, mode, 0, slash + 1);
    }

    static String normalize(
        String mediaType)
    {
        String normalized = null;

        if (mediaType != null)
        {
            final int parameters = mediaType.indexOf(';');
            final String essence = parameters != -1 ? mediaType.substring(0, parameters) : mediaType;
            normalized = essence.trim().toLowerCase();
        }

        return normalized;
    }
}
