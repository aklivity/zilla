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
package io.aklivity.zilla.runtime.binding.mcp.internal.codec;

import jakarta.json.JsonObject;

public class McpClientCapabilities
{
    public JsonObject experimental;
    public Roots roots;
    public Sampling sampling;
    public Elicitation elicitation;
    public Tasks tasks;

    public class Roots
    {
        public boolean listChanged;
    }

    public class Sampling
    {
        public Context context;
        public Tools tools;

        public record Context()
        {
        }

        public record Tools()
        {
        }
    }

    public class Elicitation
    {
        public Form form;
        public Url url;

        public record Form()
        {
        }

        public record Url()
        {
        }
    }

    public class Tasks
    {
        public List list;
        public Cancel cancel;
        public Requests requests;

        public record List()
        {
        }

        public record Cancel()
        {
        }

        public class Requests
        {
            public Sampling sampling;
            public Elicitation elicitation;

            public class Sampling
            {
                public CreateMessage createMessage;

                public record CreateMessage()
                {
                }
            }

            public class Elicitation
            {
                public Create create;

                public record Create()
                {
                }
            }
        }
    }
}
