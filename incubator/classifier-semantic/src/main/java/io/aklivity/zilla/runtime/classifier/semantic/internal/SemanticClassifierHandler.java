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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

final class SemanticClassifierHandler implements ClassifierHandler
{
    private final EmbeddingHandler embedding;
    private final SemanticReferences references;
    private final double threshold;
    private final Map<String, int[]> exemplarsByLabel;

    SemanticClassifierHandler(
        EngineContext context,
        ClassifierConfig config,
        SemanticClassifierConfiguration configuration)
    {
        SemanticClassifierOptionsConfig options = (SemanticClassifierOptionsConfig) config.options;

        List<String> phrases = new ArrayList<>();
        Map<String, int[]> exemplarsByLabel = new LinkedHashMap<>();
        options.labels.forEach((label, exemplars) ->
        {
            int[] indexes = new int[exemplars.size()];
            for (int i = 0; i < indexes.length; i++)
            {
                indexes[i] = phrases.size();
                phrases.add(exemplars.get(i));
            }
            exemplarsByLabel.put(label, indexes);
        });

        this.embedding = requireNonNull(context.supplyEmbedding(config.embeddingId), "embedding");
        this.threshold = options.threshold;
        this.exemplarsByLabel = exemplarsByLabel;
        this.references = new SemanticReferences(
            embedding,
            requireNonNull(context.supplyStore(config.storeId), "store"),
            context.signaler(),
            phrases,
            configuration.lockTtl(),
            configuration.timeout());
        this.references.start();
    }

    @Override
    public Detector initDetector(
        List<String> labels)
    {
        int count = 0;
        for (String label : labels)
        {
            count += exemplarsFor(label).length;
        }

        int[] exemplars = new int[count];
        int offset = 0;
        for (String label : labels)
        {
            int[] indexes = exemplarsFor(label);
            System.arraycopy(indexes, 0, exemplars, offset, indexes.length);
            offset += indexes.length;
        }

        return new SemanticDetector(embedding, references, threshold, exemplars);
    }

    void close()
    {
        references.close();
    }

    private int[] exemplarsFor(
        String label)
    {
        int[] indexes = exemplarsByLabel.get(label);

        if (indexes == null)
        {
            throw new IllegalArgumentException("Unrecognized label: " + label);
        }

        return indexes;
    }
}
