/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.amqp.internal.stream;

import static io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration.AMQP_INCOMING_LOCALES_DEFAULT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpConnectionState.DISCARDING;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpConnectionState.ERROR;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpConnectionState.START;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.END_RCVD;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.MAPPED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpSessionState.UNMAPPED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpTransferFlags.aborted;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpTransferFlags.batchable;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpTransferFlags.isSettled;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpTransferFlags.resume;
import static io.aklivity.zilla.runtime.binding.amqp.internal.stream.AmqpTransferFlags.settled;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpAnnotationKeyFW.KIND_ID;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpAnnotationKeyFW.KIND_NAME;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpCapabilities.RECEIVE_ONLY;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpCapabilities.SEND_ONLY;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpBeginFW.DEFAULT_VALUE_HANDLE_MAX;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.APPLICATION_PROPERTIES;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.DATA;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.MESSAGE_ANNOTATIONS;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.PROPERTIES;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.SASL_INIT;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.SEQUENCE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType.VALUE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.CONNECTION_FRAMING_ERROR;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.DECODE_ERROR;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.ILLEGAL_STATE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.INVALID_FIELD;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.LINK_DETACH_FORCED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.LINK_MESSAGE_SIZE_EXCEEDED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.LINK_TRANSFER_LIMIT_EXCEEDED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.NOT_ALLOWED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.PRECONDITION_FAILED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.RESOURCE_LIMIT_EXCEEDED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.SESSION_ERRANT_LINK;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.SESSION_HANDLE_IN_USE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.SESSION_UNATTACHED_HANDLE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType.SESSION_WINDOW_VIOLATION;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpOpenFW.DEFAULT_VALUE_MAX_FRAME_SIZE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.ATTACH;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.BEGIN;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.CLOSE;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.DETACH;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.DISPOSITION;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.END;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.FLOW;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.OPEN;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType.TRANSFER;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpReceiverSettleMode.FIRST;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpRole.RECEIVER;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpRole.SENDER;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSaslCode.OK;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSenderSettleMode.MIXED;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpType.BINARY1;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpType.BINARY4;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpType.STRING1;
import static io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpType.STRING4;
import static io.aklivity.zilla.runtime.binding.amqp.internal.util.AmqpTypeUtil.amqpCapabilities;
import static io.aklivity.zilla.runtime.binding.amqp.internal.util.AmqpTypeUtil.amqpReceiverSettleMode;
import static io.aklivity.zilla.runtime.binding.amqp.internal.util.AmqpTypeUtil.amqpSenderSettleMode;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.budget.BudgetDebitor.NO_DEBITOR_INDEX;
import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static java.lang.System.currentTimeMillis;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.amqp.internal.AmqpBinding;
import io.aklivity.zilla.runtime.binding.amqp.internal.AmqpConfiguration;
import io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpBindingConfig;
import io.aklivity.zilla.runtime.binding.amqp.internal.config.AmqpRouteConfig;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpAnnotationFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpAnnotationKeyFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpApplicationPropertyFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpBodyKind;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpCapabilities;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.AmqpPropertiesFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.Array8FW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.BoundedOctetsFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.StringFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpApplicationPropertiesFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpAttachFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpBeginFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpBinaryFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpCloseFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedType;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDescribedTypeFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpDetachFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpEndFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorListFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpErrorType;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpFlowFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpFrameHeaderFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpHeaderFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpIETFLanguageTagFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpMapFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpMessagePropertiesFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpOpenFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeType;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpPerformativeTypeFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpProtocolHeaderFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpReceiverSettleMode;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpRole;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSaslFrameHeaderFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSaslInitFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSaslMechanismsFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSaslOutcomeFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSectionType;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSectionTypeFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSecurityFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSenderSettleMode;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSimpleTypeFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSourceFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSourceListFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpStringFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpSymbolFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpTargetFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpTargetListFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpTransferFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpType;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpULongFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpValueFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpVariableLength32FW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.codec.AmqpVariableLength8FW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.AmqpBeginExFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.AmqpDataExFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.binding.amqp.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class AmqpServerFactory implements AmqpStreamFactory
{
    private static final StringFW[] EMPTY_STRINGFW_ARRAY = new StringFW[0];
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(new UnsafeBuffer(), 0, 0);

    private static final StringFW[] DEFAULT_INCOMING_LOCALES = asStringFWArray(AMQP_INCOMING_LOCALES_DEFAULT);

    private static final int FLAG_FIN = 1;
    private static final int FLAG_INIT = 2;
    private static final int FLAG_INCOMPLETE = 4;
    private static final int FLAG_INIT_INCOMPLETE = FLAG_INIT | FLAG_INCOMPLETE;
    private static final int FLAG_INIT_AND_FIN = FLAG_INIT | FLAG_FIN;
    private static final int FRAME_HEADER_SIZE = 8;
    private static final int SASL_DESCRIPTOR_SIZE = 3;
    private static final int MIN_MAX_FRAME_SIZE = 512;
    private static final int TRANSFER_HEADER_SIZE = 20;
    private static final int PAYLOAD_HEADER_SIZE = 205;
    private static final int NO_DELIVERY_ID = -1;
    private static final int PLAIN_PROTOCOL_ID = 0;
    private static final int SASL_PROTOCOL_ID = 3;
    private static final long PROTOCOL_HEADER = 0x414D5150_00010000L;
    private static final long DEFAULT_IDLE_TIMEOUT = 0;
    private static final int READ_IDLE_SIGNAL_ID = 0;
    private static final int WRITE_IDLE_SIGNAL_ID = 1;
    private static final int CLOSE_SIGNAL_ID = 2;
    private static final int MIN_IDLE_TIMEOUT = 100;
    private static final long PROTOCOL_HEADER_SASL = 0x414D5150_03010000L;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final WindowFW windowRO = new WindowFW();
    private final ResetFW resetRO = new ResetFW();
    private final SignalFW signalRO = new SignalFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();

    private final AmqpBeginExFW amqpBeginExRO = new AmqpBeginExFW();
    private final AmqpDataExFW amqpDataExRO = new AmqpDataExFW();

    private final AmqpBeginExFW.Builder amqpBeginExRW = new AmqpBeginExFW.Builder();
    private final AmqpDataExFW.Builder amqpDataExRW = new AmqpDataExFW.Builder();

    private final OctetsFW.Builder messageFragmentRW = new OctetsFW.Builder();

    private final OctetsFW payloadRO = new OctetsFW();

    private final AmqpProtocolHeaderFW amqpProtocolHeaderRO = new AmqpProtocolHeaderFW();
    private final AmqpFrameHeaderFW amqpFrameHeaderRO = new AmqpFrameHeaderFW();
    private final AmqpPerformativeTypeFW amqpPerformativeTypeRO = new AmqpPerformativeTypeFW();
    private final AmqpSaslFrameHeaderFW amqpSaslFrameHeaderRO = new AmqpSaslFrameHeaderFW();
    private final AmqpOpenFW amqpOpenRO = new AmqpOpenFW();
    private final AmqpBeginFW amqpBeginRO = new AmqpBeginFW();
    private final AmqpAttachFW amqpAttachRO = new AmqpAttachFW();
    private final AmqpFlowFW amqpFlowRO = new AmqpFlowFW();
    private final AmqpTransferFW amqpTransferRO = new AmqpTransferFW();
    private final AmqpDetachFW amqpDetachRO = new AmqpDetachFW();
    private final AmqpEndFW amqpEndRO = new AmqpEndFW();
    private final AmqpCloseFW amqpCloseRO = new AmqpCloseFW();
    private final AmqpSecurityFW amqpSecurityRO = new AmqpSecurityFW();
    private final AmqpHeaderFW headersRO = new AmqpHeaderFW();
    private final AmqpMapFW<AmqpValueFW, AmqpValueFW> deliveryAnnotationsRO =
        new AmqpMapFW<>(new AmqpValueFW(), new AmqpValueFW());
    private final AmqpMapFW<AmqpValueFW, AmqpValueFW> annotationsRO = new AmqpMapFW<>(new AmqpValueFW(), new AmqpValueFW());
    private final OctetsFW deliveryTagRO = new OctetsFW();
    private final AmqpMessagePropertiesFW amqpPropertiesRO = new AmqpMessagePropertiesFW();
    private final AmqpMapFW<AmqpValueFW, AmqpValueFW> applicationPropertyRO =
        new AmqpMapFW<>(new AmqpValueFW(), new AmqpValueFW());
    private final AmqpApplicationPropertiesFW<AmqpSimpleTypeFW> applicationPropertiesRO =
        new AmqpApplicationPropertiesFW<>(new AmqpStringFW(), new AmqpSimpleTypeFW());
    private final AmqpMapFW<AmqpValueFW, AmqpValueFW> footerRO = new AmqpMapFW<>(new AmqpValueFW(), new AmqpValueFW());
    private final AmqpSectionTypeFW amqpSectionTypeRO = new AmqpSectionTypeFW();
    private final AmqpValueFW amqpValueRO = new AmqpValueFW();
    private final AmqpSimpleTypeFW amqpSimpleTypeRO = new AmqpSimpleTypeFW();

    private final AmqpFrameHeaderFW.Builder amqpFrameHeaderRW = new AmqpFrameHeaderFW.Builder();
    private final AmqpSaslFrameHeaderFW.Builder amqpSaslFrameHeaderRW = new AmqpSaslFrameHeaderFW.Builder();
    private final AmqpOpenFW.Builder amqpOpenRW = new AmqpOpenFW.Builder();
    private final AmqpBeginFW.Builder amqpBeginRW = new AmqpBeginFW.Builder();
    private final AmqpAttachFW.Builder amqpAttachRW = new AmqpAttachFW.Builder();
    private final AmqpFlowFW.Builder amqpFlowRW = new AmqpFlowFW.Builder();
    private final AmqpTransferFW.Builder amqpTransferRW = new AmqpTransferFW.Builder();
    private final AmqpDetachFW.Builder amqpDetachRW = new AmqpDetachFW.Builder();
    private final AmqpEndFW.Builder amqpEndRW = new AmqpEndFW.Builder();
    private final AmqpCloseFW.Builder amqpCloseRW = new AmqpCloseFW.Builder();
    private final AmqpErrorListFW.Builder amqpErrorListRW = new AmqpErrorListFW.Builder();
    private final AmqpStringFW.Builder amqpStringRW = new AmqpStringFW.Builder();
    private final AmqpSimpleTypeFW.Builder amqpValueRW = new AmqpSimpleTypeFW.Builder();
    private final AmqpSymbolFW.Builder amqpSymbolRW = new AmqpSymbolFW.Builder();
    private final AmqpSourceListFW.Builder amqpSourceListRW = new AmqpSourceListFW.Builder();
    private final AmqpTargetListFW.Builder amqpTargetListRW = new AmqpTargetListFW.Builder();
    private final AmqpBinaryFW.Builder amqpBinaryRW = new AmqpBinaryFW.Builder();
    private final AmqpULongFW.Builder amqpULongRW = new AmqpULongFW.Builder();
    private final AmqpVariableLength8FW.Builder amqpVariableLength8RW = new AmqpVariableLength8FW.Builder();
    private final AmqpVariableLength32FW.Builder amqpVariableLength32RW = new AmqpVariableLength32FW.Builder();
    private final AmqpMessagePropertiesFW.Builder amqpPropertiesRW = new AmqpMessagePropertiesFW.Builder();
    private final Array32FW.Builder<AmqpAnnotationFW.Builder, AmqpAnnotationFW> annotationRW =
        new Array32FW.Builder<>(new AmqpAnnotationFW.Builder(), new AmqpAnnotationFW());
    private final AmqpPropertiesFW.Builder propertyRW = new AmqpPropertiesFW.Builder();
    private final Array32FW.Builder<AmqpApplicationPropertyFW.Builder, AmqpApplicationPropertyFW> applicationPropertyRW =
        new Array32FW.Builder<>(new AmqpApplicationPropertyFW.Builder(), new AmqpApplicationPropertyFW());
    private final AmqpSaslMechanismsFW.Builder amqpSaslMechanismsRW = new AmqpSaslMechanismsFW.Builder();
    private final AmqpSaslOutcomeFW.Builder amqpSaslOutcomeRW = new AmqpSaslOutcomeFW.Builder();
    private final Array8FW.Builder<AmqpSymbolFW.Builder, AmqpSymbolFW> anonymousRW =
        new Array8FW.Builder<>(new AmqpSymbolFW.Builder(), new AmqpSymbolFW());
    private final Array8FW.Builder<AmqpIETFLanguageTagFW.Builder, AmqpIETFLanguageTagFW> incomingLocalesRW =
        new Array8FW.Builder<>(new AmqpIETFLanguageTagFW.Builder(), new AmqpIETFLanguageTagFW());

    private final AmqpPerformativeTypeFW openType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(OPEN)
        .build();

    private final AmqpPerformativeTypeFW beginType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(BEGIN)
        .build();

    private final AmqpPerformativeTypeFW attachType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(ATTACH)
        .build();

    private final AmqpPerformativeTypeFW flowType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(FLOW)
        .build();

    private final AmqpPerformativeTypeFW transferType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(TRANSFER)
        .build();

    private final AmqpPerformativeTypeFW detachType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(DETACH)
        .build();

    private final AmqpPerformativeTypeFW endType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(END)
        .build();

    private final AmqpPerformativeTypeFW closeType = new AmqpPerformativeTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(CLOSE)
        .build();

    private final AmqpDescribedTypeFW applicationPropertiesSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(APPLICATION_PROPERTIES)
        .build();

    private final AmqpDescribedTypeFW messagePropertiesSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(PROPERTIES)
        .build();

    private final AmqpDescribedTypeFW messageAnnotationsSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(MESSAGE_ANNOTATIONS)
        .build();

    private final AmqpDescribedTypeFW dataSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(DATA)
        .build();

    private final AmqpDescribedTypeFW sequenceSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(SEQUENCE)
        .build();

    private final AmqpDescribedTypeFW valueSectionType = new AmqpDescribedTypeFW.Builder()
        .wrap(new UnsafeBuffer(new byte[3]), 0, 3)
        .set(VALUE)
        .build();

    private final AmqpProtocolHeaderFW plainProtocolHeader = new AmqpProtocolHeaderFW.Builder()
        .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
        .name(n -> n.set("AMQP".getBytes(StandardCharsets.US_ASCII)))
        .id(PLAIN_PROTOCOL_ID)
        .major(1)
        .minor(0)
        .revision(0)
        .build();

    private final AmqpProtocolHeaderFW saslProtocolHeader = new AmqpProtocolHeaderFW.Builder()
        .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
        .name(n -> n.set("AMQP".getBytes(StandardCharsets.US_ASCII)))
        .id(SASL_PROTOCOL_ID)
        .major(1)
        .minor(0)
        .revision(0)
        .build();

    private final OctetsFW nullConstructor = new OctetsFW()
        .wrap(new UnsafeBuffer(new byte[] {0x40}), 0, 1);

    private final OctetsFW emptyFrameHeader = new OctetsFW()
        .wrap(new UnsafeBuffer(new byte[] {0x00, 0x00, 0x00, 0x08, 0x02, 0x00, 0x00, 0x00}), 0, 8);

    private final StringFW timeoutDescription = new String8FW("idle-timeout expired");
    private final StringFW timeoutTooSmallDescription = new String8FW("idle-timeout is too small");
    private final StringFW anonymous = new String8FW("ANONYMOUS");

    private final AmqpMessageEncoder amqpMessageHelper = new AmqpMessageEncoder();
    private final AmqpMessageDecoder amqpMessageDecodeHelper = new AmqpMessageDecoder();

    private final MutableInteger minimum = new MutableInteger(Integer.MAX_VALUE);
    private final MutableInteger remoteLinkCreditSum = new MutableInteger(0);

    private final AmqpServerDecoder decodePlainFrame = this::decodePlainFrame;
    private final AmqpServerDecoder decodePerformative = this::decodePerformative;
    private final AmqpServerDecoder decodeSaslFrame = this::decodeSaslFrame;
    private final AmqpServerDecoder decodeProtocolHeader = this::decodeProtocolHeader;
    private final AmqpServerDecoder decodeProtocolHeaderZero = this::decodeProtocolHeaderZero;
    private final AmqpServerDecoder decodeOpen = this::decodeOpen;
    private final AmqpServerDecoder decodeBegin = this::decodeBegin;
    private final AmqpServerDecoder decodeAttach = this::decodeAttach;
    private final AmqpServerDecoder decodeFlow = this::decodeFlow;
    private final AmqpServerDecoder decodeTransfer = this::decodeTransfer;
    private final AmqpServerDecoder decodeDisposition = this::decodeIgnoreFrameBody; // TODO
    private final AmqpServerDecoder decodeDetach = this::decodeDetach;
    private final AmqpServerDecoder decodeEnd = this::decodeEnd;
    private final AmqpServerDecoder decodeClose = this::decodeClose;
    private final AmqpServerDecoder decodeSaslInit = this::decodeSaslInit;
    private final AmqpServerDecoder decodeIgnoreAll = this::decodeIgnoreAll;
    private final AmqpServerDecoder decodeIgnoreFrameBody = this::decodeIgnoreFrameBody;
    private final AmqpServerDecoder decodeUnknownType = this::decodeUnknownType;

    private final Map<AmqpPerformativeType, AmqpServerDecoder> decodersByPerformativeType;
    {
        final Map<AmqpPerformativeType, AmqpServerDecoder> decodersByPerformativeType = new EnumMap<>(AmqpPerformativeType.class);
        decodersByPerformativeType.put(OPEN, decodeOpen);
        decodersByPerformativeType.put(BEGIN, decodeBegin);
        decodersByPerformativeType.put(ATTACH, decodeAttach);
        decodersByPerformativeType.put(FLOW, decodeFlow);
        decodersByPerformativeType.put(TRANSFER, decodeTransfer);
        decodersByPerformativeType.put(DISPOSITION, decodeDisposition);
        decodersByPerformativeType.put(DETACH, decodeDetach);
        decodersByPerformativeType.put(END, decodeEnd);
        decodersByPerformativeType.put(CLOSE, decodeClose);
        this.decodersByPerformativeType = decodersByPerformativeType;
    }

    private final Map<AmqpDescribedType, AmqpServerDecoder> decodersBySaslType;
    {
        final Map<AmqpDescribedType, AmqpServerDecoder> decodersBySaslType = new EnumMap<>(AmqpDescribedType.class);
        // decodersBySaslType.put(SASL_MECHANISMS, decodeSaslMechanisms);
        decodersBySaslType.put(SASL_INIT, decodeSaslInit);
        // decodersBySaslType.put(SASL_CHALLENGE, decodeSaslChallenge);
        // decodersBySaslType.put(SASL_RESPONSE, decodeSaslResponse);
        // decodersBySaslType.put(SASL_OUTCOME, decodeSaslOutcome);
        this.decodersBySaslType = decodersBySaslType;
    }

    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer frameBuffer;
    private final MutableDirectBuffer extraBuffer;
    private final MutableDirectBuffer valueBuffer;
    private final MutableDirectBuffer stringBuffer;

    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongSupplier supplyTraceId;
    private final LongSupplier supplyBudgetId;
    private final LongFunction<BudgetDebitor> supplyDebitor;

    private final BufferPool bufferPool;
    private final BudgetCreditor creditor;
    private final Signaler signaler;
    private final BindingHandler streamFactory;

    private final Long2ObjectHashMap<AmqpBindingConfig> bindings;
    private final int amqpTypeId;

    private final int outgoingWindow;
    private final int closeTimeout;
    private final StringFW containerId;
    private final long defaultMaxFrameSize;
    private final long defaultMaxMessageSize;
    private final long defaultHandleMax;
    private final long defaultIdleTimeout;
    private final StringFW[] defaultIncomingLocales;

    public AmqpServerFactory(
        AmqpConfiguration config,
        EngineContext context)
    {
        this.writeBuffer = context.writeBuffer();
        this.frameBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.extraBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.stringBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.valueBuffer = new UnsafeBuffer(new byte[writeBuffer.capacity()]);
        this.bufferPool = context.bufferPool();
        this.creditor = context.creditor();
        this.signaler = context.signaler();
        this.streamFactory = context.streamFactory();
        this.supplyDebitor = context::supplyDebitor;
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.supplyBudgetId = context::supplyBudgetId;
        this.supplyTraceId = context::supplyTraceId;
        this.bindings = new Long2ObjectHashMap<>();
        this.amqpTypeId = context.supplyTypeId(AmqpBinding.NAME);

        this.containerId = new String8FW(config.containerId());
        this.outgoingWindow = config.outgoingWindow();
        this.defaultMaxFrameSize = config.maxFrameSize();
        this.defaultMaxMessageSize = config.maxMessageSize();
        this.defaultHandleMax = config.handleMax();
        this.defaultIdleTimeout = config.idleTimeout();
        this.defaultIncomingLocales = asStringFWArray(config.incomingLocales());
        this.closeTimeout = config.closeExchangeTimeout();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        AmqpBindingConfig amqpBinding = new AmqpBindingConfig(binding);
        bindings.put(binding.id, amqpBinding);
    }

    @Override
    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long routeId = begin.routeId();

        MessageConsumer newStream = null;

        final AmqpBindingConfig binding = bindings.get(routeId);
        if (binding != null)
        {
            final long initialId = begin.streamId();
            final long affinity = begin.affinity();

            newStream = new AmqpServer(sender, routeId, initialId, affinity)::onNetwork;
        }

        return newStream;
    }

    private AmqpRouteConfig resolveRoute(
        long routeId,
        long authorization,
        StringFW address,
        AmqpCapabilities capabilities)
    {
        final AmqpBindingConfig binding = bindings.get(routeId);
        return binding.resolve(authorization, address.asString(), capabilities);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long routeId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(replyId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        MessageConsumer receiver =
            streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doBegin(
        MessageConsumer receiver,
        long routeId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(replyId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .affinity(affinity)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long routeId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int flags,
        long budgetId,
        int reserved,
        Flyweight payload,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(replyId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .flags(flags)
            .budgetId(budgetId)
            .reserved(reserved)
            .payload(payload != null ? payloadRO.wrap(payload.buffer(), payload.offset(), payload.limit()) : null)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long routeId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(replyId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long routeId,
        long replyId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(replyId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        OctetsFW extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .reserved(reserved)
            .extension(extension)
            .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doWindow(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int minimum)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .budgetId(budgetId)
            .padding(padding)
            .minimum(minimum)
            .build();

        receiver.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer receiver,
        long routeId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Flyweight extension)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity()).routeId(routeId)
            .streamId(streamId)
            .sequence(sequence)
            .acknowledge(acknowledge)
            .maximum(maximum)
            .traceId(traceId)
            .authorization(authorization)
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();

        receiver.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    @FunctionalInterface
    private interface AmqpServerDecoder
    {
        int decode(
            AmqpServer server,
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    @FunctionalInterface
    private interface AmqpSectionDecoder
    {
        int decode(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    @FunctionalInterface
    private interface AmqpSectionEncoder
    {
        int encode(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit);
    }

    private int decodePlainFrame(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        decode:
        if (length != 0)
        {
            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRO.tryWrap(buffer, offset, limit);
            if (frameHeader == null)
            {
                break decode;
            }

            final long frameSize = frameHeader.size();

            if (frameSize > server.decodeMaxFrameSize)
            {
                server.onDecodeError(traceId, authorization, CONNECTION_FRAMING_ERROR, null);
                server.decoder = decodePlainFrame;
                progress = limit;
                break decode;
            }

            if (length < frameSize)
            {
                break decode;
            }

            server.decodableBodyBytes = (int) (frameSize - frameHeader.doff() * 4);
            server.decodeChannel = frameHeader.channel();
            server.decoder = decodePerformative;
            server.readIdleTimeout = defaultIdleTimeout;
            server.doSignalReadIdleTimeoutIfNecessary();
            progress = frameHeader.limit();
        }

        return progress;
    }

    private int decodePerformative(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        decode:
        if (length != 0)
        {
            final AmqpPerformativeTypeFW performativeType = amqpPerformativeTypeRO.tryWrap(buffer, offset, limit);
            if (performativeType == null)
            {
                break decode;
            }

            final AmqpPerformativeType descriptor = performativeType.get();

            final AmqpServer.AmqpSession session = server.sessions.get(server.decodeChannel);
            if (session != null && session.sessionState == AmqpSessionState.DISCARDING && descriptor != END)
            {
                server.decoder = decodeIgnoreFrameBody;
                break decode;
            }

            server.decoder = decodersByPerformativeType.getOrDefault(descriptor, decodeUnknownType);
            server.decodableBodyBytes -= performativeType.sizeof();
            assert server.decodableBodyBytes >= 0;
            progress = performativeType.limit();
        }
        else
        {
            server.decoder = decodePlainFrame;
        }

        return progress;
    }

    private int decodeSaslFrame(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        decode:
        if (length != 0)
        {
            if (server.hasSaslOutcome)
            {
                server.decoder = decodeProtocolHeaderZero;
                break decode;
            }

            final AmqpSaslFrameHeaderFW saslFrameHeader = amqpSaslFrameHeaderRO.tryWrap(buffer, offset, limit);
            if (saslFrameHeader == null)
            {
                break decode;
            }

            final long frameSize = saslFrameHeader.size();

            if (frameSize > server.decodeMaxFrameSize)
            {
                server.onDecodeError(traceId, authorization, CONNECTION_FRAMING_ERROR, null);
                server.decoder = decodePlainFrame;
                progress = limit;
                break decode;
            }

            if (length < frameSize)
            {
                break decode;
            }

            final AmqpSecurityFW security = saslFrameHeader.security();
            final AmqpDescribedType descriptor = security.kind();
            server.decoder = decodersBySaslType.getOrDefault(descriptor, decodeUnknownType);
            progress = security.offset();
        }

        return progress;
    }

    private int decodeProtocolHeader(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpProtocolHeaderFW protocolHeader = amqpProtocolHeaderRO.tryWrap(buffer, offset, limit);
        assert protocolHeader != null;

        final int protocolId = protocolHeader.id();
        int progress;

        switch (protocolId)
        {
        case PLAIN_PROTOCOL_ID:
            server.connectionState = server.connectionState.receivedHeader();
            if (server.connectionState == ERROR)
            {
                decodeError(server, traceId, authorization);
                break;
            }
            server.onDecodeProtocolHeader(traceId, authorization, protocolHeader);
            server.decoder = decodePlainFrame;
            break;
        case SASL_PROTOCOL_ID:
            server.onDecodeSaslProtocolHeader(traceId, authorization, protocolHeader);
            server.decoder = decodeSaslFrame;
            break;
        default:
            server.onDecodeError(traceId, authorization, NOT_ALLOWED, null);
        }
        progress = protocolHeader.limit();

        return progress;
    }

    private int decodeProtocolHeaderZero(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpProtocolHeaderFW protocolHeader = amqpProtocolHeaderRO.tryWrap(buffer, offset, limit);
        int progress = offset;

        decode:
        if (protocolHeader != null)
        {
            server.connectionState = server.connectionState.receivedHeader();
            if (server.connectionState == ERROR)
            {
                server.onDecodeError(traceId, authorization, ILLEGAL_STATE, null);
                decodeError(server, traceId, authorization);
                break decode;
            }

            server.decoder = decodePlainFrame;
            server.onDecodeProtocolHeader(traceId, authorization, protocolHeader);

            progress = protocolHeader.limit();
        }

        return progress;
    }

    private int decodeOpen(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpOpenFW open = amqpOpenRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        decode:
        if (open != null)
        {
            // TODO: verify decodeChannel == 0
            // TODO: verify not already open
            server.connectionState = server.connectionState.receivedOpen();
            if (server.connectionState == ERROR)
            {
                decodeError(server, traceId, authorization);
                break decode;
            }
            server.onDecodeOpen(traceId, authorization, open);
            progress = open.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.connectionState = server.connectionState.receivedOpen();
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;
        return progress;
    }

    private int decodeBegin(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpBeginFW begin = amqpBeginRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        decode:
        if (begin != null)
        {
            server.onDecodeBegin(traceId, authorization, begin);
            if (server.connectionState == DISCARDING)
            {
                server.decoder = decodeIgnoreAll;
                break decode;
            }
            progress = begin.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeAttach(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpAttachFW attach = amqpAttachRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        if (attach != null)
        {
            server.onDecodeAttach(traceId, authorization, attach);
            progress = attach.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeFlow(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpFlowFW flow = amqpFlowRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        if (flow != null)
        {
            server.onDecodeFlow(traceId, authorization, flow);
            progress = flow.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeTransfer(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpTransferFW transfer = amqpTransferRO.tryWrap(buffer, offset, limit);

        int progress = offset;

        if (transfer != null)
        {
            final long deliveryId = transfer.hasDeliveryId() ? transfer.deliveryId() : NO_DELIVERY_ID;
            final long handle = transfer.handle();

            decode:
            {
                AmqpServer.AmqpSession session = server.sessions.get(server.decodeChannel);
                assert session != null; // TODO error if null

                AmqpServer.AmqpSession.AmqpServerStream sender = session.links.get(handle);
                assert sender != null; // TODO error if null

                if (!sender.fragmented)
                {
                    assert deliveryId != NO_DELIVERY_ID; // TODO: error
                    session.remoteDeliveryId = sequenceNext(session.remoteDeliveryId);

                    if (transfer.hasAborted() && transfer.aborted() == 1)
                    {
                        progress = limit;
                        server.decoder = decodePlainFrame;
                        break decode;
                    }

                    if (deliveryId != session.remoteDeliveryId)
                    {
                        server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
                        progress = limit;
                        server.decoder = decodePlainFrame;
                        break decode;
                    }
                }

                server.decodableBodyBytes -= transfer.sizeof();
                final int fragmentOffset = transfer.limit();
                final int fragmentSize = server.decodableBodyBytes;
                final int fragmentLimit = fragmentOffset + fragmentSize;

                if (deliveryId != NO_DELIVERY_ID && deliveryId != session.remoteDeliveryId)
                {
                    server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
                    progress = fragmentLimit;
                    server.decoder = decodePlainFrame;
                    break decode;
                }

                assert fragmentLimit <= limit;

                int reserved = fragmentSize + sender.initialPad;
                boolean canSend = reserved <= sender.initialMax;

                if (canSend && sender.debitorIndex != NO_DEBITOR_INDEX)
                {
                    reserved = sender.debitor.claim(traceId, sender.debitorIndex, sender.initialId, reserved, reserved, 0);
                }

                if (canSend && reserved != 0)
                {
                    server.onDecodeTransfer(traceId, authorization, transfer, reserved, buffer, fragmentOffset, fragmentLimit);

                    server.decoder = decodePlainFrame;
                    progress = fragmentLimit;
                }
            }
        }

        return progress;
    }

    private void decodeError(
        AmqpServer server,
        final long traceId,
        final long authorization)
    {
        server.onDecodeError(traceId, authorization, DECODE_ERROR, null);
        server.decoder = decodeIgnoreAll;
    }

    private int decodeDetach(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpDetachFW detach = amqpDetachRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        if (detach != null)
        {
            server.onDecodeDetach(traceId, authorization, detach);

            progress = detach.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeEnd(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpEndFW end = amqpEndRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        if (end != null)
        {
            server.onDecodeEnd(traceId, authorization, end);

            progress = end.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeClose(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpCloseFW close = amqpCloseRO.tryWrap(buffer, offset, limit);

        int progress = offset;
        int length = limit - offset;

        decode:
        if (close != null)
        {
            server.connectionState = server.connectionState.receivedClose();
            if (server.connectionState == ERROR)
            {
                decodeError(server, traceId, authorization);
                break decode;
            }

            server.onDecodeClose(traceId, authorization);

            progress = close.limit();
        }
        else if (length >= server.decodableBodyBytes)
        {
            server.onDecodeError(traceId, authorization, INVALID_FIELD, null);
            progress = limit;
        }
        server.decoder = decodePlainFrame;

        return progress;
    }

    private int decodeSaslInit(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        final AmqpSecurityFW security = amqpSecurityRO.tryWrap(buffer, offset, limit);
        int progress = offset;

        decode:
        if (security != null)
        {
            final AmqpSaslInitFW saslInit = security.saslInit();
            assert saslInit != null;

            if (server.connectionState != START)
            {
                decodeError(server, traceId, authorization);
                break decode;
            }

            server.onDecodeSaslInit(traceId, authorization, saslInit);
            server.decoder = decodeSaslFrame;
            progress = saslInit.limit();
        }

        return progress;
    }

    private int decodeIgnoreAll(
        AmqpServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        return limit;
    }

    private int decodeIgnoreFrameBody(
        AmqpServer server,
        long traceId,
        long authorization,
        long budgetId,
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        final int length = limit - offset;

        int progress = offset;

        if (length != 0)
        {
            progress = Math.min(offset + server.decodableBodyBytes, limit);
            server.decodableBodyBytes -= progress - offset;
            assert server.decodableBodyBytes >= 0;
            if (server.decodableBodyBytes == 0)
            {
                server.decoder = decodePlainFrame;
            }
        }

        return progress;
    }

    private int decodeUnknownType(
        AmqpServer server,
        final long traceId,
        final long authorization,
        final long budgetId,
        final DirectBuffer buffer,
        final int offset,
        final int limit)
    {
        server.onDecodeError(traceId, authorization, DECODE_ERROR, null);
        server.decoder = decodeIgnoreAll;
        return limit;
    }

    private final class AmqpServer
    {
        private final MessageConsumer network;
        private final long routeId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long budgetId;
        private final long replySharedBudgetId;

        private final Int2ObjectHashMap<AmqpSession> sessions;

        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyPad;
        private long replyBudgetId;

        private long replyBudgetIndex = NO_CREDITOR_INDEX;
        private int replySharedBudget;
        private int replyBudgetReserved;

        private int decodeSlot = NO_SLOT;
        private int decodeSlotOffset;
        private int decodeSlotReserved;

        private int encodeSlot = NO_SLOT;
        private int encodeSlotOffset;
        private long encodeSlotTraceId;
        private int encodeSlotMaxLimit = Integer.MAX_VALUE;

        private int decodeChannel;
        private int outgoingChannel;
        private int decodableBodyBytes;
        private long decodeHandleMax;
        private long decodeMaxFrameSize = MIN_MAX_FRAME_SIZE;
        private int encodeMaxFrameSize = MIN_MAX_FRAME_SIZE;
        private long writeIdleTimeout = DEFAULT_IDLE_TIMEOUT;
        private long readIdleTimeout = DEFAULT_IDLE_TIMEOUT;

        private long readIdleTimeoutId = NO_CANCEL_ID;
        private long readIdleTimeoutAt;

        private long writeIdleTimeoutId = NO_CANCEL_ID;
        private long writeIdleTimeoutAt;

        private long closeTimeoutId = NO_CANCEL_ID;

        private boolean hasSaslOutcome;

        private AmqpServerDecoder decoder;

        private int state;
        private AmqpConnectionState connectionState;

        private AmqpServer(
            MessageConsumer network,
            long routeId,
            long initialId,
            long affinity)
        {
            this.network = network;
            this.routeId = routeId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.budgetId = supplyBudgetId.getAsLong();
            this.affinity = affinity;
            this.replySharedBudgetId = budgetId;
            this.decoder = decodeProtocolHeader;
            this.sessions = new Int2ObjectHashMap<>();
            this.hasSaslOutcome = false;
            this.decodeMaxFrameSize = defaultMaxFrameSize;
            this.decodeHandleMax = defaultHandleMax;
            this.connectionState = START;
        }

        private int replyPendingAck()
        {
            return (int)(replySeq - replyAck);
        }

        private int replyWindow()
        {
            return replyMax - replyPendingAck();
        }

        private void doEncodePlainProtocolHeader(
            long traceId,
            long authorization)
        {
            doNetworkData(traceId, authorization, 0L, plainProtocolHeader);
        }

        private void doEncodePlainProtocolHeaderIfNecessary(
            long traceId,
            long authorization)
        {
            replyBudgetReserved += plainProtocolHeader.sizeof() + replyPad;
            if (!hasSaslOutcome)
            {
                doEncodePlainProtocolHeader(traceId, authorization);
                connectionState = connectionState.sentHeader();
                assert connectionState != ERROR;
            }
        }

        private void doEncodeSaslProtocolHeader(
            long traceId,
            long authorization)
        {
            replyBudgetReserved += saslProtocolHeader.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, saslProtocolHeader);
            doEncodeSaslMechanisms(traceId, authorization, anonymous);
        }

        private void doEncodeSaslMechanisms(
            long traceId,
            long authorization,
            StringFW mechanisms)
        {
            Array8FW<AmqpSymbolFW> annonymousRO = anonymousRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                .item(i -> i.set(mechanisms))
                .build();

            final AmqpSaslMechanismsFW saslMechanisms =
                amqpSaslMechanismsRW.wrap(frameBuffer, FRAME_HEADER_SIZE + SASL_DESCRIPTOR_SIZE, frameBuffer.capacity())
                    .mechanisms(annonymousRO)
                    .build();

            final AmqpSaslFrameHeaderFW saslFrameHeader = amqpSaslFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(FRAME_HEADER_SIZE + SASL_DESCRIPTOR_SIZE + saslMechanisms.sizeof())
                .security(b -> b.saslMechanisms(saslMechanisms))
                .build();

            replyBudgetReserved += saslFrameHeader.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, saslFrameHeader);
        }

        private void doEncodeSaslOutcome(
            long traceId,
            long authorization,
            AmqpSaslInitFW saslInit)
        {
            final AmqpSaslOutcomeFW saslOutcome =
                amqpSaslOutcomeRW.wrap(frameBuffer, FRAME_HEADER_SIZE + SASL_DESCRIPTOR_SIZE, frameBuffer.capacity())
                    .code(OK)
                    .build();

            final AmqpSaslFrameHeaderFW saslFrameHeader =
                amqpSaslFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                    .size(FRAME_HEADER_SIZE + SASL_DESCRIPTOR_SIZE + saslOutcome.sizeof())
                    .security(b -> b.saslOutcome(saslOutcome))
                    .build();

            replyBudgetReserved += saslFrameHeader.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, saslFrameHeader);
        }

        private void doEncodeEmptyFrame(
            long traceId,
            long authorization)
        {
            doNetworkData(traceId, authorization, 0L, emptyFrameHeader);
        }

        private void doEncodeOpen(
            long traceId,
            long authorization)
        {
            final int performativeSize = openType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, openType.buffer(), 0, performativeSize);

            final AmqpOpenFW.Builder builder =
                amqpOpenRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .containerId(containerId);

            if (decodeMaxFrameSize != DEFAULT_VALUE_MAX_FRAME_SIZE)
            {
                builder.maxFrameSize(decodeMaxFrameSize);
            }

            if (defaultIdleTimeout != DEFAULT_IDLE_TIMEOUT)
            {
                builder.idleTimeOut(defaultIdleTimeout);
            }

            if (defaultIncomingLocales.length > 0 && !Arrays.equals(defaultIncomingLocales, DEFAULT_INCOMING_LOCALES))
            {
                Array8FW.Builder<AmqpIETFLanguageTagFW.Builder, AmqpIETFLanguageTagFW> incomingLocales =
                        incomingLocalesRW.wrap(extraBuffer, 0, extraBuffer.capacity());
                for (StringFW incomingLocale : defaultIncomingLocales)
                {
                    incomingLocales.item(i -> i.set(incomingLocale));
                }
                builder.incomingLocales(incomingLocales.build());
            }

            final AmqpOpenFW open = builder.build();

            final int size = FRAME_HEADER_SIZE + performativeSize + open.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(0)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            final OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += payload.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeBegin(
            long traceId,
            long authorization,
            int remoteChannel,
            int nextOutgoingId)
        {
            final int performativeSize = beginType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, beginType.buffer(), 0, performativeSize);

            final AmqpBeginFW.Builder builder =
                amqpBeginRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .remoteChannel(remoteChannel)
                    .nextOutgoingId(nextOutgoingId)
                    .incomingWindow(bufferPool.slotCapacity())
                    .outgoingWindow(outgoingWindow);

            if (decodeHandleMax != DEFAULT_VALUE_HANDLE_MAX)
            {
                builder.handleMax(decodeHandleMax);
            }

            final AmqpBeginFW begin = builder.build();

            final int size = FRAME_HEADER_SIZE + performativeSize + begin.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(outgoingChannel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            final OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += payload.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeAttach(
            long traceId,
            long authorization,
            String name,
            int channel,
            long handle,
            AmqpRole role,
            AmqpSenderSettleMode senderSettleMode,
            AmqpReceiverSettleMode receiverSettleMode,
            StringFW addressFrom,
            StringFW addressTo,
            long deliveryCount,
            long maxMessageSize)
        {
            final int performativeSize = attachType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, attachType.buffer(), 0, performativeSize);

            AmqpAttachFW.Builder builder =
                amqpAttachRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .name(amqpStringRW.wrap(stringBuffer, 0, stringBuffer.capacity()).set(name, UTF_8).build().get())
                    .handle(handle)
                    .role(role)
                    .sndSettleMode(senderSettleMode)
                    .rcvSettleMode(receiverSettleMode);

            int extraOffset = 0;
            if (addressFrom != null && addressFrom.length() != -1)
            {
                AmqpSourceListFW sourceList = amqpSourceListRW
                    .wrap(extraBuffer, extraOffset, extraBuffer.capacity())
                    .address(addressFrom)
                    .build();
                builder.source(b -> b.sourceList(sourceList));
                extraOffset = sourceList.limit();
            }

            if (addressTo != null)
            {
                if (addressTo.length() != -1)
                {
                    AmqpTargetListFW targetList = amqpTargetListRW
                        .wrap(extraBuffer, extraOffset, extraBuffer.capacity())
                        .address(addressTo)
                        .build();
                    builder.target(b -> b.targetList(targetList));
                }
                else
                {
                    AmqpTargetListFW targetList = amqpTargetListRW
                        .wrap(extraBuffer, extraOffset, extraBuffer.capacity())
                        .build();
                    builder.target(b -> b.targetList(targetList));
                }
            }

            if (role == AmqpRole.SENDER)
            {
                builder.initialDeliveryCount(deliveryCount);
            }

            if (maxMessageSize > 0)
            {
                builder.maxMessageSize(maxMessageSize);
            }

            final AmqpAttachFW attach = builder.build();

            final int size = FRAME_HEADER_SIZE + performativeSize + attach.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(channel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            final OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += payload.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeFlow(
            long traceId,
            long authorization,
            int channel,
            int nextOutgoingId,
            long nextIncomingId,
            long incomingWindow,
            long handle,
            long deliveryCount,
            int linkCredit)
        {
            final int performativeSize = flowType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, flowType.buffer(), 0, performativeSize);

            final AmqpFlowFW.Builder builder =
                amqpFlowRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .nextIncomingId(nextIncomingId)
                    .incomingWindow(incomingWindow)
                    .nextOutgoingId(nextOutgoingId)
                    .outgoingWindow(outgoingWindow);

            if (handle >= 0)
            {
                builder.handle(handle)
                    .deliveryCount(deliveryCount)
                    .linkCredit(linkCredit);
            }

            final AmqpFlowFW flow = builder.build();

            final int size = FRAME_HEADER_SIZE + performativeSize + flow.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(channel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            final OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += payload.sizeof() + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeTransfer(
            long traceId,
            long authorization,
            int channel,
            AmqpTransferFW transfer,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            final int performativeSize = transferType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, transferType.buffer(), 0, performativeSize);
            frameBuffer.putBytes(FRAME_HEADER_SIZE + performativeSize, transfer.buffer(), transfer.offset(), transfer.sizeof());
            frameBuffer.putBytes(FRAME_HEADER_SIZE + performativeSize + transfer.sizeof(), buffer, offset, length);

            final int size = FRAME_HEADER_SIZE + performativeSize + transfer.sizeof() + length;
            assert size <= encodeMaxFrameSize;

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(channel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += size + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeTransferFragments(
            long traceId,
            long authorization,
            int channel,
            long handle,
            boolean more,
            DirectBuffer fragmentBuffer,
            int fragmentProgress,
            int fragmentLimit)
        {
            int fragmentRemaining = fragmentLimit - fragmentProgress;

            final int performativeSize = transferType.sizeof();
            AmqpTransferFW transferCont = amqpTransferRW
                    .wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .handle(handle)
                    .more(1)
                    .build();
            int fragmentSizeCont = encodeMaxFrameSize - FRAME_HEADER_SIZE - performativeSize - transferCont.sizeof();
            while (fragmentRemaining > fragmentSizeCont)
            {
                doEncodeTransfer(traceId, authorization, outgoingChannel, transferCont, fragmentBuffer, fragmentProgress,
                    fragmentSizeCont);
                fragmentProgress += fragmentSizeCont;
                fragmentRemaining -= fragmentSizeCont;
            }

            AmqpTransferFW.Builder transferFinBuilder = amqpTransferRW
                    .wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .handle(handle);

            if (more)
            {
                transferFinBuilder.more(1);
            }

            AmqpTransferFW transferFin = transferFinBuilder.build();

            int fragmentSizeFin = encodeMaxFrameSize - FRAME_HEADER_SIZE - performativeSize - transferFin.sizeof();
            assert fragmentRemaining <= fragmentSizeFin;

            doEncodeTransfer(traceId, authorization, channel, transferFin, fragmentBuffer,
                fragmentProgress, fragmentRemaining);
            fragmentProgress += fragmentRemaining;
            assert fragmentProgress == fragmentLimit;
        }

        private void doEncodeDetach(
            long traceId,
            long authorization,
            AmqpErrorType errorType,
            int channel,
            long handle)
        {
            final int performativeSize = detachType.sizeof();

            frameBuffer.putBytes(FRAME_HEADER_SIZE, detachType.buffer(), 0, performativeSize);

            final AmqpDetachFW.Builder detachRW =
                amqpDetachRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                    .handle(handle)
                    .closed(1);

            AmqpDetachFW detach;
            if (errorType != null)
            {
                AmqpErrorListFW errorList = amqpErrorListRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                    .condition(errorType)
                    .build();
                detach = detachRW.error(e -> e.errorList(errorList)).build();
            }
            else
            {
                detach = detachRW.build();
            }

            final int size = FRAME_HEADER_SIZE + performativeSize + detach.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(channel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += size + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeEnd(
            long traceId,
            long authorization,
            int channel,
            AmqpErrorType errorType)
        {
            final int performativeSize = endType.sizeof();

            frameBuffer.putBytes(FRAME_HEADER_SIZE, endType.buffer(), 0, performativeSize);

            final AmqpEndFW.Builder builder =
                amqpEndRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity());

            AmqpEndFW end;
            if (errorType != null)
            {
                AmqpErrorListFW errorList = amqpErrorListRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                    .condition(errorType)
                    .build();

                end = builder.error(e -> e.errorList(errorList)).build();
            }
            else
            {
                end = builder.build();
            }

            final int size = FRAME_HEADER_SIZE + performativeSize + end.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(channel)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += size + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
        }

        private void doEncodeClose(
            long traceId,
            long authorization,
            AmqpErrorType errorType,
            StringFW errorDescription)
        {
            final int performativeSize = closeType.sizeof();
            frameBuffer.putBytes(FRAME_HEADER_SIZE, closeType.buffer(), 0, performativeSize);

            final AmqpCloseFW.Builder builder =
                amqpCloseRW.wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity());

            AmqpCloseFW close;
            if (errorType != null)
            {
                AmqpErrorListFW.Builder errorBuilder = amqpErrorListRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                    .condition(errorType);
                if (errorDescription != null)
                {
                    errorBuilder.description(errorDescription);
                }
                AmqpErrorListFW errorList = errorBuilder.build();
                close = builder.error(e -> e.errorList(errorList)).build();
            }
            else
            {
                close = builder.build();
            }

            final int size = FRAME_HEADER_SIZE + performativeSize + close.sizeof();

            final AmqpFrameHeaderFW frameHeader = amqpFrameHeaderRW.wrap(frameBuffer, 0, frameBuffer.capacity())
                .size(size)
                .doff(2)
                .type(0)
                .channel(0)
                .build();

            assert frameHeader.sizeof() == FRAME_HEADER_SIZE;

            OctetsFW payload = payloadRO.wrap(frameBuffer, 0, size);

            replyBudgetReserved += size + replyPad;
            doNetworkData(traceId, authorization, 0L, payload);
            doSignalCloseTimeout();
        }

        private void encodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit,
            int maxLimit)
        {
            encodeNetworkData(traceId, authorization, budgetId, buffer, offset, limit, maxLimit);
        }

        private void encodeNetworkData(
            long traceId,
            long authorization,
            long budgetId,
            DirectBuffer buffer,
            int offset,
            int limit,
            int maxLimit)
        {
            final int length = Math.max(Math.min(replyWindow() - replyPad, limit - offset), 0);

            if (length > 0)
            {
                final int reserved = length + replyPad;

                OctetsFW payload = payloadRO.wrap(buffer, offset, limit);
                doData(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, FLAG_INIT_AND_FIN,
                    budgetId, reserved, payload, EMPTY_OCTETS);

                replySeq += reserved;

                assert replySeq <= replyAck + replyMax :
                    String.format("%d <= %d + %d", replySeq, replyAck, replyMax);

                doSignalWriteIdleTimeoutIfNecessary();
            }

            final int maxLength = maxLimit - offset;
            final int remaining = maxLength - length;
            if (remaining > 0)
            {
                if (encodeSlot == NO_SLOT)
                {
                    encodeSlot = bufferPool.acquire(replyId);
                }
                else
                {
                    encodeSlotMaxLimit -= length;
                    assert encodeSlotMaxLimit >= 0;
                }

                if (encodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                    encodeBuffer.putBytes(0, buffer, offset + length, remaining);
                    encodeSlotOffset = remaining;
                }
            }
            else
            {
                cleanupEncodeSlotIfNecessary();
                if (sessions.isEmpty() && decoder == decodeIgnoreAll)
                {
                    doNetworkEnd(traceId, authorization);
                }
            }
        }

        private void onNetwork(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onNetworkBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onNetworkData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onNetworkEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onNetworkAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onNetworkFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onNetworkWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onNetworkReset(reset);
                break;
            case SignalFW.TYPE_ID:
                final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                onNetworkSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onNetworkBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();
            final long authorization = begin.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge >= initialAck;

            state = AmqpState.openingInitial(state);
            initialSeq = sequence;
            initialAck = acknowledge;

            assert initialAck <= initialSeq;

            doNetworkBegin(traceId, authorization);
            doNetworkWindow(traceId, authorization, 0L, 0, bufferPool.slotCapacity());
        }

        private void onNetworkData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long authorization = data.authorization();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + data.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doNetworkReset(supplyTraceId.getAsLong(), authorization);
            }
            else
            {
                final long budgetId = data.budgetId();
                final OctetsFW payload = data.payload();

                DirectBuffer buffer = payload.buffer();
                int offset = payload.offset();
                int limit = payload.limit();
                int reserved = data.reserved();

                if (decodeSlot != NO_SLOT)
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    slotBuffer.putBytes(decodeSlotOffset, buffer, offset, limit - offset);
                    decodeSlotOffset += limit - offset;
                    decodeSlotReserved += reserved;

                    buffer = slotBuffer;
                    offset = 0;
                    limit = decodeSlotOffset;
                    reserved = decodeSlotReserved;
                }

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void onNetworkFlush(
            FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final long authorization = flush.authorization();
            final long budgetId = flush.budgetId();
            final int reserved = flush.reserved();
            final OctetsFW extension = flush.extension();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;
            assert acknowledge <= initialAck;

            initialSeq = sequence + flush.reserved();

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doNetworkReset(supplyTraceId.getAsLong(), authorization);
            }
            else
            {
                sessions.values().forEach(s -> s.links.values().forEach(l -> l.doApplicationFlush(traceId, authorization,
                    budgetId, reserved, extension)));
            }
        }

        private void onNetworkEnd(
            EndFW end)
        {
            final long authorization = end.authorization();

            state = AmqpState.closeInitial(state);

            if (decodeSlot == NO_SLOT)
            {
                final long traceId = end.traceId();

                cleanupStreams(traceId, authorization);

                doNetworkEndIfNecessary(traceId, authorization);
            }
            decoder = decodeIgnoreAll;
        }

        private void onNetworkAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();
            final long authorization = abort.authorization();

            cleanupStreams(traceId, authorization);
            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doNetworkAbort(traceId, authorization);
        }

        private void onNetworkWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long authorization = window.authorization();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            final long replyWin = replySeq - replyAck;
            final long newReplyWin = sequence - acknowledge;
            final int credit = (int) (replyWin - newReplyWin) + (maximum - replyMax);

            state = AmqpState.openReply(state);
            replyAck = acknowledge;
            replyMax = maximum;
            replyBudgetId = budgetId;
            replyPad = padding;

            assert replyAck <= replySeq;

            if (replyBudgetReserved > 0)
            {
                final int reservedCredit = Math.min(credit, replyBudgetReserved);
                replyBudgetReserved -= reservedCredit;
            }

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer buffer = bufferPool.buffer(encodeSlot);
                final int limit = Math.min(encodeSlotOffset, encodeSlotMaxLimit);
                final int maxLimit = encodeSlotOffset;

                encodeNetwork(encodeSlotTraceId, authorization, budgetId, buffer, 0, limit, maxLimit);
            }

            flushReplySharedBudget(traceId);
        }

        private void flushReplySharedBudget(
            long traceId)
        {
            final int slotCapacity = bufferPool.slotCapacity();
            minimum.value = Integer.MAX_VALUE;
            sessions.values().forEach(s -> minimum.value = Math.min(s.remoteIncomingWindow, minimum.value));

            final int budget = replyWindow();
            final int replySharedBudgetMax = sessions.values().size() > 0 ?
                Math.min(minimum.value * encodeMaxFrameSize, budget) : budget;
            final int replySharedCredit = replySharedBudgetMax - Math.max(this.replySharedBudget, 0)
                - Math.max(encodeSlotOffset, 0);

            if (replySharedCredit != 0 && replyBudgetReserved == 0)
            {
                final long replySharedBudgetPrevious = creditor.credit(traceId, replyBudgetIndex, replySharedCredit);

                this.replySharedBudget += replySharedCredit;
                assert replySharedBudgetPrevious <= slotCapacity
                    : String.format("%d <= %d, replyBudget = %d",
                    replySharedBudgetPrevious, slotCapacity, budget);

                assert replySharedBudget <= slotCapacity
                    : String.format("%d <= %d", replySharedBudget, slotCapacity);
            }
        }

        private void onNetworkReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final long authorization = reset.authorization();

            cleanupStreams(traceId, authorization);
            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doNetworkReset(traceId, authorization);
        }

        private void onNetworkSignal(
            SignalFW signal)
        {
            final int signalId = signal.signalId();

            switch (signalId)
            {
            case READ_IDLE_SIGNAL_ID:
                onReadIdleTimeoutSignal(signal);
                break;
            case WRITE_IDLE_SIGNAL_ID:
                onWriteIdleTimeoutSignal(signal);
                break;
            case CLOSE_SIGNAL_ID:
                onCloseTimeoutSignal(signal);
                break;
            default:
                break;
            }
        }

        private void onReadIdleTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            final long now = currentTimeMillis();
            if (now >= readIdleTimeoutAt)
            {
                onDecodeError(traceId, authorization, RESOURCE_LIMIT_EXCEEDED, timeoutDescription);
                decoder = decodeIgnoreAll;
            }
            else
            {
                readIdleTimeoutId = signaler.signalAt(readIdleTimeoutAt, routeId, replyId, READ_IDLE_SIGNAL_ID, 0);
            }
        }

        private void onWriteIdleTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();

            final long now = currentTimeMillis();
            if (now >= writeIdleTimeoutAt)
            {
                writeIdleTimeoutId = NO_CANCEL_ID;
                doEncodeEmptyFrame(traceId, authorization);
            }
            else
            {
                writeIdleTimeoutId = signaler.signalAt(writeIdleTimeoutAt, routeId, replyId, WRITE_IDLE_SIGNAL_ID, 0);
            }
        }

        private void onCloseTimeoutSignal(
            SignalFW signal)
        {
            final long traceId = signal.traceId();
            final long authorization = signal.authorization();
            doNetworkEndIfNecessary(traceId, authorization);
        }

        private void onDecodeError(
            long traceId,
            long authorization,
            AmqpErrorType errorType,
            StringFW errorDescription)
        {
            cleanupStreams(traceId, authorization);
            doEncodeCloseAndEndIfNecessary(traceId, authorization, errorType, errorDescription);
        }

        private void doNetworkBegin(
            long traceId,
            long authorization)
        {
            state = AmqpState.openingReply(state);

            doBegin(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, affinity, EMPTY_OCTETS);

            assert replyBudgetIndex == NO_CREDITOR_INDEX;
            this.replyBudgetIndex = creditor.acquire(replySharedBudgetId);
        }

        private void doNetworkData(
            long traceId,
            long authorization,
            long budgetId,
            Flyweight payload)
        {
            DirectBuffer buffer = payload.buffer();
            int offset = payload.offset();
            int limit = payload.limit();
            int maxLimit = limit;

            if (encodeSlot != NO_SLOT)
            {
                final MutableDirectBuffer encodeBuffer = bufferPool.buffer(encodeSlot);
                encodeBuffer.putBytes(encodeSlotOffset, buffer, offset, limit - offset);
                encodeSlotOffset += limit - offset;
                encodeSlotTraceId = traceId;

                buffer = encodeBuffer;
                offset = 0;
                limit = Math.min(encodeSlotOffset, encodeSlotMaxLimit);
                maxLimit = encodeSlotOffset;
            }

            encodeNetwork(traceId, authorization, budgetId, buffer, offset, limit, maxLimit);
        }

        private void doNetworkEnd(
            long traceId,
            long authorization)
        {
            state = AmqpState.closeReply(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doEnd(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkAbort(
            long traceId,
            long authorization)
        {
            state = AmqpState.closeReply(state);

            cleanupBudgetCreditorIfNecessary();
            cleanupEncodeSlotIfNecessary();

            doAbort(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
        }

        private void doNetworkFlush(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            OctetsFW extension)
        {
            doFlush(network, routeId, replyId, replySeq, replyAck, replyMax, traceId, authorization, budgetId, reserved,
                extension);
        }

        private void doNetworkReset(
            long traceId,
            long authorization)
        {
            state = AmqpState.closeInitial(state);

            cleanupDecodeSlotIfNecessary();

            doReset(network, routeId, initialId, initialSeq, initialAck, bufferPool.slotCapacity(), traceId, authorization,
                EMPTY_OCTETS);
        }

        private void doNetworkWindow(
            long traceId,
            long authorization,
            long budgetId,
            int minInitialNoAck,
            int minInitialMax)
        {
            final long newInitialAck = Math.max(initialSeq - minInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax || !AmqpState.initialOpened(state))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = AmqpState.openInitial(state);

                doWindow(network, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, budgetId, 0, 0);
            }
        }

        private void decodeNetworkIfNecessary(
            long traceId)
        {
            if (decodeSlot != NO_SLOT)
            {
                final long authorization = 0L; // TODO
                final long budgetId = 0L; // TODO

                final DirectBuffer buffer = bufferPool.buffer(decodeSlot);
                final int offset = 0;
                final int limit = decodeSlotOffset;
                final int reserved = decodeSlotReserved;

                decodeNetwork(traceId, authorization, budgetId, reserved, buffer, offset, limit);
            }
        }

        private void decodeNetwork(
            long traceId,
            long authorization,
            long budgetId,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            AmqpServerDecoder previous = null;
            int progress = offset;
            while (progress <= limit && previous != decoder)
            {
                previous = decoder;
                progress = decoder.decode(this, traceId, authorization, budgetId, buffer, progress, limit);
            }

            if (progress < limit)
            {
                if (decodeSlot == NO_SLOT)
                {
                    decodeSlot = bufferPool.acquire(initialId);
                }

                if (decodeSlot == NO_SLOT)
                {
                    cleanupNetwork(traceId, authorization);
                }
                else
                {
                    final MutableDirectBuffer slotBuffer = bufferPool.buffer(decodeSlot);
                    decodeSlotOffset = limit - progress;
                    decodeSlotReserved = (int)((long) reserved * (limit - progress) / (limit - offset));
                    slotBuffer.putBytes(0, buffer, progress, decodeSlotOffset);
                }
            }
            else
            {
                cleanupDecodeSlotIfNecessary();

                if (AmqpState.initialClosed(state))
                {
                    cleanupStreams(traceId, authorization);
                    doNetworkEndIfNecessary(traceId, authorization);
                }
            }

            if (!AmqpState.initialClosed(state))
            {
                final int decoded = reserved - decodeSlotReserved;

                final long initialAckMax = Math.min(initialAck + decoded, initialSeq);
                if (initialAckMax > initialAck)
                {
                    initialAck = initialAckMax;
                    assert initialAck <= initialSeq;

                    doNetworkWindow(traceId, authorization, budgetId, 0, initialMax);
                }
            }
        }

        private void onDecodeProtocolHeader(
            long traceId,
            long authorization,
            AmqpProtocolHeaderFW header)
        {
            doEncodePlainProtocolHeaderIfNecessary(traceId, authorization);
            if (!isProtocolHeaderValid(header))
            {
                doNetworkEnd(traceId, authorization);
            }
            else if (!hasSaslOutcome)
            {
                doEncodeOpen(traceId, authorization);
                connectionState = connectionState.sentOpen();
                assert connectionState != ERROR;
            }
        }

        private void onDecodeSaslProtocolHeader(
            long traceId,
            long authorization,
            AmqpProtocolHeaderFW header)
        {
            if (isSaslProtocolHeaderValid(header))
            {
                doEncodeSaslProtocolHeader(traceId, authorization);
            }
            else
            {
                onDecodeError(traceId, authorization, DECODE_ERROR, null);
            }
        }

        private void onDecodeOpen(
            long traceId,
            long authorization,
            AmqpOpenFW open)
        {
            // TODO: use buffer slot capacity instead
            this.encodeMaxFrameSize = (int) Math.min(replySharedBudget, open.maxFrameSize());
            this.writeIdleTimeout = open.hasIdleTimeOut() ? open.idleTimeOut() : DEFAULT_IDLE_TIMEOUT;

            if (writeIdleTimeout > 0)
            {
                if (writeIdleTimeout < MIN_IDLE_TIMEOUT)
                {
                    onDecodeError(traceId, authorization, NOT_ALLOWED, timeoutTooSmallDescription);
                }
                doSignalWriteIdleTimeoutIfNecessary();
            }
        }

        private void onDecodeBegin(
            long traceId,
            long authorization,
            AmqpBeginFW begin)
        {
            if (begin.hasRemoteChannel())
            {
                onDecodeError(traceId, authorization, NOT_ALLOWED, null);
            }
            else
            {
                AmqpSession session = sessions.computeIfAbsent(decodeChannel, AmqpSession::new);
                session.sessionState = session.sessionState.receivedBegin();
                if (session.sessionState == AmqpSessionState.ERROR)
                {
                    onDecodeError(traceId, authorization, ILLEGAL_STATE, null);
                }
                else
                {
                    session.outgoingChannel(outgoingChannel);
                    session.nextIncomingId(begin.nextOutgoingId());
                    session.incomingWindow(writeBuffer.capacity());
                    session.outgoingWindow(outgoingWindow);
                    session.remoteIncomingWindow((int) begin.incomingWindow());
                    session.remoteOutgoingWindow((int) begin.outgoingWindow());
                    session.onDecodeBegin(traceId, authorization);
                    this.outgoingChannel++;
                }
            }
        }

        private void onDecodeAttach(
            long traceId,
            long authorization,
            AmqpAttachFW attach)
        {
            AmqpSession session = sessions.get(decodeChannel);
            decode:
            if (session != null)
            {
                final long handle = attach.handle();
                if (handle > decodeHandleMax)
                {
                    onDecodeError(traceId, authorization, CONNECTION_FRAMING_ERROR, null);
                    break decode;
                }

                final boolean handleInUse = session.links.containsKey(handle);
                if (handleInUse)
                {
                    onDecodeError(traceId, authorization, SESSION_HANDLE_IN_USE, null);
                    break decode;
                }
                session.onDecodeAttach(traceId, authorization, attach);
            }
            else
            {
                onDecodeError(traceId, authorization, NOT_ALLOWED, null);
            }
        }

        private void onDecodeFlow(
            long traceId,
            long authorization,
            AmqpFlowFW flow)
        {
            AmqpSession session = sessions.get(decodeChannel);
            if (session != null)
            {
                session.onDecodeFlow(traceId, authorization, flow);
            }
            else
            {
                onDecodeError(traceId, authorization, NOT_ALLOWED, null);
            }
        }

        private void onDecodeTransfer(
            long traceId,
            long authorization,
            AmqpTransferFW transfer,
            int reserved,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            AmqpSession session = sessions.get(decodeChannel);
            if (session != null)
            {
                session.onDecodeTransfer(traceId, authorization, transfer, reserved, buffer, offset, limit);
            }
            else
            {
                onDecodeError(traceId, authorization, NOT_ALLOWED, null);
            }
        }

        private void onDecodeDetach(
            long traceId,
            long authorization,
            AmqpDetachFW detach)
        {
            AmqpErrorType error = null;
            if (detach.hasError())
            {
                error = detach.error().errorList().condition();
            }
            AmqpSession session = sessions.get(decodeChannel);
            if (session != null)
            {
                session.onDecodeDetach(traceId, authorization, error, detach.handle());
            }
        }

        private void onDecodeEnd(
            long traceId,
            long authorization,
            AmqpEndFW end)
        {
            AmqpErrorType errorType = null;
            if (end.fieldCount() > 0)
            {
                errorType = end.error().errorList().condition();
            }
            AmqpSession session = sessions.get(decodeChannel);
            decode:
            if (session != null)
            {
                session.sessionState = session.sessionState.receivedEnd();
                if (session.sessionState == AmqpSessionState.ERROR)
                {
                    break decode;
                }
                session.doEncodeEndIfNecessary(traceId, authorization, errorType);
                session.cleanup(traceId, authorization);
            }
        }

        private void onDecodeClose(
            long traceId,
            long authorization)
        {
            sessions.values().forEach(s -> s.cleanup(traceId, authorization));
            doEncodeCloseAndEndIfNecessary(traceId, authorization, null, null);
            doCancelCloseTimeoutIfNecessary();
        }

        private void onDecodeSaslInit(
            long traceId,
            long authorization,
            AmqpSaslInitFW saslInit)
        {
            this.hasSaslOutcome = true;
            doEncodeSaslOutcome(traceId, authorization, saslInit);
            doEncodePlainProtocolHeader(traceId, authorization);
            connectionState = connectionState.sentHeader();
            doEncodeOpen(traceId, authorization);
            connectionState = connectionState.sentOpen();
        }

        private boolean isProtocolHeaderValid(
            AmqpProtocolHeaderFW header)
        {
            return PROTOCOL_HEADER == header.buffer().getLong(header.offset(), BIG_ENDIAN);
        }

        private boolean isSaslProtocolHeaderValid(
            AmqpProtocolHeaderFW header)
        {
            return PROTOCOL_HEADER_SASL == header.buffer().getLong(header.offset(), BIG_ENDIAN);
        }

        private void cleanupNetwork(
            long traceId,
            long authorization)
        {
            cleanupStreams(traceId, authorization);

            doNetworkResetIfNecessary(traceId, authorization);
            doNetworkAbortIfNecessary(traceId, authorization);
        }

        private void cleanupStreams(
            long traceId,
            long authorization)
        {
            sessions.values().forEach(s -> s.cleanup(traceId, authorization));
        }

        private void doEncodeCloseAndEndIfNecessary(
            long traceId,
            long authorization,
            AmqpErrorType errorType,
            StringFW errorDescription)
        {
            if (!AmqpState.replyClosed(state))
            {
                doEncodeClose(traceId, authorization, errorType, errorDescription);
                doNetworkEnd(traceId, authorization);
                connectionState = connectionState.sentClose();
            }
        }

        private void doNetworkEndIfNecessary(
            long traceId,
            long authorization)
        {
            if (!AmqpState.replyClosed(state))
            {
                doNetworkEnd(traceId, authorization);
            }
        }

        private void doNetworkResetIfNecessary(
                long traceId,
                long authorization)
        {
            if (!AmqpState.initialClosed(state))
            {
                doNetworkReset(traceId, authorization);
            }
        }

        private void doNetworkAbortIfNecessary(
            long traceId,
            long authorization)
        {
            if (!AmqpState.replyClosed(state))
            {
                doNetworkAbort(traceId, authorization);
            }
        }

        private void doCancelCloseTimeoutIfNecessary()
        {
            if (closeTimeoutId != NO_CANCEL_ID)
            {
                signaler.cancel(closeTimeoutId);
                closeTimeoutId = NO_CANCEL_ID;
            }
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (replyBudgetIndex != NO_CREDITOR_INDEX)
            {
                creditor.release(replyBudgetIndex);
                replyBudgetIndex = NO_CREDITOR_INDEX;
            }
        }

        private void cleanupDecodeSlotIfNecessary()
        {
            if (decodeSlot != NO_SLOT)
            {
                bufferPool.release(decodeSlot);
                decodeSlot = NO_SLOT;
                decodeSlotOffset = 0;
            }
        }

        private void cleanupEncodeSlotIfNecessary()
        {
            if (encodeSlot != NO_SLOT)
            {
                bufferPool.release(encodeSlot);
                encodeSlot = NO_SLOT;
                encodeSlotOffset = 0;
                encodeSlotTraceId = 0;
            }
        }

        private void doSignalReadIdleTimeoutIfNecessary()
        {
            if (readIdleTimeout > 0)
            {
                readIdleTimeoutAt = currentTimeMillis() + readIdleTimeout;

                if (readIdleTimeoutId == NO_CANCEL_ID)
                {
                    readIdleTimeoutId = signaler.signalAt(readIdleTimeoutAt, routeId, replyId, READ_IDLE_SIGNAL_ID, 0);
                }
            }
        }

        private void doSignalWriteIdleTimeoutIfNecessary()
        {
            if (writeIdleTimeout > 0)
            {
                writeIdleTimeoutAt = currentTimeMillis() + writeIdleTimeout;

                if (writeIdleTimeoutId == NO_CANCEL_ID)
                {
                    writeIdleTimeoutId = signaler.signalAt(writeIdleTimeoutAt, routeId, replyId, WRITE_IDLE_SIGNAL_ID, 0);
                }
            }
        }

        private void doSignalCloseTimeout()
        {
            final long closeTimeoutAt = currentTimeMillis() + closeTimeout;

            assert closeTimeoutId == NO_CANCEL_ID;
            closeTimeoutId = signaler.signalAt(closeTimeoutAt, routeId, replyId, CLOSE_SIGNAL_ID, 0);
        }

        private final class AmqpSession
        {
            private final Long2ObjectHashMap<AmqpServerStream> links;
            private final int incomingChannel;

            private long deliveryId = NO_DELIVERY_ID;
            private long abortedDeliveryId = NO_DELIVERY_ID;
            private long remoteDeliveryId = NO_DELIVERY_ID;
            private int outgoingChannel;
            private long nextIncomingId;
            private int incomingWindow;
            private int nextOutgoingId;
            private int outgoingWindow;
            private int remoteIncomingWindow;
            private int remoteOutgoingWindow;

            private AmqpSessionState sessionState;

            private AmqpSession(
                int incomingChannel)
            {
                this.links = new Long2ObjectHashMap<>();
                this.incomingChannel = incomingChannel;
                this.nextOutgoingId++;
                this.sessionState = UNMAPPED;
            }

            private void outgoingChannel(
                int outgoingChannel)
            {
                this.outgoingChannel = outgoingChannel;
            }

            private void nextIncomingId(
                long nextOutgoingId)
            {
                this.nextIncomingId = nextOutgoingId;
            }

            private void incomingWindow(
                int incomingWindow)
            {
                this.incomingWindow = incomingWindow;
            }

            private void outgoingWindow(
                int outgoingWindow)
            {
                this.outgoingWindow = outgoingWindow;
            }

            private void remoteIncomingWindow(
                int incomingWindow)
            {
                this.remoteIncomingWindow = incomingWindow;
            }

            private void remoteOutgoingWindow(
                int outgoingWindow)
            {
                this.remoteOutgoingWindow = outgoingWindow;
            }

            private void onDecodeBegin(
                long traceId,
                long authorization)
            {
                doEncodeBegin(traceId, authorization);
            }

            private void onDecodeError(
                long traceId,
                long authorization,
                AmqpErrorType errorType)
            {
                doEncodeEndIfNecessary(traceId, authorization, errorType);
                cleanup(traceId, authorization);
            }

            private void onDecodeAttach(
                long traceId,
                long authorization,
                AmqpAttachFW attach)
            {
                final long handle = attach.handle();
                decode:
                if (links.containsKey(handle))
                {
                    if (links.get(handle).detachError != null)
                    {
                        onDecodeError(traceId, authorization, SESSION_ERRANT_LINK);
                        break decode;
                    }
                    AmqpServer.this.onDecodeError(traceId, authorization, NOT_ALLOWED, null);
                }
                else
                {
                    AmqpRole role = attach.role();

                    AmqpSourceFW source = attach.hasSource() ? attach.source() : null;
                    AmqpSourceListFW sourceList = source != null ? source.sourceList() : null;
                    StringFW sourceAddress = sourceList != null && sourceList.hasAddress() ? sourceList.address() : null;

                    AmqpTargetFW target = attach.hasTarget() ? attach.target() : null;
                    AmqpTargetListFW targetList = target != null ? target.targetList() : null;
                    StringFW targetAddress = targetList != null && targetList.hasAddress() ? targetList.address() : null;

                    final AmqpRouteConfig route;
                    switch (role)
                    {
                    case RECEIVER:
                        route = resolveRoute(routeId, authorization, sourceAddress, RECEIVE_ONLY);
                        break;
                    case SENDER:
                        route = resolveRoute(routeId, authorization, targetAddress, SEND_ONLY);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + role);
                    }

                    if (route != null)
                    {
                        String addressFrom = sourceAddress != null ? sourceAddress.asString() : null;
                        String addressTo = targetAddress != null ? targetAddress.asString() : null;

                        AmqpServerStream link = new AmqpServerStream(addressFrom, addressTo, role, route.id);
                        AmqpServerStream oldLink = links.put(handle, link);
                        assert oldLink == null;
                        if (sourceList != null)
                        {
                            link.sourceDurable = sourceList.hasDurable() && sourceList.durable().value() > 0;
                        }
                        link.onDecodeAttach(traceId, authorization, attach);
                    }
                    else
                    {
                        // TODO: reject
                    }
                }
            }

            private void onDecodeFlow(
                long traceId,
                long authorization,
                AmqpFlowFW flow)
            {
                int flowNextIncomingId = (int) flow.nextIncomingId();
                int flowIncomingWindow = (int) flow.incomingWindow();
                long flowNextOutgoingId = flow.nextOutgoingId();
                int flowOutgoingWindow = (int) flow.outgoingWindow();
                boolean hasHandle = flow.hasHandle();
                boolean echo = flow.echo() != 0;
                boolean hasLinkCredit = flow.hasLinkCredit();
                boolean hasDeliveryCount = flow.hasDeliveryCount();

                decode:
                if (hasHandle)
                {
                    long handle = flow.handle();
                    long deliveryCount = flow.deliveryCount();
                    int linkCredit = (int) flow.linkCredit();

                    AmqpServerStream attachedLink = links.get(handle);
                    if (attachedLink == null)
                    {
                        onDecodeError(traceId, authorization, SESSION_UNATTACHED_HANDLE);
                        break decode;
                    }

                    if (attachedLink.detachError != null)
                    {
                        break decode;
                    }

                    attachedLink.onDecodeFlow(traceId, authorization, deliveryCount, linkCredit, echo);
                }
                else if (hasLinkCredit || hasDeliveryCount)
                {
                    AmqpServer.this.onDecodeError(traceId, authorization, INVALID_FIELD, null);
                    return;
                }
                else if (echo)
                {
                    doEncodeFlow(traceId, authorization, outgoingChannel, nextOutgoingId, nextIncomingId, incomingWindow,
                        -1, -1, -1);
                }

                this.nextIncomingId = flowNextOutgoingId;
                this.remoteIncomingWindow = flowNextIncomingId + flowIncomingWindow - nextOutgoingId;
                this.remoteOutgoingWindow = flowOutgoingWindow;

                flushReplySharedBudget(traceId);
            }

            private void onDecodeTransfer(
                long traceId,
                long authorization,
                AmqpTransferFW transfer,
                int reserved,
                DirectBuffer buffer,
                int offset,
                int limit)
            {
                this.nextIncomingId = sequenceNext(nextIncomingId);
                this.remoteOutgoingWindow--;
                this.incomingWindow--;
                if (links.get(transfer.handle()).detachError != null)
                {
                    onDecodeError(traceId, authorization, SESSION_ERRANT_LINK);
                }
                else if (incomingWindow < 0)
                {
                    doEncodeEndIfNecessary(traceId, authorization, SESSION_WINDOW_VIOLATION);
                }
                else
                {
                    final BoundedOctetsFW deliveryTag = transfer.hasDeliveryTag() ? transfer.deliveryTag() : null;
                    final long messageFormat = transfer.hasMessageFormat() ? transfer.messageFormat() : 0;
                    boolean settled = transfer.hasSettled() && transfer.settled() == 1;
                    boolean resume = transfer.hasResume() && transfer.resume() == 1;
                    boolean aborted = transfer.hasAborted() && transfer.aborted() == 1;
                    boolean batchable = transfer.hasBatchable() && transfer.batchable() == 1;
                    boolean more = transfer.hasMore() && transfer.more() == 1;
                    AmqpServerStream link = links.get(transfer.handle());
                    link.onDecodeTransfer(traceId, authorization, reserved, deliveryTag, messageFormat, settled,
                        resume, aborted, batchable, more, buffer, offset, limit);
                }
            }

            private void onDecodeDetach(
                long traceId,
                long authorization,
                AmqpErrorType errorType,
                long handle)
            {
                AmqpServerStream link = links.get(handle);
                if (link != null)
                {
                    link.onDecodeDetach(traceId, authorization, errorType);
                }
            }

            private void doEncodeBegin(
                long traceId,
                long authorization)
            {
                AmqpServer.this.doEncodeBegin(traceId, authorization, incomingChannel, nextOutgoingId);
                sessionState = sessionState.sentBegin();
                assert sessionState != AmqpSessionState.ERROR;
            }

            private void doEncodeEndIfNecessary(
                long traceId,
                long authorization,
                AmqpErrorType errorType)
            {
                if (sessionState == MAPPED || sessionState == END_RCVD)
                {
                    AmqpServer.this.doEncodeEnd(traceId, authorization, outgoingChannel, errorType);
                    sessionState = sessionState.sentEnd();
                }
            }

            private void cleanup(
                long traceId,
                long authorization)
            {
                links.values().forEach(l -> l.cleanup(traceId, authorization));
                sessions.remove(incomingChannel);
                flushReplySharedBudget(traceId);
            }

            private class AmqpServerStream
            {
                private MessageConsumer application;
                private long newRouteId;
                private long initialId;
                private long replyId;

                private int state;
                private int capabilities;

                private boolean fragmented;
                private long remoteDeliveryCount;
                private long deliveryCount;
                private int remoteLinkCredit;
                private int linkCredit;

                private BudgetDebitor debitor;
                private long debitorIndex = NO_DEBITOR_INDEX;

                private long initialBudgetId;

                private long initialSeq;
                private long initialAck;
                private int initialMax;
                private int initialPad;

                private long replySeq;
                private long replyAck;
                private int replyMax;

                private String name;
                private long handle;
                private AmqpRole role;
                private StringFW addressFrom;
                private StringFW addressTo;
                private long decodeMaxMessageSize;
                private long encodeMaxMessageSize;

                private AmqpErrorType detachError;

                private AmqpBodyKind encodeBodyKind;
                private AmqpBodyKind decodeBodyKind;

                private AmqpSectionDecoder decoder;
                private int decodableBytes;

                private boolean sourceDurable;
                private boolean headerDurable;

                BoundedOctetsFW deliveryTag;
                long messageFormat;

                AmqpServerStream(
                    String addressFrom,
                    String addressTo,
                    AmqpRole role,
                    long resolvedId)
                {
                    this.addressFrom = new String8FW(addressFrom);
                    this.addressTo = new String8FW(addressTo);
                    this.role = role;
                    this.capabilities = 0;
                    this.newRouteId = resolvedId;
                    this.initialId = supplyInitialId.applyAsLong(newRouteId);
                    this.replyId = supplyReplyId.applyAsLong(initialId);
                    this.decodeMaxMessageSize = defaultMaxMessageSize;
                }

                private void onDecodeAttach(
                    long traceId,
                    long authorization,
                    AmqpAttachFW attach)
                {
                    this.name = attach.name().asString();
                    this.handle = attach.handle();
                    this.encodeMaxMessageSize = attach.hasMaxMessageSize() ? attach.maxMessageSize() : 0;

                    final AmqpCapabilities capability = amqpCapabilities(role);
                    final AmqpSenderSettleMode amqpSenderSettleMode = attach.sndSettleMode();
                    final AmqpReceiverSettleMode amqpReceiverSettleMode = attach.rcvSettleMode();

                    remoteDeliveryCount = attach.hasInitialDeliveryCount() ? attach.initialDeliveryCount() : 0;

                    doApplicationBeginIfNecessary(traceId, authorization, affinity, capability, amqpSenderSettleMode,
                        amqpReceiverSettleMode);
                }

                private void onDecodeFlow(
                    long traceId,
                    long authorization,
                    long decodeDeliveryCount,
                    int decodeLinkCredit,
                    boolean echo)
                {
                    if (echo)
                    {
                        doEncodeFlow(traceId, authorization, outgoingChannel, nextOutgoingId, nextIncomingId, incomingWindow,
                            handle, deliveryCount, remoteLinkCredit);
                    }
                    this.linkCredit = (int) (decodeDeliveryCount + decodeLinkCredit - remoteDeliveryCount);
                    this.remoteDeliveryCount = decodeDeliveryCount;
                    doApplicationWindow(traceId, authorization);
                }

                private void onDecodeTransfer(
                    long traceId,
                    long authorization,
                    int reserved,
                    BoundedOctetsFW deliveryTag,
                    long messageFormat,
                    boolean settled,
                    boolean resume,
                    boolean aborted,
                    boolean batchable,
                    boolean more,
                    DirectBuffer buffer,
                    int offset,
                    int limit)
                {
                    int flags = 0;
                    if (!fragmented)
                    {
                        flags |= FLAG_INIT;
                        if (more)
                        {
                            this.deliveryTag = deliveryTag;
                            this.messageFormat = messageFormat;
                        }
                    }
                    if (aborted)
                    {
                        flags = FLAG_INCOMPLETE;
                    }

                    if (!more && !aborted)
                    {
                        flags |= FLAG_FIN;
                        deliveryCount = sequenceNext(deliveryCount);
                    }

                    int transferFlags = 0;
                    transferFlags = settled ? settled(transferFlags) : transferFlags;
                    transferFlags = resume ? resume(transferFlags) : transferFlags;
                    transferFlags = aborted ? aborted(transferFlags) : transferFlags;
                    transferFlags = batchable ? batchable(transferFlags) : transferFlags;

                    OctetsFW payload = null;
                    Flyweight extension = EMPTY_OCTETS;
                    int size = 0;
                    decode:
                    if (!fragmented)
                    {
                        this.remoteLinkCredit--;
                        if (remoteLinkCredit < 0)
                        {
                            onDecodeError(traceId, authorization, LINK_TRANSFER_LIMIT_EXCEEDED);
                            break decode;
                        }

                        final AmqpDataExFW.Builder amqpDataEx = amqpDataExRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                            .typeId(amqpTypeId)
                            .deliveryTag(b -> b.bytes(deliveryTag.get(deliveryTagRO::tryWrap)))
                            .messageFormat(messageFormat)
                            .flags(transferFlags);

                        final OctetsFW messageFragment = amqpMessageDecodeHelper.decodeFragmentInit(this, buffer, offset, limit,
                            amqpDataEx);
                        size = messageFragment.sizeof();
                        if (size > 0)
                        {
                            payload = messageFragment;
                        }

                        extension = amqpDataEx
                            .bodyKind(b -> b.set(decodeBodyKind))
                            .deferred(decodableBytes)
                            .build();
                    }
                    else
                    {
                        OctetsFW messageFragment =  aborted ? EMPTY_OCTETS :
                            amqpMessageDecodeHelper.decodeFragment(this, buffer, offset, limit);
                        size = messageFragment.sizeof();
                        if (size >= 0)
                        {
                            payload = messageFragment;
                        }
                    }

                    if (!sourceDurable && headerDurable)
                    {
                        onDecodeError(traceId, authorization, PRECONDITION_FAILED);
                    }
                    else if (defaultMaxMessageSize > 0 && size > defaultMaxMessageSize)
                    {
                        onDecodeError(traceId, authorization, LINK_MESSAGE_SIZE_EXCEEDED);
                    }
                    else if (fragmented &&
                             (deliveryTag != null && !this.deliveryTag.equals(deliveryTag) ||
                              this.messageFormat != messageFormat))
                    {
                        AmqpServer.this.onDecodeError(traceId, authorization, INVALID_FIELD, null);
                    }
                    else
                    {
                        doApplicationData(traceId, authorization, flags, reserved, payload, extension);
                    }

                    this.fragmented = more;
                }

                private void onDecodeDetach(
                    long traceId,
                    long authorization,
                    AmqpErrorType errorType)
                {
                    doApplicationEndIfNecessary(traceId, authorization, EMPTY_OCTETS);
                }

                private void onDecodeError(
                    long traceId,
                    long authorization,
                    AmqpErrorType errorType)
                {
                    doEncodeDetach(traceId, authorization, errorType, outgoingChannel, handle);
                    this.detachError = errorType;
                    doApplicationAbortIfNecessary(traceId, authorization);
                }

                private void doApplicationBeginIfNecessary(
                    long traceId,
                    long authorization,
                    long affinity,
                    AmqpCapabilities capability,
                    AmqpSenderSettleMode senderSettleMode,
                    AmqpReceiverSettleMode receiverSettleMode)
                {
                    final int newCapabilities = capabilities | capability.value();
                    if (!AmqpState.initialOpening(state))
                    {
                        this.capabilities = newCapabilities;
                        doApplicationBegin(traceId, authorization, affinity, senderSettleMode, receiverSettleMode);
                    }
                }

                private void doApplicationBegin(
                    long traceId,
                    long authorization,
                    long affinity,
                    AmqpSenderSettleMode senderSettleMode,
                    AmqpReceiverSettleMode receiverSettleMode)
                {
                    initialSeq = AmqpServer.this.initialSeq;
                    initialAck = initialSeq;

                    assert state == 0;
                    state = AmqpState.openingInitial(state);

                    StringFW address = null;
                    switch (role)
                    {
                    case RECEIVER:
                        address = addressFrom;
                        break;
                    case SENDER:
                        address = addressTo;
                    }
                    final AmqpBeginExFW beginEx = amqpBeginExRW.wrap(extraBuffer, 0, extraBuffer.capacity())
                        .typeId(amqpTypeId)
                        .address((String8FW) address)
                        .capabilities(r -> r.set(AmqpCapabilities.valueOf(capabilities)))
                        .senderSettleMode(s -> s.set(amqpSenderSettleMode(senderSettleMode)))
                        .receiverSettleMode(r -> r.set(amqpReceiverSettleMode(receiverSettleMode)))
                        .build();

                    application = newStream(this::onApplication, newRouteId, initialId, initialSeq, initialAck, initialMax,
                            traceId, authorization, affinity, beginEx);
                }

                private void doApplicationData(
                    long traceId,
                    long authorization,
                    int flags,
                    int reserved,
                    OctetsFW payload,
                    Flyweight extension)
                {
                    assert AmqpState.initialOpening(state);

                    final int length = payload != null ? payload.sizeof() : 0;
                    assert reserved >= length + initialPad : String.format("%d >= %d", reserved, length + initialPad);

                    doData(application, newRouteId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, flags,
                        initialBudgetId, reserved, payload, extension);

                    initialSeq += reserved;
                    assert initialSeq <= initialAck + initialMax;
                }

                private void doApplicationAbort(
                    long traceId,
                    long authorization,
                    Flyweight extension)
                {
                    setInitialClosed();

                    doAbort(application, newRouteId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                }

                private void doApplicationAbortIfNecessary(
                    long traceId,
                    long authorization)
                {
                    if (!AmqpState.initialClosed(state))
                    {
                        doApplicationAbort(traceId, authorization, EMPTY_OCTETS);
                    }
                }

                private void setInitialClosed()
                {
                    assert !AmqpState.initialClosed(state);

                    state = AmqpState.closeInitial(state);

                    if (debitorIndex != NO_DEBITOR_INDEX)
                    {
                        debitor.release(debitorIndex, initialId);
                        debitorIndex = NO_DEBITOR_INDEX;
                    }

                    if (AmqpState.closed(state))
                    {
                        capabilities = 0;
                        links.remove(handle);
                    }
                }

                private void onApplication(
                    int msgTypeId,
                    DirectBuffer buffer,
                    int index,
                    int length)
                {
                    switch (msgTypeId)
                    {
                    case BeginFW.TYPE_ID:
                        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                        onApplicationBegin(begin);
                        break;
                    case DataFW.TYPE_ID:
                        final DataFW data = dataRO.wrap(buffer, index, index + length);
                        onApplicationData(data);
                        break;
                    case EndFW.TYPE_ID:
                        final EndFW end = endRO.wrap(buffer, index, index + length);
                        onApplicationEnd(end);
                        break;
                    case AbortFW.TYPE_ID:
                        final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                        onApplicationAbort(abort);
                        break;
                    case FlushFW.TYPE_ID:
                        final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                        onApplicationFlush(flush);
                        break;
                    case WindowFW.TYPE_ID:
                        final WindowFW window = windowRO.wrap(buffer, index, index + length);
                        onApplicationWindow(window);
                        break;
                    case ResetFW.TYPE_ID:
                        final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                        onApplicationReset(reset);
                        break;
                    case SignalFW.TYPE_ID:
                        final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                        onApplicationSignal(signal);
                        break;
                    }
                }

                private void onApplicationWindow(
                    WindowFW window)
                {
                    final long sequence = window.sequence();
                    final long acknowledge = window.acknowledge();
                    final long traceId = window.traceId();
                    final long authorization = window.authorization();
                    final long budgetId = window.budgetId();
                    final int maximum = window.maximum();
                    final int padding = window.padding();

                    this.state = AmqpState.openInitial(state);
                    this.initialBudgetId = budgetId;

                    assert acknowledge <= sequence;
                    assert sequence <= initialSeq;
                    assert acknowledge >= initialAck;
                    assert maximum + acknowledge >= initialMax + initialAck;

                    initialAck = acknowledge;
                    initialMax = maximum;
                    initialPad = padding;

                    assert initialAck <= initialSeq;

                    if (budgetId != 0L && debitorIndex == NO_DEBITOR_INDEX)
                    {
                        debitor = supplyDebitor.apply(budgetId);
                        debitorIndex = debitor.acquire(budgetId, initialId, AmqpServer.this::decodeNetworkIfNecessary);
                    }

                    flushInitialWindow(traceId, authorization);

                    if (AmqpState.initialClosing(state) && !AmqpState.initialClosed(state))
                    {
                        doApplicationEnd(traceId, authorization, EMPTY_OCTETS);
                    }
                }

                private void flushInitialWindow(
                    long traceId,
                    long authorization)
                {
                    if (AmqpState.replyOpened(state) && role == SENDER)
                    {
                        this.remoteLinkCredit = (int) (Math.min(bufferPool.slotCapacity(), initialMax) /
                                                       Math.min(bufferPool.slotCapacity(), decodeMaxFrameSize));
                        remoteLinkCreditSum.value = 0;
                        links.values().forEach(l -> remoteLinkCreditSum.value += l.remoteLinkCredit);
                        incomingWindow = remoteLinkCreditSum.value;

                        doEncodeFlow(traceId, authorization, outgoingChannel, nextOutgoingId, nextIncomingId, incomingWindow,
                            handle, deliveryCount, remoteLinkCredit);
                    }
                }

                private void onApplicationReset(
                    ResetFW reset)
                {
                    final long traceId = reset.traceId();
                    final long authorization = reset.authorization();

                    if (!AmqpState.replyOpened(state))
                    {
                        AmqpRole amqpRole = role == RECEIVER ? SENDER : RECEIVER;
                        if (amqpRole == RECEIVER)
                        {
                            doEncodeAttach(traceId, authorization, name, outgoingChannel, handle, amqpRole, MIXED, FIRST,
                                addressFrom, null, deliveryCount, decodeMaxMessageSize);
                        }
                        else
                        {
                            doEncodeAttach(traceId, authorization, name, outgoingChannel, handle, amqpRole, MIXED, FIRST,
                                null, addressTo, deliveryCount, decodeMaxMessageSize);
                        }
                    }

                    setInitialClosed();

                    onDecodeError(traceId, authorization, LINK_DETACH_FORCED);
                }

                private void onApplicationSignal(
                    SignalFW signal)
                {
                    final long signalId = signal.signalId();
                    // TODO
                }

                private void onApplicationBegin(
                    BeginFW begin)
                {
                    state = AmqpState.openReply(state);

                    final long sequence = begin.sequence();
                    final long acknowledge = begin.acknowledge();
                    final long traceId = begin.traceId();
                    final long authorization = begin.authorization();

                    assert acknowledge <= sequence;
                    assert sequence >= replySeq;
                    assert acknowledge <= replyAck;

                    replySeq = sequence;
                    replyAck = acknowledge;

                    assert replyAck <= replySeq;

                    AmqpRole amqpRole = role == RECEIVER ? SENDER : RECEIVER;
                    AmqpSenderSettleMode amqpSenderSettleMode = MIXED;
                    AmqpReceiverSettleMode amqpReceiverSettleMode = FIRST;
                    deliveryCount = remoteDeliveryCount;

                    final AmqpBeginExFW amqpBeginEx = begin.extension().get(amqpBeginExRO::tryWrap);
                    if (amqpBeginEx != null)
                    {
                        amqpSenderSettleMode = amqpSenderSettleMode(amqpBeginEx.senderSettleMode().get());
                        amqpReceiverSettleMode = amqpReceiverSettleMode(amqpBeginEx.receiverSettleMode().get());
                    }

                    doEncodeAttach(traceId, authorization, name, outgoingChannel, handle, amqpRole, amqpSenderSettleMode,
                        amqpReceiverSettleMode, addressFrom, addressTo, deliveryCount, decodeMaxMessageSize);

                    flushInitialWindow(traceId, authorization);
                    doApplicationWindow(traceId, authorization);
                }

                private void onApplicationData(
                    DataFW data)
                {
                    final long sequence = data.sequence();
                    final long acknowledge = data.acknowledge();
                    final long traceId = data.traceId();
                    final int reserved = data.reserved();
                    final long authorization = data.authorization();
                    final int flags = data.flags();
                    final OctetsFW extension = data.extension();
                    final OctetsFW payload = data.payload();

                    assert acknowledge <= sequence;
                    assert sequence >= replySeq;
                    assert acknowledge <= replyAck;

                    replySeq = sequence + reserved;

                    assert replyAck <= replySeq;

                    replySharedBudget -= reserved;

                    if (replySeq > replyAck + replyMax)
                    {
                        doApplicationReset(traceId, authorization);
                        doNetworkAbort(traceId, authorization);
                    }

                    if ((flags & FLAG_INIT_INCOMPLETE) == FLAG_INIT_INCOMPLETE)
                    {
                        flushReplySharedBudget(traceId);
                    }
                    else
                    {
                        nextOutgoingId++;
                        outgoingWindow--;

                        if ((flags & FLAG_INIT) == FLAG_INIT)
                        {
                            deliveryId++;
                            onApplicationDataInit(traceId, reserved, authorization, flags, extension, payload);
                        }
                        else if (deliveryId != abortedDeliveryId)
                        {
                            onApplicationDataContOrFin(traceId, reserved, authorization, flags, payload);
                        }
                    }
                }

                private void onApplicationDataInit(
                    long traceId,
                    int reserved,
                    long authorization,
                    int flags,
                    OctetsFW extension,
                    OctetsFW payload)
                {
                    final AmqpDataExFW dataEx = extension.get(amqpDataExRO::tryWrap);
                    assert dataEx != null;

                    final int deferred = dataEx.deferred();
                    final boolean more = (flags & FLAG_FIN) == 0;

                    final AmqpBodyKind bodyKind = dataEx.bodyKind().get();
                    final OctetsFW deliveryTagBytes = dataEx.deliveryTag().bytes();
                    final BoundedOctetsFW deliveryTag =
                            amqpBinaryRW.wrap(stringBuffer, 0, stringBuffer.capacity())
                                .set(deliveryTagBytes.value(), 0, deliveryTagBytes.sizeof())
                                .build()
                                .get();
                    final long messageFormat = dataEx.messageFormat();
                    final boolean settled = isSettled(dataEx.flags());

                    final OctetsFW messageFragment = amqpMessageHelper.encodeFragmentInit(
                            deferred, extension, payload);

                    this.encodeBodyKind = bodyKind;
                    final int performativeSize = transferType.sizeof();

                    final AmqpTransferFW.Builder transferBuilder = amqpTransferRW
                            .wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                            .handle(handle)
                            .deliveryId(deliveryId)
                            .deliveryTag(deliveryTag)
                            .messageFormat(messageFormat)
                            .settled(settled ? 1 : 0);

                    if (more)
                    {
                        transferBuilder.more(1);
                    }

                    final DirectBuffer fragmentBuffer = messageFragment.buffer();
                    final int fragmentOffset = messageFragment.offset();
                    final int fragmentLimit = messageFragment.limit();
                    int fragmentSize = fragmentLimit - fragmentOffset;

                    if (encodeMaxMessageSize > 0 && fragmentSize + deferred > encodeMaxMessageSize)
                    {
                        transferBuilder.aborted(1);
                        abortedDeliveryId = deliveryId;
                        fragmentSize = 0;
                    }

                    final AmqpTransferFW transfer = transferBuilder.build();
                    final int frameSize = FRAME_HEADER_SIZE + performativeSize + transfer.sizeof() + fragmentSize;

                    if (frameSize <= encodeMaxFrameSize)
                    {
                        doEncodeTransfer(traceId, authorization, outgoingChannel, transfer,
                                fragmentBuffer, fragmentOffset, fragmentSize);
                    }
                    else
                    {
                        AmqpTransferFW transferInit = amqpTransferRW
                                .wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                                .handle(handle)
                                .deliveryId(deliveryId)
                                .deliveryTag(deliveryTag)
                                .messageFormat(messageFormat)
                                .settled(settled ? 1 : 0)
                                .more(1)
                                .build();

                        int fragmentSizeInit = encodeMaxFrameSize - FRAME_HEADER_SIZE - performativeSize - transferInit.sizeof();
                        int fragmentProgress = fragmentOffset;

                        doEncodeTransfer(traceId, authorization, outgoingChannel,
                                transferInit, fragmentBuffer, fragmentProgress, fragmentSizeInit);
                        fragmentProgress += fragmentSizeInit;

                        doEncodeTransferFragments(
                            traceId, authorization, outgoingChannel, handle, more,
                            fragmentBuffer, fragmentProgress, fragmentLimit);
                    }
                }

                private void onApplicationDataContOrFin(
                    long traceId,
                    int reserved,
                    long authorization,
                    int flags,
                    OctetsFW payload)
                {
                    final boolean aborted = (flags & FLAG_INCOMPLETE) == FLAG_INCOMPLETE;
                    final boolean more = (flags & FLAG_FIN) == 0 && !aborted;

                    OctetsFW messageFragment = aborted ? EMPTY_OCTETS : amqpMessageHelper.encodeFragment(encodeBodyKind, payload);

                    final int performativeSize = transferType.sizeof();
                    final AmqpTransferFW.Builder transferBuilder = amqpTransferRW
                        .wrap(frameBuffer, FRAME_HEADER_SIZE + performativeSize, frameBuffer.capacity())
                        .handle(handle);

                    if (more)
                    {
                        transferBuilder.more(1);
                    }
                    if (aborted)
                    {
                        transferBuilder.aborted(1);
                    }

                    final DirectBuffer fragmentBuffer = messageFragment.buffer();
                    final int fragmentOffset = messageFragment.offset();
                    final int fragmentLimit = messageFragment.limit();
                    final int fragmentSize = fragmentLimit - fragmentOffset;
                    final AmqpTransferFW transfer = transferBuilder.build();
                    final int frameSize = FRAME_HEADER_SIZE + performativeSize + transfer.sizeof() + fragmentSize;

                    if (frameSize <= encodeMaxFrameSize)
                    {
                        doEncodeTransfer(traceId, authorization, outgoingChannel, transfer,
                            fragmentBuffer, fragmentOffset, fragmentSize);
                    }
                    else
                    {
                        doEncodeTransferFragments(
                            traceId, authorization, outgoingChannel, handle, more,
                            fragmentBuffer, fragmentOffset, fragmentLimit);
                    }
                }

                private void onApplicationFlush(
                    FlushFW flush)
                {
                    final long sequence = flush.sequence();
                    final long acknowledge = flush.acknowledge();
                    final long traceId = flush.traceId();
                    final long authorization = flush.authorization();
                    final long budgetId = flush.budgetId();
                    final int reserved = flush.reserved();
                    final OctetsFW extension = flush.extension();

                    assert acknowledge <= sequence;
                    assert sequence >= replySeq;
                    assert acknowledge <= replyAck;

                    replySeq = sequence;

                    assert replyAck <= replySeq;

                    if (replySeq > replyAck + replyMax)
                    {
                        doApplicationReset(traceId, authorization);
                        doNetworkAbort(traceId, authorization);
                    }
                    else
                    {
                        doNetworkFlush(traceId, authorization, budgetId, reserved, extension);
                    }
                }

                private void onApplicationEnd(
                    EndFW end)
                {
                    setReplyClosed();

                    final long traceId = end.traceId();
                    final long authorization = end.authorization();

                    doEncodeDetach(traceId, authorization, null, decodeChannel, handle);
                    cleanup(traceId, authorization);
                }

                private void onApplicationAbort(
                    AbortFW abort)
                {
                    setReplyClosed();

                    final long traceId = abort.traceId();
                    final long authorization = abort.authorization();

                    cleanup(traceId, authorization);
                }

                private void doApplicationEnd(
                    long traceId,
                    long authorization,
                    Flyweight extension)
                {
                    setInitialClosed();
                    capabilities = 0;
                    links.remove(handle);

                    doEnd(application, newRouteId, initialId, initialSeq, initialAck, initialMax, traceId, authorization,
                        extension);
                }

                private void doApplicationEndIfNecessary(
                    long traceId,
                    long authorization,
                    Flyweight extension)
                {
                    if (!AmqpState.initialClosed(state))
                    {
                        doApplicationEnd(traceId, authorization, extension);
                    }
                }

                private void doApplicationWindow(
                    long traceId,
                    long authorization)
                {
                    if (AmqpState.replyOpened(state))
                    {
                        final int maxFrameSize = encodeMaxFrameSize;
                        final int slotCapacity = bufferPool.slotCapacity();
                        final int maxFrameCount = (slotCapacity + maxFrameSize - 1) / maxFrameSize;
                        final int padding = PAYLOAD_HEADER_SIZE + (TRANSFER_HEADER_SIZE * maxFrameCount);
                        final int replyBudget = linkCredit * encodeMaxFrameSize;
                        final int replyPendingAck = Math.max(replyMax - replyBudget, 0);
                        final int replyAckMax = (int)(replySeq - replyPendingAck);
                        final int replyBudgetMax = replyBudget + replyPendingAck;
                        if (replyAckMax > replyAck || replyBudgetMax != replyMax)
                        {
                            replyAck = replyAckMax;
                            assert replyAck <= replySeq;
                            replyMax = replyBudgetMax;
                            assert replyMax >= 0;
                            doWindow(application, newRouteId, replyId, replySeq, replyAck, replyMax, traceId, authorization,
                                replySharedBudgetId, padding, maxFrameSize);
                        }
                    }
                }

                private void doApplicationFlush(
                    long traceId,
                    long authorization,
                    long budgetId,
                    int reserved,
                    OctetsFW extension)
                {
                    doFlush(application, routeId, initialId, initialSeq, initialAck, initialMax, traceId, authorization, budgetId,
                        reserved, extension);
                }

                private void doApplicationReset(
                    long traceId,
                    long authorization)
                {
                    setReplyClosed();

                    doReset(application, newRouteId, replyId, replySeq, replyAck, replyMax, traceId, authorization, EMPTY_OCTETS);
                }

                private void doApplicationResetIfNecessary(
                    long traceId,
                    long authorization)
                {
                    if (!AmqpState.replyClosed(state))
                    {
                        doApplicationReset(traceId, authorization);
                    }
                }

                private void setReplyClosed()
                {
                    assert !AmqpState.replyClosed(state);

                    state = AmqpState.closeReply(state);

                    if (AmqpState.closed(state))
                    {
                        capabilities = 0;
                        links.remove(handle);
                    }
                }

                private void cleanup(
                    long traceId,
                    long authorization)
                {
                    doApplicationAbortIfNecessary(traceId, authorization);
                    doApplicationResetIfNecessary(traceId, authorization);
                    doCancelReadIdleTimeoutIfNecessary();
                    doCancelWriteIdleTimeoutIfNecessary();
                }

                private void doCancelReadIdleTimeoutIfNecessary()
                {
                    if (readIdleTimeoutId != NO_CANCEL_ID)
                    {
                        signaler.cancel(readIdleTimeoutId);
                        readIdleTimeoutId = NO_CANCEL_ID;
                    }
                }

                private void doCancelWriteIdleTimeoutIfNecessary()
                {
                    if (writeIdleTimeoutId != NO_CANCEL_ID)
                    {
                        signaler.cancel(writeIdleTimeoutId);
                        writeIdleTimeoutId = NO_CANCEL_ID;
                    }
                }
            }
        }
    }

    private final class AmqpMessageEncoder
    {
        private final AmqpMapFW.Builder<AmqpValueFW, AmqpValueFW, AmqpValueFW.Builder, AmqpValueFW.Builder> annotationsRW =
            new AmqpMapFW.Builder<>(
                new AmqpValueFW(),
                new AmqpValueFW(),
                new AmqpValueFW.Builder(),
                new AmqpValueFW.Builder());

        private final AmqpApplicationPropertiesFW.Builder<AmqpSimpleTypeFW, AmqpSimpleTypeFW.Builder> applicationPropertiesRW =
            new AmqpApplicationPropertiesFW.Builder<>(
                new AmqpStringFW(),
                new AmqpSimpleTypeFW(),
                new AmqpStringFW.Builder(),
                new AmqpSimpleTypeFW.Builder());

        private AmqpSectionEncoder sectionEncoder;
        private int encodableBytes;

        private OctetsFW encodeFragmentInit(
            int deferred,
            OctetsFW extension,
            OctetsFW payload)
        {
            messageFragmentRW.wrap(extraBuffer, 0, extraBuffer.capacity());

            final AmqpDataExFW dataEx = extension.get(amqpDataExRO::tryWrap);
            assert dataEx != null;
            final AmqpBodyKind bodyKind = dataEx.bodyKind().get();

            encodeMessageProperties(dataEx.properties());
            encodeMessageAnnotations(dataEx.annotations());
            encodeApplicationProperties(dataEx.applicationProperties());

            if (payload == null)
            {
                return encodeSectionValueNull();
            }
            else
            {
                this.sectionEncoder = lookupBodyEncoder(bodyKind);
                return encodeSections(deferred, payload);
            }
        }

        private OctetsFW encodeFragment(
            AmqpBodyKind bodyKind,
            OctetsFW payload)
        {
            assert bodyKind != null;

            messageFragmentRW.wrap(extraBuffer, 0, extraBuffer.capacity());

            this.sectionEncoder = lookupBodyBytesEncoder(bodyKind);

            return encodeSections(0, payload);
        }

        private OctetsFW encodeSections(
            int deferred,
            OctetsFW payload)
        {
            AmqpSectionEncoder previous = null;
            final DirectBuffer buffer = payload.buffer();
            int progress = payload.offset();
            final int limit = payload.limit();

            while (progress <= limit && previous != sectionEncoder)
            {
                previous = sectionEncoder;
                progress = sectionEncoder.encode(deferred, buffer, progress, limit);
            }

            assert progress == limit; // more

            return messageFragmentRW.build();
        }

        private AmqpSectionEncoder lookupBodyBytesEncoder(
            AmqpBodyKind bodyKind)
        {
            AmqpSectionEncoder encoder;
            switch (bodyKind)
            {
            case DATA:
                encoder = this::encodeSectionDataBytes;
                break;
            case SEQUENCE:
                encoder = this::encodeSectionSequenceBytes;
                break;
            case VALUE:
            case VALUE_STRING8:
            case VALUE_STRING32:
            case VALUE_BINARY8:
            case VALUE_BINARY32:
            case VALUE_SYMBOL8:
            case VALUE_SYMBOL32:
                encoder = this::encodeSectionValueBytes;
                break;
            default:
                throw new IllegalArgumentException("Unexpected body kind: " + bodyKind);
            }
            return encoder;
        }

        private AmqpSectionEncoder lookupBodyEncoder(
            final AmqpBodyKind bodyKind)
        {
            AmqpSectionEncoder encoder;

            switch (bodyKind)
            {
            case DATA:
                encoder = this::encodeSectionData;
                break;
            case SEQUENCE:
                encoder = this::encodeSectionSequence;
                break;
            case VALUE:
                encoder = this::encodeSectionValue;
                break;
            case VALUE_STRING8:
                encoder = this::encodeSectionValueString8;
                break;
            case VALUE_STRING32:
                encoder = this::encodeSectionValueString32;
                break;
            case VALUE_BINARY8:
                encoder = this::encodeSectionValueBinary8;
                break;
            case VALUE_BINARY32:
                encoder = this::encodeSectionValueBinary32;
                break;
            case VALUE_SYMBOL8:
                encoder = this::encodeSectionValueSymbol8;
                break;
            case VALUE_SYMBOL32:
                encoder = this::encodeSectionValueSymbol32;
                break;
            default:
                throw new IllegalArgumentException("Unexpected body kind: " + bodyKind);
            }

            return encoder;
        }

        private void encodeMessageProperties(
            AmqpPropertiesFW properties)
        {
            if (properties.fieldCount() > 0)
            {
                AmqpDescribedTypeFW type = messagePropertiesSectionType;
                messageFragmentRW.put(type.buffer(), type.offset(), type.sizeof());
                messageFragmentRW.put((b, o, l) ->
                {
                    AmqpMessagePropertiesFW.Builder amqpProperties = amqpPropertiesRW.wrap(b, o, l);
                    if (properties.hasMessageId())
                    {
                        amqpProperties.messageId(properties.messageId().stringtype());
                    }
                    if (properties.hasUserId())
                    {
                        final BoundedOctetsFW userId = amqpBinaryRW.wrap(stringBuffer, 0, stringBuffer.capacity())
                            .set(properties.userId().bytes().value(), 0, properties.userId().length())
                            .build()
                            .get();
                        amqpProperties.userId(userId);
                    }
                    if (properties.hasTo())
                    {
                        amqpProperties.to(properties.to());
                    }
                    if (properties.hasSubject())
                    {
                        amqpProperties.subject(properties.subject());
                    }
                    if (properties.hasReplyTo())
                    {
                        amqpProperties.replyTo(properties.replyTo());
                    }
                    if (properties.hasCorrelationId())
                    {
                        amqpProperties.correlationId(properties.correlationId().stringtype());
                    }
                    if (properties.hasContentType())
                    {
                        amqpProperties.contentType(properties.contentType());
                    }
                    if (properties.hasContentEncoding())
                    {
                        amqpProperties.contentEncoding(properties.contentEncoding());
                    }
                    if (properties.hasAbsoluteExpiryTime())
                    {
                        amqpProperties.absoluteExpiryTime(properties.absoluteExpiryTime());
                    }
                    if (properties.hasCreationTime())
                    {
                        amqpProperties.creationTime(properties.creationTime());
                    }
                    if (properties.hasGroupId())
                    {
                        amqpProperties.groupId(properties.groupId());
                    }
                    if (properties.hasGroupSequence())
                    {
                        amqpProperties.groupSequence(properties.groupSequence());
                    }
                    if (properties.hasReplyToGroupId())
                    {
                        amqpProperties.replyToGroupId(properties.replyToGroupId());
                    }
                    return amqpProperties.build().sizeof();
                });
            }
        }

        private void encodeMessageAnnotations(
            Array32FW<AmqpAnnotationFW> value)
        {
            if (value.fieldCount() > 0)
            {
                AmqpDescribedTypeFW type = messageAnnotationsSectionType;
                messageFragmentRW.put(type.buffer(), type.offset(), type.sizeof());
                messageFragmentRW.put((b, o, l) ->
                {
                    annotationsRW.wrap(b, o, l);
                    value.forEach(this::encodeMessageAnnotation);
                    return annotationsRW.build().sizeof();
                });
            }
        }

        private void encodeMessageAnnotation(
            AmqpAnnotationFW item)
        {
            final AmqpAnnotationKeyFW key = item.key();
            final OctetsFW valueBytes = item.value().bytes();
            final AmqpValueFW value = valueBytes.get(amqpValueRO::wrap);

            switch (key.kind())
            {
            case KIND_ID:
                AmqpULongFW id = amqpULongRW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .set(key.id())
                    .build();
                annotationsRW.entry(
                    k -> k.setAsAmqpULong(id),
                    v -> v.set(value));
                break;
            case KIND_NAME:
                AmqpSymbolFW name = amqpSymbolRW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .set(key.name())
                    .build();
                annotationsRW.entry(
                    k -> k.setAsAmqpSymbol(name),
                    v -> v.set(value));
                break;
            }
        }

        private void encodeApplicationProperties(
            Array32FW<AmqpApplicationPropertyFW> value)
        {
            if (value.fieldCount() > 0)
            {
                AmqpDescribedTypeFW type = applicationPropertiesSectionType;
                messageFragmentRW.put(type.buffer(), type.offset(), type.sizeof());
                messageFragmentRW.put((b, o, l) ->
                {
                    applicationPropertiesRW.wrap(b, o, l);
                    value.forEach(this::encodeApplicationProperty);
                    return applicationPropertiesRW.build().sizeof();
                });
            }
        }

        private void encodeApplicationProperty(
            AmqpApplicationPropertyFW item)
        {
            final OctetsFW bytes = item.value().bytes();
            DirectBuffer buffer = bytes.buffer();
            int offset = bytes.offset();
            int limit = bytes.limit();

            StringFW key =  item.key();
            AmqpSimpleTypeFW value = amqpSimpleTypeRO.tryWrap(buffer, offset, limit);

            applicationPropertiesRW.entry(k -> k.set(key), v -> v.set(value));
        }

        private int encodeSectionData(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            AmqpType constructorByte = AmqpType.valueOf(buffer.getByte(offset) & 0xFF);
            if (constructorByte != null)
            {
                messageFragmentRW
                    .put(dataSectionType.buffer(), dataSectionType.offset(), dataSectionType.sizeof())
                    .put(buffer, progress, Byte.BYTES);

                progress++;
                switch (constructorByte)
                {
                case BINARY1:
                    this.encodableBytes = buffer.getByte(progress);
                    messageFragmentRW.put(buffer, progress, Byte.BYTES);
                    progress += Byte.BYTES;
                    break;
                case BINARY4:
                    this.encodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                    messageFragmentRW.put(buffer, progress, Integer.BYTES);
                    progress += Integer.BYTES;
                    break;
                }
                this.sectionEncoder = this::encodeSectionDataBytes;
            }
            return progress;
        }

        private int encodeSectionDataBytes(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            int totalSize = limit - offset;
            int size = Math.min(encodableBytes, totalSize);
            messageFragmentRW.put(buffer, offset, size);
            this.encodableBytes = totalSize - size;
            progress += size;
            if (encodableBytes > 0)
            {
                this.sectionEncoder = this::encodeSectionData;
            }
            return progress;
        }

        private int encodeSectionSequence(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            AmqpType constructorByte = AmqpType.valueOf(buffer.getByte(offset) & 0xFF);
            if (constructorByte != null)
            {
                messageFragmentRW.put(sequenceSectionType.buffer(), sequenceSectionType.offset(), sequenceSectionType.sizeof())
                    .put(buffer, progress, Byte.BYTES);
                progress++;
                switch (constructorByte)
                {
                case LIST1:
                    this.encodableBytes = buffer.getByte(progress);
                    messageFragmentRW.put(buffer, progress, Byte.BYTES);
                    progress += Byte.BYTES;
                    break;
                case LIST4:
                    this.encodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                    messageFragmentRW.put(buffer, progress, Integer.BYTES);
                    progress += Integer.BYTES;
                    break;
                }
                this.sectionEncoder = this::encodeSectionSequenceBytes;
            }
            return progress;
        }

        private int encodeSectionSequenceBytes(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            int totalSize = limit - offset;
            int size = Math.min(encodableBytes, totalSize);
            messageFragmentRW.put(buffer, offset, size);
            this.encodableBytes = totalSize - size;
            progress += size;
            if (encodableBytes > 0)
            {
                this.sectionEncoder = this::encodeSectionSequence;
            }
            return progress;
        }

        private int encodeSectionValue(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            int constructor = buffer.getByte(offset) & 0xF0;

            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof())
                .put(buffer, progress, Byte.BYTES);
            progress++;

            switch (constructor)
            {
            case 0x40:
                this.encodableBytes = 0;
                break;
            case 0x50:
                this.encodableBytes = Byte.BYTES;
                break;
            case 0x60:
                this.encodableBytes = Short.BYTES;
                break;
            case 0x70:
                this.encodableBytes = Integer.BYTES;
                break;
            case 0x80:
                this.encodableBytes = Long.BYTES;
                break;
            case 0x90:
                this.encodableBytes = Long.BYTES + Long.BYTES;
                break;
            case 0xc0:
            case 0xe0:
                this.encodableBytes = buffer.getByte(progress);
                messageFragmentRW.put(buffer, progress, Byte.BYTES);
                progress += Byte.BYTES;
                break;
            case 0xd0:
            case 0xf0:
                this.encodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                messageFragmentRW.put(buffer, progress, Integer.BYTES);
                progress += Integer.BYTES;
                break;
            }

            this.sectionEncoder = this::encodeSectionValueBytes;

            return progress;
        }

        private OctetsFW encodeSectionValueNull()
        {
            return messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof())
                .put(nullConstructor)
                .build();
        }

        private int encodeSectionValueString8(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength8FW bodyHeader =
                amqpVariableLength8RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .constructor(c -> c.set(STRING1))
                    .length(length)
                    .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueString32(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength32FW bodyHeader =
                amqpVariableLength32RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .constructor(c -> c.set(STRING4))
                    .length(length)
                    .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueBinary8(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength8FW bodyHeader =
                amqpVariableLength8RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .constructor(c -> c.set(BINARY1))
                    .length(length)
                    .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueBinary32(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength32FW bodyHeader =
                amqpVariableLength32RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                .constructor(c -> c.set(BINARY4))
                .length(length)
                .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueSymbol8(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength8FW bodyHeader =
                amqpVariableLength8RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .constructor(c -> c.set(AmqpType.SYMBOL1))
                    .length(length)
                    .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueSymbol32(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            this.encodableBytes = limit - offset;
            messageFragmentRW.put(valueSectionType.buffer(), valueSectionType.offset(), valueSectionType.sizeof());

            int length = deferred == 0 ? encodableBytes : encodableBytes + deferred;
            AmqpVariableLength32FW bodyHeader =
                amqpVariableLength32RW.wrap(valueBuffer, 0, valueBuffer.capacity())
                    .constructor(c -> c.set(AmqpType.SYMBOL4))
                    .length(length)
                    .build();
            int bodyHeaderSize = bodyHeader.sizeof();
            messageFragmentRW.put(bodyHeader.buffer(), bodyHeader.offset(), bodyHeaderSize);

            this.sectionEncoder = this::encodeSectionValueBytes;
            return offset;
        }

        private int encodeSectionValueBytes(
            int deferred,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            int size = limit - offset;
            messageFragmentRW.put(buffer, offset, size);
            progress += size;
            return progress;
        }
    }

    private final class AmqpMessageDecoder
    {
        private int decodeOffset;

        private OctetsFW decodeFragmentInit(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit,
            AmqpDataExFW.Builder amqpDataEx)
        {
            stream.decodeBodyKind = null;

            decodeHeaders(buffer, offset, limit, stream);
            skipDeliveryAnnotations(buffer, decodeOffset, limit);
            final Array32FW<AmqpAnnotationFW> annotations = decodeAnnotations(buffer, decodeOffset, limit);
            amqpDataEx.annotations(annotations);
            final AmqpPropertiesFW properties = decodeProperties(buffer, decodeOffset, limit);
            amqpDataEx.properties(properties);
            final Array32FW<AmqpApplicationPropertyFW> applicationProperties =
                decodeApplicationProperties(buffer, decodeOffset, limit);
            amqpDataEx.applicationProperties(applicationProperties);

            messageFragmentRW.wrap(valueBuffer, 0, valueBuffer.capacity());

            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, decodeOffset, limit);
            stream.decoder = lookupSectionDecoder(sectionType.get());

            return decodeMessageFragment(stream, buffer, sectionType.limit(), limit);
        }

        private OctetsFW decodeFragment(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            messageFragmentRW.wrap(valueBuffer, 0, valueBuffer.capacity());
            return decodeMessageFragment(stream, buffer, offset, limit);
        }

        private AmqpSectionDecoder lookupSectionDecoder(
            AmqpSectionType sectionType)
        {
            AmqpSectionDecoder decoder;
            switch (sectionType)
            {
            case DATA:
                decoder = this::decodeSectionData;
                break;
            case SEQUENCE:
                decoder = this::decodeSectionSequence;
                break;
            case VALUE:
                decoder = this::decodeSectionValue;
                break;
            case FOOTER:
                decoder = this::skipFooter;
                break;
            default:
                throw new IllegalArgumentException("Unexpected section type: " + sectionType);
            }

            return decoder;
        }

        private OctetsFW decodeMessageFragment(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            AmqpSectionDecoder previous = null;
            int progress = offset;

            while (progress <= limit && previous != stream.decoder)
            {
                previous = stream.decoder;
                progress = stream.decoder.decode(stream, buffer, progress, limit);
            }

            assert progress == limit;

            return messageFragmentRW.build();
        }

        private int decodeSection(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            if (progress < limit)
            {
                final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, progress, limit);
                stream.decoder = lookupSectionDecoder(sectionType.get());
                progress = sectionType.limit();
            }
            return progress;
        }

        private int decodeSectionData(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            stream.decodeBodyKind = AmqpBodyKind.DATA;
            int constructor = buffer.getByte(offset) & 0xff;
            int progress = offset;
            stream.decodableBytes = 0;
            messageFragmentRW.put(buffer, progress, Byte.BYTES);
            progress++;

            switch (constructor)
            {
            case 0xa0:
                stream.decodableBytes = buffer.getByte(progress);
                messageFragmentRW.put(buffer, progress, Byte.BYTES);
                progress += Byte.BYTES;
                break;
            case 0xb0:
                stream.decodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                messageFragmentRW.put(buffer, progress, Integer.BYTES);
                progress += Integer.BYTES;
                break;
            }

            int available = Math.min(stream.decodableBytes, limit - progress);
            messageFragmentRW.put(buffer, progress, available);
            progress += available;
            stream.decodableBytes -= available;
            assert stream.decodableBytes >= 0;
            if (stream.decodableBytes == 0)
            {
                stream.decoder = this::decodeSection;
            }
            else
            {
                stream.decoder = this::decodeSectionBytes;
            }

            return progress;
        }

        private int decodeSectionSequence(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            stream.decodeBodyKind = AmqpBodyKind.SEQUENCE;
            int constructor = buffer.getByte(offset) & 0xff;
            int progress = offset;
            stream.decodableBytes = 0;
            messageFragmentRW.put(buffer, progress, Byte.BYTES);
            progress++;

            switch (constructor)
            {
            case 0x45:
                break;
            case 0xc0:
                stream.decodableBytes = buffer.getByte(progress);
                messageFragmentRW.put(buffer, progress, Byte.BYTES);
                progress += Byte.BYTES;
                break;
            case 0xd0:
                stream.decodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                messageFragmentRW.put(buffer, progress, Integer.BYTES);
                progress += Integer.BYTES;
                break;
            }

            int available = Math.min(stream.decodableBytes, limit - progress);
            messageFragmentRW.put(buffer, progress, available);
            progress += available;
            stream.decodableBytes -= available;
            assert stream.decodableBytes >= 0;
            if (stream.decodableBytes == 0)
            {
                stream.decoder = this::decodeSection;
            }
            else
            {
                stream.decoder = this::decodeSectionBytes;
            }

            return progress;
        }

        private int decodeSectionValue(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int constructor = buffer.getByte(offset) & 0xff;
            int constructorMask = constructor & 0xf0;
            int progress = offset;
            stream.decodableBytes = 0;
            stream.decodeBodyKind = AmqpBodyKind.VALUE;
            if (constructorMask != 0xa0 && constructorMask != 0xb0 && constructor != 0x40)
            {
                messageFragmentRW.put(buffer, progress, Byte.BYTES);
            }
            progress++;

            switch (constructorMask)
            {
            case 0x40:
                stream.decodableBytes = 0;
                if (constructor == 0x40)
                {
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_NULL;
                }
                break;
            case 0x50:
                stream.decodableBytes = Byte.BYTES;
                break;
            case 0x60:
                stream.decodableBytes = Short.BYTES;
                break;
            case 0x70:
                stream.decodableBytes = Integer.BYTES;
                break;
            case 0x80:
                stream.decodableBytes = Long.BYTES;
                break;
            case 0x90:
                stream.decodableBytes = Long.BYTES + Long.BYTES;
                break;
            case 0xa0:
                stream.decodableBytes = buffer.getByte(progress);
                switch (constructor)
                {
                case 0xa0:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_BINARY8;
                    break;
                case 0xa1:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_STRING8;
                    break;
                case 0xa3:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_SYMBOL8;
                    break;
                }
                progress++;
                break;
            case 0xb0:
                stream.decodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                switch (constructor)
                {
                case 0xb0:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_BINARY32;
                    break;
                case 0xb1:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_STRING32;
                    break;
                case 0xb3:
                    stream.decodeBodyKind = AmqpBodyKind.VALUE_SYMBOL32;
                    break;
                }
                progress += Integer.BYTES;
                break;
            case 0xc0:
            case 0xe0:
                stream.decodableBytes = buffer.getByte(progress);
                messageFragmentRW.put(buffer, progress, Byte.BYTES);
                progress += Byte.BYTES;
                break;
            case 0xd0:
            case 0xf0:
                stream.decodableBytes = buffer.getInt(progress, BIG_ENDIAN);
                messageFragmentRW.put(buffer, progress, Integer.BYTES);
                progress += Integer.BYTES;
                break;
            }

            int available = Math.min(stream.decodableBytes, limit - progress);
            messageFragmentRW.put(buffer, progress, available);
            progress += available;
            stream.decodableBytes -= available;
            assert stream.decodableBytes >= 0;
            if (stream.decodableBytes == 0)
            {
                stream.decoder = this::decodeSection;
            }
            else
            {
                stream.decoder = this::decodeSectionBytes;
            }

            return progress;
        }

        private int decodeSectionBytes(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;
            final int length = limit - offset;
            if (length > 0)
            {
                int available = Math.min(stream.decodableBytes, limit - progress);
                messageFragmentRW.put(buffer, progress, available);
                progress += available;
                stream.decodableBytes -= available;
                assert stream.decodableBytes >= 0;
                if (stream.decodableBytes == 0)
                {
                    stream.decoder = this::decodeSection;
                }
            }
            return progress;
        }

        private void decodeHeaders(
            DirectBuffer buffer,
            int offset,
            int limit,
            AmqpServer.AmqpSession.AmqpServerStream stream)
        {
            this.decodeOffset = offset;
            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, offset, limit);

            if (sectionType != null && sectionType.get() == AmqpSectionType.HEADER)
            {
                AmqpHeaderFW header = headersRO.tryWrap(buffer, sectionType.limit(), limit);
                assert header != null;

                if (header.hasDurable() && header.durable() != 0)
                {
                    stream.headerDurable = true;
                }

                this.decodeOffset = header.limit();
            }
        }

        private void skipDeliveryAnnotations(
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, offset, limit);

            if (sectionType != null && sectionType.get() == AmqpSectionType.DELIVERY_ANNOTATIONS)
            {
                AmqpMapFW<AmqpValueFW, AmqpValueFW> deliveryAnnotations =
                    deliveryAnnotationsRO.tryWrap(buffer, sectionType.limit(), limit);

                this.decodeOffset = deliveryAnnotations.limit();
            }
        }

        private Array32FW<AmqpAnnotationFW> decodeAnnotations(
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            AmqpMapFW<AmqpValueFW, AmqpValueFW> annotations = null;
            Array32FW.Builder<AmqpAnnotationFW.Builder, AmqpAnnotationFW> annotationBuilder =
                annotationRW.wrap(frameBuffer, 0, frameBuffer.capacity());
            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, offset, limit);

            if (sectionType != null && sectionType.get() == AmqpSectionType.MESSAGE_ANNOTATIONS)
            {
                annotations = annotationsRO.tryWrap(buffer, sectionType.limit(), limit);
                assert annotations != null;
                this.decodeOffset = annotations.limit();

                annotations.forEach((kv, vv) ->
                {
                    switch (kv.kind())
                    {
                    case SYMBOL1:
                        StringFW symbolKey = kv.getAsAmqpSymbol().get();
                        annotationBuilder.item(b -> b.key(k -> k.name(symbolKey))
                                                     .value(vb -> vb.bytes(vv.buffer(), vv.offset(), vv.sizeof())));
                        break;
                    case ULONG0:
                    case ULONG1:
                    case ULONG8:
                        long longKey = kv.getAsAmqpULong().get();
                        annotationBuilder.item(b -> b.key(k -> k.id(longKey))
                                                     .value(vb -> vb.bytes(vv.buffer(), vv.offset(), vv.sizeof())));
                        break;
                    default:
                        break;
                    }
                });
            }

            return annotationBuilder.build();
        }

        private AmqpPropertiesFW decodeProperties(
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            AmqpPropertiesFW.Builder propertyBuilder = propertyRW.wrap(frameBuffer, 0, frameBuffer.capacity());
            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, offset, limit);

            if (sectionType != null && sectionType.get() == AmqpSectionType.PROPERTIES)
            {
                AmqpMessagePropertiesFW property = amqpPropertiesRO.tryWrap(buffer, sectionType.limit(), limit);
                assert property != null;
                this.decodeOffset = property.limit();

                if (property.hasMessageId())
                {
                    propertyBuilder.messageId(b -> b.stringtype(property.messageId().asString()));
                }
                if (property.hasUserId())
                {
                    propertyBuilder.userId(b -> b.bytes(property.userId().value(), 0, property.userId().length()));
                }
                if (property.hasTo())
                {
                    propertyBuilder.to((String8FW) property.to());
                }
                if (property.hasSubject())
                {
                    propertyBuilder.subject((String8FW) property.subject());
                }
                if (property.hasReplyTo())
                {
                    propertyBuilder.replyTo((String8FW) property.replyTo());
                }
                if (property.hasCorrelationId())
                {
                    propertyBuilder.correlationId(b -> b.stringtype(property.correlationId().asString()));
                }
                if (property.hasContentType())
                {
                    propertyBuilder.contentType((String8FW) property.contentType());
                }
                if (property.hasContentEncoding())
                {
                    propertyBuilder.contentEncoding((String8FW) property.contentEncoding());
                }
                if (property.hasAbsoluteExpiryTime())
                {
                    propertyBuilder.absoluteExpiryTime(property.absoluteExpiryTime());
                }
                if (property.hasCreationTime())
                {
                    propertyBuilder.creationTime(property.creationTime());
                }
                if (property.hasGroupId())
                {
                    propertyBuilder.groupId((String8FW) property.groupId());
                }
                if (property.hasGroupSequence())
                {
                    propertyBuilder.groupSequence((int) property.groupSequence());
                }
                if (property.hasReplyToGroupId())
                {
                    propertyBuilder.replyToGroupId((String8FW) property.replyToGroupId());
                }
            }

            return propertyBuilder.build();
        }

        private Array32FW<AmqpApplicationPropertyFW> decodeApplicationProperties(
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            Array32FW.Builder<AmqpApplicationPropertyFW.Builder, AmqpApplicationPropertyFW> applicationPropertyBuilder =
                applicationPropertyRW.wrap(frameBuffer, 0, frameBuffer.capacity());
            final AmqpSectionTypeFW sectionType = amqpSectionTypeRO.tryWrap(buffer, offset, limit);

            if (sectionType != null && sectionType.get() == AmqpSectionType.APPLICATION_PROPERTIES)
            {
                AmqpApplicationPropertiesFW<AmqpSimpleTypeFW> applicationProperty = applicationPropertiesRO.tryWrap(buffer,
                    sectionType.limit(), limit);

                applicationProperty.forEach((k, v) ->
                {
                    String key = k.get().asString();
                    applicationPropertyBuilder.item(kb -> kb.key(key)
                                                            .value(i -> i.bytes(v.buffer(), v.offset(), v.sizeof())));
                });
                this.decodeOffset = applicationProperty.limit();
            }

            return applicationPropertyBuilder.build();
        }

        private int skipFooter(
            AmqpServer.AmqpSession.AmqpServerStream stream,
            DirectBuffer buffer,
            int offset,
            int limit)
        {
            int progress = offset;

            final int length = limit - progress;
            if (length > 0)
            {
                AmqpMapFW<AmqpValueFW, AmqpValueFW> footer = footerRO.tryWrap(buffer, progress, limit);
                progress = footer.limit();
            }
            return progress;
        }
    }

    private static StringFW[] asStringFWArray(
        String[] strings)
    {
        StringFW[] flyweights = EMPTY_STRINGFW_ARRAY;

        if (strings.length != 0)
        {
            flyweights = asList(strings)
                    .stream()
                    .map(String8FW::new)
                    .collect(toList())
                    .toArray(new StringFW[strings.length]);
        }

        return flyweights;
    }

    private static long sequenceNext(
        long value)
    {
        return (value + 1) & 0xFFFF_FFFFL;
    }
}
