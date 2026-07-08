/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchDocument;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchIndex;
import io.aklivity.zilla.runtime.binding.mcp.search.McpToolSearchMatch;

/**
 * BM25 ranking over the configured fields, with each field's weighted term frequency summed
 * into a single per-document score before applying the standard BM25 formula. Field weights
 * are a simple multiplier, not full BM25F term-independent length normalization.
 */
final class McpKeywordToolSearchIndex implements McpToolSearchIndex
{
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final List<String> fields;
    private final Map<String, Double> weights;

    private final Map<String, Map<String, Double>> termFrequenciesByDocument = new LinkedHashMap<>();
    private final Map<String, Double> documentLengths = new LinkedHashMap<>();
    private final Map<String, Integer> documentFrequencies = new HashMap<>();
    private double averageDocumentLength;

    McpKeywordToolSearchIndex(
        List<String> fields,
        Map<String, Double> weights)
    {
        this.fields = fields != null ? fields : List.of();
        this.weights = weights != null ? weights : Map.of();
    }

    @Override
    public void index(
        Collection<McpToolSearchDocument> documents)
    {
        termFrequenciesByDocument.clear();
        documentLengths.clear();
        documentFrequencies.clear();

        double totalLength = 0.0;

        for (McpToolSearchDocument document : documents)
        {
            Map<String, Double> termFrequencies = new HashMap<>();
            double length = 0.0;

            for (String field : fields)
            {
                String text = document.field(field);
                if (text == null)
                {
                    continue;
                }

                double weight = weights.getOrDefault(field, 1.0);
                List<String> tokens = McpTextTokenizer.tokenize(text);
                length += tokens.size() * weight;

                for (String token : tokens)
                {
                    termFrequencies.merge(token, weight, Double::sum);
                }
            }

            termFrequenciesByDocument.put(document.name, termFrequencies);
            documentLengths.put(document.name, length);
            totalLength += length;

            for (String term : termFrequencies.keySet())
            {
                documentFrequencies.merge(term, 1, Integer::sum);
            }
        }

        int documentCount = documentLengths.size();
        averageDocumentLength = documentCount == 0 ? 0.0 : totalLength / documentCount;
    }

    @Override
    public List<McpToolSearchMatch> query(
        String text)
    {
        List<String> queryTerms = McpTextTokenizer.tokenize(text);
        List<McpToolSearchMatch> matches = new ArrayList<>();

        if (!queryTerms.isEmpty() && !documentLengths.isEmpty())
        {
            int documentCount = documentLengths.size();

            for (Map.Entry<String, Map<String, Double>> entry : termFrequenciesByDocument.entrySet())
            {
                String name = entry.getKey();
                Map<String, Double> termFrequencies = entry.getValue();
                double length = documentLengths.get(name);
                double score = 0.0;

                for (String term : queryTerms)
                {
                    Double frequency = termFrequencies.get(term);
                    if (frequency == null)
                    {
                        continue;
                    }

                    int documentFrequency = documentFrequencies.getOrDefault(term, 0);
                    double idf = Math.log(1.0 + (documentCount - documentFrequency + 0.5) / (documentFrequency + 0.5));
                    double normalization = 1.0 - B + B * (length / averageDocumentLength);
                    score += idf * (frequency * (K1 + 1.0)) / (frequency + K1 * normalization);
                }

                if (score > 0.0)
                {
                    matches.add(new McpToolSearchMatch(name, score));
                }
            }

            matches.sort((a, b) -> Double.compare(b.score, a.score));
        }

        return matches;
    }
}
