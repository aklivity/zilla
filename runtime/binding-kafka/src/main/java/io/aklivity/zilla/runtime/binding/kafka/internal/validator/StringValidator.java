/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.ValidatorConfig;

public class StringValidator implements Validator
{
    private Charset encoding;
    private CharsetDecoder decoder;

    public StringValidator(
        ValidatorConfig config)
    {
        this.encoding = config.encoding();
        this.decoder = encoding.newDecoder();
    }

    @Override
    public boolean validate(
        OctetsFW payload)
    {
        boolean valid = false;
        if (payload != null)
        {
            byte[] payloadBytes = new byte[payload.sizeof()];
            payload.value().getBytes(0, payloadBytes);
            try
            {
                decoder.decode(ByteBuffer.wrap(payloadBytes));
                valid = true;
            }
            catch (CharacterCodingException ex)
            {
            }
        }
        return valid;
    }
}
