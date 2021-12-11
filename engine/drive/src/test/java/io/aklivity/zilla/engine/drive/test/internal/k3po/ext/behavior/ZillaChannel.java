/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior;

import static io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.ZillaThrottleMode.MESSAGE;

import java.util.Deque;
import java.util.LinkedList;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.kaazing.k3po.driver.internal.netty.bootstrap.channel.AbstractChannel;
import org.kaazing.k3po.driver.internal.netty.channel.ChannelAddress;

import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetDebitor;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.types.stream.Capability;

public abstract class ZillaChannel extends AbstractChannel<ZillaChannelConfig>
{
    static final ChannelBufferFactory NATIVE_BUFFER_FACTORY = ZillaByteOrder.NATIVE.toBufferFactory();

    private long routeId;

    private long writableSeq;
    private long writableAck;
    private long writableAckCheckpoint = -1L;

    private int writablePadding;
    private int writableMin;
    private int writableMax;

    private long readableSeq;
    private long readableAck;

    private long sourceId;
    private long sourceAuth;
    private long targetId;
    private long targetAuth;

    final ZillaEngine engine;
    final Deque<MessageEvent> writeRequests;

    private ZillaExtensionKind readExtKind;
    private ChannelBuffer readExtBuffer;

    private ZillaExtensionKind writeExtKind;
    private ChannelBuffer writeExtBuffer;

    private boolean targetWriteRequestInProgress;

    private ChannelFuture beginOutputFuture;
    private ChannelFuture beginInputFuture;

    private int capabilities;
    private boolean flushable;

    private DefaultBudgetDebitor debitor;
    private long debitorIndex = -1L;

    private DefaultBudgetCreditor creditor;
    private long creditorIndex = -1L;

    private long debitorId;
    private long creditorId;

    private int pendingSharedBudget;

    private int readFlags = -1;
    private int writeFlags = -1;

    ZillaChannel(
        ZillaServerChannel parent,
        ChannelFactory factory,
        ChannelPipeline pipeline,
        ChannelSink sink,
        ZillaEngine engine,
        long targetId)
    {
        super(parent, factory, pipeline, sink, new DefaultZillaChannelConfig());

        this.engine = engine;
        this.writeRequests = new LinkedList<>();
        this.routeId = -1L;
        this.targetId = targetId;
    }

    @Override
    public ZillaChannelAddress getLocalAddress()
    {
        return (ZillaChannelAddress) super.getLocalAddress();
    }

    @Override
    public ZillaChannelAddress getRemoteAddress()
    {
        return (ZillaChannelAddress) super.getRemoteAddress();
    }

    public int getLocalScope()
    {
        return Long.SIZE - 1;
    }

    public abstract int getRemoteScope();

    @Override
    protected void setBound()
    {
        super.setBound();
    }

    @Override
    protected void setConnected()
    {
        super.setConnected();
    }

    @Override
    protected boolean isReadAborted()
    {
        return super.isReadAborted();
    }

    @Override
    protected boolean isWriteAborted()
    {
        return super.isWriteAborted();
    }

    @Override
    protected boolean isReadClosed()
    {
        return super.isReadClosed();
    }

    @Override
    protected boolean isWriteClosed()
    {
        return super.isWriteClosed();
    }

    @Override
    protected boolean setReadClosed()
    {
        return super.setReadClosed();
    }

    @Override
    protected boolean setWriteClosed()
    {
        return super.setWriteClosed();
    }

    @Override
    protected boolean setReadAborted()
    {
        return super.setReadAborted();
    }

    @Override
    protected boolean setWriteAborted()
    {
        return super.setWriteAborted();
    }

    @Override
    protected boolean setClosed()
    {
        return super.setClosed();
    }

    @Override
    protected void setRemoteAddress(ChannelAddress remoteAddress)
    {
        super.setRemoteAddress(remoteAddress);
    }

    @Override
    protected void setLocalAddress(ChannelAddress localAddress)
    {
        super.setLocalAddress(localAddress);
    }

    @Override
    public String toString()
    {
        ChannelAddress localAddress = this.getLocalAddress();
        String description = localAddress != null ? localAddress.toString() : super.toString();
        return String.format("%s [sourceId=%d, targetId=%d]", description, sourceId, targetId);
    }

    public void acknowledgeBytes(
        int reserved)
    {
        readableAck += reserved;
        assert readableAck <= readableSeq;
    }

    public int readableBudget()
    {
        final int readableMax = sourceMax();
        return Math.max(readableMax - (int)Math.max(readableSeq - readableAck, 0), 0);
    }

    public void routeId(
        long routeId)
    {
        this.routeId = routeId;
    }

    public long routeId()
    {
        return routeId;
    }

    public void sourceId(
        long sourceId)
    {
        this.sourceId = sourceId;
    }

    public long sourceId()
    {
        return sourceId;
    }

    public long targetId()
    {
        return targetId;
    }

    public void sourceAuth(
        long sourceAuth)
    {
        this.sourceAuth = sourceAuth;
    }

    public long sourceAuth()
    {
        return sourceAuth;
    }

    public void targetAuth(long targetAuth)
    {
        this.targetAuth = targetAuth;
    }

    public long targetAuth()
    {
        return targetAuth;
    }

    public long sourceSeq()
    {
        return readableSeq;
    }

    public void sourceSeq(
        long readableSeq)
    {
        assert readableSeq >= this.readableSeq;
        this.readableSeq = readableSeq;
    }

    public long sourceAck()
    {
        return readableAck;
    }

    public void sourceAck(
        long readableAck)
    {
        assert readableAck >= this.readableAck;
        this.readableAck = readableAck;
    }

    public int sourceMax()
    {
        return getConfig().getWindow();
    }

    public ChannelFuture beginOutputFuture()
    {
        if (beginOutputFuture == null)
        {
            beginOutputFuture = Channels.future(this);
        }

        return beginOutputFuture;
    }

    public ChannelFuture beginInputFuture()
    {
        if (beginInputFuture == null)
        {
            beginInputFuture = Channels.future(this);
        }

        return beginInputFuture;
    }

    public void setCreditor(
        DefaultBudgetCreditor creditor,
        long creditorId)
    {
        assert this.creditor == null;
        this.creditor = creditor;
        this.creditorId = creditorId;
    }

    public void setCreditorIndex(
        long creditorIndex)
    {
        assert creditorIndex != -1L;
        this.creditorIndex = creditorIndex;
        getCloseFuture().addListener(this::cleanupCreditor);
    }

    public void doSharedCredit(
        long traceId,
        int credit)
    {
        if (creditor != null && creditorId != 0L)
        {
            creditor.creditById(traceId, creditorId, credit);
        }
    }

    public int pendingSharedBudget()
    {
        return pendingSharedBudget;
    }

    public void pendingSharedCredit(
        int pendingSharedCredit)
    {
        if (creditorId != 0L)
        {
            pendingSharedBudget += pendingSharedCredit;
        }
    }

    public int readFlags()
    {
        return readFlags;
    }

    public void readFlags(
        int readFlags)
    {
        this.readFlags = readFlags;
    }

    public int writeFlags()
    {
        return writeFlags;
    }

    public void writeFlags(
        int flags)
    {
        this.writeFlags = flags;
    }

    public void setDebitor(
        DefaultBudgetDebitor debitor,
        long debitorId)
    {
        assert this.debitor == null;
        this.debitor = debitor;
        this.debitorId = debitorId;
        this.debitorIndex = debitor.acquire(debitorId, targetId, this::systemFlush);
        if (this.debitorIndex == -1L)
        {
            getCloseFuture().setFailure(new ChannelException("Unable to acquire debitor"));
        }
        else
        {
            assert this.debitorIndex != -1L;
            getCloseFuture().addListener(this::cleanupDebitor);
        }
    }

    public boolean hasDebitor()
    {
        return debitor != null;
    }

    public long debitorId()
    {
        return debitorId;
    }

    public long creditorId()
    {
        return creditorId;
    }

    private void systemFlush(
        long budgetId)
    {
        ChannelFuture flushFuture = Channels.future(this);
        engine.systemFlush(this, flushFuture);
    }

    public int writableBytes()
    {
        return Math.max(writableBudget() - writablePadding, 0);
    }

    private int writableBudget()
    {
        return writableMax - (int) Math.max(writableSeq - writableAck, 0);
    }

    public boolean writable()
    {
        int writableBudget = writableBudget();

        if (debitor != null && debitorIndex != -1L)
        {
            writableBudget = Math.min(writableBudget, (int) debitor.available(debitorIndex));
        }

        return writableBudget > writablePadding || !getConfig().hasThrottle();
    }

    public int paddedBytes(
        int unpaddedBytes)
    {
        return unpaddedBytes + getConfig().getPadding();
    }

    public int reservedBytes(
        int writableBytes)
    {
        int reservedBytes = Math.max(writableBytes + writablePadding, writableMin);

        final boolean hasThrottle = getConfig().hasThrottle();
        if (hasThrottle)
        {
            writableBytes = Math.min(writableBytes(), writableBytes);
            reservedBytes = Math.max(writableBytes + writablePadding, writableMin);

            if (reservedBytes > 0 && debitor != null && debitorIndex != -1L)
            {
                reservedBytes = debitor.claim(debitorIndex, targetId, reservedBytes, reservedBytes);
            }
        }

        return reservedBytes;
    }

    public void readBytes(
        long sequence,
        int reservedBytes)
    {
        final int readableMax = sourceMax();
        this.readableSeq = sequence + reservedBytes;
        assert readableSeq <= readableAck + readableMax;
    }

    public void writtenBytes(
        int writtenBytes,
        int reservedBytes)
    {
        assert reservedBytes >= 0;
        this.writableSeq += reservedBytes;
        assert writableSeq >= writableAck;
        assert writablePadding >= 0 && (writableSeq <= writableAck + writableMax || !getConfig().hasThrottle());
    }

    public long targetSeq()
    {
        return writableSeq;
    }

    public long targetAck()
    {
        return writableAck;
    }

    public void targetAck(
        long writableAck)
    {
        assert writableAck >= this.writableAck;
        this.writableAck = writableAck;

        assert writableAck <= writableSeq;
    }

    public int targetPad()
    {
        return writablePadding;
    }

    public int targetMax()
    {
        return writableMax;
    }

    public int targetMin()
    {
        return writableMin;
    }

    public void writableWindow(
        long acknowledge,
        int padding,
        int minimum,
        int maximum,
        long traceId)
    {
        writableAck = acknowledge;
        writablePadding = padding;
        writableMin = minimum;
        writableMax = maximum;

        assert writableAck <= writableSeq;

        if (getConfig().getThrottle() == MESSAGE && targetWriteRequestInProgress)
        {
            if (writableAck >= writableAckCheckpoint)
            {
                completeWriteRequestIfFullyWritten();
            }
        }
    }

    public void capabilities(
        int capabilities)
    {
        this.capabilities = capabilities;
    }

    public boolean hasCapability(
        Capability capability)
    {
        return (capabilities & (1 << capability.ordinal())) != 0;
    }

    public void targetWriteRequestProgressing()
    {
        if (getConfig().getThrottle() == MESSAGE)
        {
            final MessageEvent writeRequest = writeRequests.peekFirst();
            final ChannelBuffer message = (ChannelBuffer) writeRequest.getMessage();
            writableAckCheckpoint = writableSeq + message.readableBytes();
            targetWriteRequestInProgress = true;
        }
    }

    public ChannelBuffer writeExtBuffer(
        ZillaExtensionKind writeExtKind,
        boolean readonly)
    {
        if (this.writeExtKind != writeExtKind)
        {
            if (readonly)
            {
                return ChannelBuffers.EMPTY_BUFFER;
            }
            else
            {
                if (writeExtBuffer == null)
                {
                    writeExtBuffer = getConfig().getBufferFactory().getBuffer(8192);
                }
                else
                {
                    writeExtBuffer.clear();
                }
                this.writeExtKind = writeExtKind;
            }
        }

        return writeExtBuffer;
    }

    public ChannelBuffer readExtBuffer(
        ZillaExtensionKind readExtKind)
    {
        if (this.readExtKind != readExtKind)
        {
            if (readExtBuffer == null)
            {
                readExtBuffer = getConfig().getBufferFactory().getBuffer(8192);
            }
            else
            {
                readExtBuffer.clear();
            }
            this.readExtKind = readExtKind;
        }

        return readExtBuffer;
    }

    public void targetWriteRequestProgress()
    {
        switch (getConfig().getThrottle())
        {
        case MESSAGE:
            if (targetWriteRequestInProgress && writableAck >= writableAckCheckpoint)
            {
                completeWriteRequestIfFullyWritten();
            }
            break;
        default:
            completeWriteRequestIfFullyWritten();
            break;
        }
    }

    public boolean isTargetWriteRequestInProgress()
    {
        return targetWriteRequestInProgress;
    }

    public void setFlushable()
    {
        flushable = true;
    }

    public boolean isFlushable()
    {
        return flushable;
    }

    private void cleanupCreditor(
        ChannelFuture future) throws Exception
    {
        assert creditorIndex != -1L;
        creditor.release(creditorIndex);
        creditorIndex = -1L;
    }

    private void cleanupDebitor(
        ChannelFuture future) throws Exception
    {
        assert debitorIndex != -1L;
        debitor.release(debitorIndex, targetId);
        debitorIndex = -1L;
        debitorId = 0L;
    }

    private void completeWriteRequestIfFullyWritten()
    {
        final MessageEvent writeRequest = writeRequests.peekFirst();
        final ChannelBuffer message = (ChannelBuffer) writeRequest.getMessage();
        if (!message.readable())
        {
            targetWriteRequestInProgress = false;
            writeRequests.removeFirst();
            writeRequest.getFuture().setSuccess();
        }
    }
}
