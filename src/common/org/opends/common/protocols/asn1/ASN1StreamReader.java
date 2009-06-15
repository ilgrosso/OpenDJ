package org.opends.common.protocols.asn1;

import org.opends.messages.Message;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.asn1.ASN1Constants.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.DebugLogLevel;
import org.opends.common.protocols.ProtocolException;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import com.sun.grizzly.utils.PoolableObject;
import com.sun.grizzly.streams.StreamReader;

public class ASN1StreamReader implements ASN1Reader, PoolableObject
{
  private static final DebugTracer TRACER = getTracer();
  private static final int MAX_STRING_BUFFER_SIZE = 1024;

  private int state = ELEMENT_READ_STATE_NEED_TYPE;
  private byte peekType = 0;
  private int peekLength = -1;
  private int lengthBytesNeeded = 0;
  private final int maxElementSize;

  private StreamReader streamReader;
  private final RootSequenceLimiter rootLimiter;
  private SequenceLimiter readLimiter;
  private byte[] buffer;

  private interface SequenceLimiter
  {
    public SequenceLimiter endSequence() throws IOException;

    public SequenceLimiter startSequence(int readLimit);

    public void checkLimit(int readSize)
        throws IOException, BufferUnderflowException;

    public int remaining();
  }

  class RootSequenceLimiter implements SequenceLimiter
  {
    private ChildSequenceLimiter child;

    public ChildSequenceLimiter endSequence() throws ProtocolException
    {
      Message message = ERR_ASN1_SEQUENCE_READ_NOT_STARTED.get();
      throw new ProtocolException(message);
    }

    public ChildSequenceLimiter startSequence(int readLimit)
    {
      if (child == null)
      {
        child = new ChildSequenceLimiter();
        child.parent = this;
      }

      child.readLimit = readLimit;
      child.bytesRead = 0;

      return child;
    }

    public void checkLimit(int readSize)
    {
    }

    public int remaining()
    {
      return streamReader.availableDataSize();
    }
  }

  class ChildSequenceLimiter implements SequenceLimiter
  {
    private SequenceLimiter parent;
    private ChildSequenceLimiter child;
    private int readLimit;
    private int bytesRead;

    public SequenceLimiter endSequence() throws IOException
    {
      parent.checkLimit(remaining());
      for (int i = 0; i < remaining(); i++)
      {
        streamReader.readByte();
      }

      return parent;
    }

    public ChildSequenceLimiter startSequence(int readLimit)
    {
      if (child == null)
      {
        child = new ChildSequenceLimiter();
        child.parent = this;
      }

      child.readLimit = readLimit;
      child.bytesRead = 0;

      return child;
    }

    public void checkLimit(int readSize)
        throws IOException, BufferUnderflowException
    {
      if (readLimit > 0 && bytesRead + readSize > readLimit)
      {
        throw new BufferUnderflowException();
      }

      parent.checkLimit(readSize);

      bytesRead += readSize;
    }

    public int remaining()
    {
      return readLimit - bytesRead;
    }
  }


  /**
   * Creates a new ASN1 reader whose source is the provided input stream and
   * having a user defined maximum BER element size.
   *
   * @param maxElementSize The maximum BER element size, or <code>0</code> to
   *                       indicate that there is no limit.
   */
  public ASN1StreamReader(int maxElementSize)
  {
    this.readLimiter = this.rootLimiter = new RootSequenceLimiter();
    this.buffer = new byte[MAX_STRING_BUFFER_SIZE];
    this.maxElementSize = maxElementSize;
  }

  /**
   * Determines if a complete ASN.1 element is ready to be read from the stream
   * reader.
   *
   * @return <code>true</code> if another complete element is available or
   *         <code>false</code> otherwise.
   *
   * @throws IOException If an error occurs while trying to decode an ASN1
   *                       element.
   */
  public boolean elementAvailable() throws IOException
  {
    if (state == ELEMENT_READ_STATE_NEED_TYPE &&
        !needTypeState(true))
    {
      return false;
    }
    if (state == ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE &&
        !needFirstLengthByteState(true))
    {
      return false;
    }
    if (state == ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES &&
        !needAdditionalLengthBytesState(true))
    {
      return false;
    }

    return peekLength <= readLimiter.remaining();
  }

  /**
   * Determines if the input stream contains at least one ASN.1 element to be
   * read.
   *
   * @return <code>true</code> if another element is available or
   *         <code>false</code> otherwise.
   *
   * @throws IOException If an error occurs while trying to decode an ASN1
   *                       element.
   */
  public boolean hasNextElement() throws IOException
  {
    return state != ELEMENT_READ_STATE_NEED_TYPE ||
           needTypeState(true);
  }

  /**
   * Internal helper method reading the ASN.1 type byte and transition to the
   * next state if successful.
   *
   * @param ensureRead <code>true</code>  to check for availability first.
   *
   * @return <code>true</code> if the type byte was successfully read
   *
   * @throws IOException If an error occurs while trying to decode an ASN1
   *                       element.
   */
  private boolean needTypeState(boolean ensureRead)
      throws IOException
  {
    // Read just the type.
    if (ensureRead && readLimiter.remaining() <= 0)
    {
      return false;
    }

    readLimiter.checkLimit(1);
    peekType = streamReader.readByte();
    state = ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE;
    return true;
  }

  /**
   * Internal helper method reading the first length bytes and transition to the
   * next state if successful.
   *
   * @param ensureRead <code>true</code> to check for availability first.
   *
   * @return <code>true</code> if the length bytes was successfully read
   *
   * @throws IOException If an error occurs while trying to decode an ASN1
   *                       element.
   */
  private boolean needFirstLengthByteState(boolean ensureRead)
      throws IOException
  {
    if (ensureRead && readLimiter.remaining() <= 0)
    {
      return false;
    }

    readLimiter.checkLimit(1);
    byte readByte = streamReader.readByte();
    peekLength = (readByte & 0x7F);
    if (peekLength != readByte)
    {
      lengthBytesNeeded = peekLength;
      if (lengthBytesNeeded > 4)
      {
        Message message =
            ERR_ASN1_INVALID_NUM_LENGTH_BYTES.get(lengthBytesNeeded);
        throw new ProtocolException(message);
      }
      peekLength = 0x00;

      if (ensureRead && readLimiter.remaining() < lengthBytesNeeded)
      {
        state = ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES;
        return false;
      }

      readLimiter.checkLimit(lengthBytesNeeded);
      while (lengthBytesNeeded > 0)
      {
        readByte = streamReader.readByte();
        peekLength = (peekLength << 8) | (readByte & 0xFF);
        lengthBytesNeeded--;
      }
    }

    // Make sure that the element is not larger than the maximum allowed
    // message size.
    if ((maxElementSize > 0) && (peekLength > maxElementSize))
    {
      Message m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
          peekLength, maxElementSize);
      throw new ProtocolException(m);
    }
    state = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
    return true;
  }

  /**
   * Internal helper method reading the additional ASN.1 length bytes and
   * transition to the next state if successful.
   *
   * @param ensureRead <code>true</code> to check for availability first.
   *
   * @return <code>true</code> if the length bytes was successfully read.
   *
   * @throws IOException   If an error occurs while reading from the stream.
   */
  private boolean needAdditionalLengthBytesState(boolean ensureRead)
      throws IOException
  {
    if (ensureRead && readLimiter.remaining() < lengthBytesNeeded)
    {
      return false;
    }

    byte readByte;
    readLimiter.checkLimit(lengthBytesNeeded);
    while (lengthBytesNeeded > 0)
    {
      readByte = streamReader.readByte();
      peekLength = (peekLength << 8) | (readByte & 0xFF);
      lengthBytesNeeded--;
    }

    // Make sure that the element is not larger than the maximum allowed
    // message size.
    if ((maxElementSize > 0) && (peekLength > maxElementSize))
    {
      Message m = ERR_LDAP_CLIENT_DECODE_MAX_REQUEST_SIZE_EXCEEDED.get(
          peekLength, maxElementSize);
      throw new ProtocolException(m);
    }
    state = ELEMENT_READ_STATE_NEED_VALUE_BYTES;
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public byte peekType() throws IOException
  {
    if (state == ELEMENT_READ_STATE_NEED_TYPE)
    {
      needTypeState(false);
    }

    return peekType;
  }

  /**
   * {@inheritDoc}
   */
  public int peekLength() throws IOException
  {
    peekType();

    switch (state)
    {
      case ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE:
        needFirstLengthByteState(false);
        break;

      case ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES:
        needAdditionalLengthBytesState(false);
    }

    return peekLength;
  }

  /**
   * {@inheritDoc}
   */
  public boolean readBoolean() throws IOException
  {
    return readBoolean(UNIVERSAL_BOOLEAN_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public boolean readBoolean(byte expectedTag) throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    if (peekLength != 1)
    {
      Message message =
          ERR_ASN1_BOOLEAN_INVALID_LENGTH.get(peekLength);
      throw new ProtocolException(message);
    }

    readLimiter.checkLimit(peekLength);
    byte readByte = streamReader.readByte();

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
         String.format("READ ASN.1 BOOLEAN(type=0x%x, length=%d, value=%s)",
                       peekType, peekLength, String.valueOf(readByte != 0x00)));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return readByte != 0x00;
  }

  /**
   * {@inheritDoc}
   */
  public int readEnumerated() throws IOException
  {
    return readEnumerated(UNIVERSAL_ENUMERATED_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public int readEnumerated(Byte expectedTag) throws IOException
  {
    // TODO: Should we check type here?
    
    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 4))
    {
      Message message = ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw new ProtocolException(message);
    }

    // From an implementation point of view, an enumerated value is
    // equivalent to an integer.
    return (int) readInteger(expectedTag);
  }

  /**
   * {@inheritDoc}
   */
  public long readInteger() throws IOException
  {
    return readInteger(UNIVERSAL_INTEGER_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public long readInteger(byte expectedTag) throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    if ((peekLength < 1) || (peekLength > 8))
    {
      Message message =
          ERR_ASN1_INTEGER_INVALID_LENGTH.get(peekLength);
      throw new ProtocolException(message);
    }

    readLimiter.checkLimit(peekLength);
    if (peekLength > 4)
    {
      long longValue = 0;
      for (int i = 0; i < peekLength; i++)
      {
        int readByte = streamReader.readByte();
        if (i == 0 && ((byte) readByte) < 0)
        {
          longValue = 0xFFFFFFFFFFFFFFFFL;
        }
        longValue = (longValue << 8) | (readByte & 0xFF);
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return longValue;
    }
    else
    {
      int intValue = 0;
      for (int i = 0; i < peekLength; i++)
      {
        int readByte = streamReader.readByte();
        if (i == 0 && ((byte) readByte) < 0)
        {
          intValue = 0xFFFFFFFF;
        }
        intValue = (intValue << 8) | (readByte & 0xFF);
      }

      if (debugEnabled())
      {
        TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
             String.format("READ ASN.1 INTEGER(type=0x%x, length=%d, value=%d)",
                           peekType, peekLength, intValue));
      }

      state = ELEMENT_READ_STATE_NEED_TYPE;
      return intValue;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void readNull() throws IOException
  {
    readNull(UNIVERSAL_NULL_TYPE);
  }


  /**
   * {@inheritDoc}
   */
  public void readNull(byte expectedTag) throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    // Make sure that the decoded length is exactly zero byte.
    if (peekLength != 0)
    {
      Message message =
          ERR_ASN1_NULL_INVALID_LENGTH.get(peekLength);
      throw new ProtocolException(message);
    }

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                          String.format("READ ASN.1 NULL(type=0x%x, length=%d)",
                                        peekType, peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString() throws IOException
  {
    return readOctetString(UNIVERSAL_OCTET_STRING_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public ByteString readOctetString(byte expectedTag)
      throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return ByteString.empty();
    }

    readLimiter.checkLimit(peekLength);
    // Copy the value and construct the element to return.
    byte[] value = new byte[peekLength];
    streamReader.readByteArray(value);

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                   String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)",
                                 peekType, peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
    return ByteString.wrap(value);
  }


  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString() throws IOException
  {
    // We could cache the UTF-8 CharSet if performance proves to be an
    // issue.
    return readOctetStringAsString("UTF-8");
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(byte expectedTag)
      throws IOException
  {
    // We could cache the UTF-8 CharSet if performance proves to be an
    // issue.
    return readOctetStringAsString(expectedTag, "UTF-8");
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(String charSet)
      throws IOException
  {
    return readOctetStringAsString(UNIVERSAL_OCTET_STRING_TYPE, charSet);
  }

  /**
   * {@inheritDoc}
   */
  public String readOctetStringAsString(byte expectedTag, String charSet)
      throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return "";
    }

    byte[] readBuffer;
    if (peekLength <= buffer.length)
    {
      readBuffer = buffer;
    }
    else
    {
      readBuffer = new byte[peekLength];
    }

    readLimiter.checkLimit(peekLength);
    streamReader.readByteArray(readBuffer, 0, peekLength);

    state = ELEMENT_READ_STATE_NEED_TYPE;

    String str;
    try
    {
      str = new String(buffer, 0, peekLength, charSet);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      str = new String(buffer, 0, peekLength);
    }

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                 String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d, " +
                               "value=%s)", peekType, peekLength, str));
    }

    return str;
  }

  /**
   * {@inheritDoc}
   */
  public void readOctetString(ByteStringBuilder buffer)
      throws IOException
  {
    readOctetString(UNIVERSAL_OCTET_STRING_TYPE, buffer);
  }

  /**
   * {@inheritDoc}
   */
  public void readOctetString(byte expectedTag, ByteStringBuilder buffer)
      throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    if (peekLength == 0)
    {
      state = ELEMENT_READ_STATE_NEED_TYPE;
      return;
    }

    readLimiter.checkLimit(peekLength);
    // Copy the value and construct the element to return.
    // TODO: Is there a more efficient way to do this?
    for(int i = 0; i < peekLength; i++)
    {
      buffer.append(streamReader.readByte());
    }

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                   String.format("READ ASN.1 OCTETSTRING(type=0x%x, length=%d)",
                                 peekType, peekLength));
    }

    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSequence() throws IOException
  {
    readStartSequence(UNIVERSAL_SEQUENCE_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSequence(byte expectedTag)
      throws IOException
  {
    checkTag(expectedTag);

    // Read the header if haven't done so already
    peekLength();

    readLimiter = readLimiter.startSequence(peekLength);

    if (debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE,
                      String.format("READ ASN.1 SEQUENCE(type=0x%x, length=%d)",
                                    peekType, peekLength));
    }

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSet() throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readStartSequence(UNIVERSAL_SET_TYPE);
  }

  /**
   * {@inheritDoc}
   */
  public void readStartSet(byte expectedTag) throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readStartSequence(expectedTag);
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSequence() throws IOException
  {
    readLimiter = readLimiter.endSequence();

    // Reset the state
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * {@inheritDoc}
   */
  public void readEndSet() throws IOException
  {
    // From an implementation point of view, a set is equivalent to a
    // sequence.
    readEndSequence();
  }

  /**
   * {@inheritDoc}
   */
  public void skipElement() throws IOException
  {
    // Read the header if haven't done so already
    peekLength();

    readLimiter.checkLimit(peekLength);
    for (int i = 0; i < peekLength; i++)
    {
      streamReader.readByte();
    }
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }

  /**
   * Closes this ASN.1 reader and the underlying stream.
   *
   * @throws IOException if an I/O error occurs
   */
  public void close() throws IOException
  {
    // close the stream reader.
    streamReader.close();
  }

  private void checkTag(byte expected)
      throws IOException
  {
    if(peekType() != expected)
    {
      throw new ProtocolException(ERR_ASN1_UNEXPECTED_TAG.get(
          expected, peekType()));
    }
  }

  public void setStreamReader(StreamReader streamReader)
  {
    this.streamReader = streamReader;
  }

  public void prepare()
  {
    // Nothing to do
  }

  public void release()
  {
    streamReader = null;
    peekLength = -1;
    peekType = 0;
    readLimiter = rootLimiter;
    state = ELEMENT_READ_STATE_NEED_TYPE;
  }
}
