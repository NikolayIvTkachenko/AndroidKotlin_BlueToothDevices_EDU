package com.rsh.tkachenkoni.app_version_07_ble_04_example.blessed_model

import org.jetbrains.annotations.Nullable
import java.nio.ByteOrder
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and


/**
 *
 * Created by Nikolay Tkachenko
 * E-Mail: tkachenni@mail.ru
 */
class BluetoothBytesParser @JvmOverloads constructor(
    value: ByteArray,
    offset: Int = 0,
    byteOrder: ByteOrder = LITTLE_ENDIAN
) {
    private var internalOffset: Int
    private var mValue: ByteArray
    private val internalByteOrder: ByteOrder

    /**
     * Create a BluetoothBytesParser that contains an empty byte array and sets the byteOrder.
     *
     * @param byteOrder the byte order to use (either LITTLE_ENDIAN or BIG_ENDIAN)
     * @throws NullPointerException if the byteOrder is null
     */
    constructor(byteOrder: ByteOrder) : this(ByteArray(0), byteOrder) {}
    /**
     * Create a BluetoothBytesParser containing the byte array and byteOrder.
     *
     * @param value     byte array
     * @param byteOrder the byte order to use (either LITTLE_ENDIAN or BIG_ENDIAN)
     * @throws IllegalArgumentException if the byteOrder is not LITTLE_ENDIAN or BIG_ENDIAN
     * @throws NullPointerException if the value or byteOrder are null
     */
    /**
     * Create a BluetoothBytesParser containing an empty byte array and sets the byteOrder to LITTLE_ENDIAN.
     *
     */
    @JvmOverloads
    constructor(value: ByteArray = ByteArray(0), byteOrder: ByteOrder = LITTLE_ENDIAN) : this(
        value,
        0,
        byteOrder
    ) {
    }

    /**
     * Return a Long value. This operation will automatically advance the internal offset to the next position.
     *
     * @return a Long value
     * @throws IllegalArgumentException if there are not enough bytes for a long value
     */
    fun getLongValue(): Long {
        return getLongValue(internalByteOrder)
    }

    /**
     * Return a Long value using the specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return a Long value
     * @throws IllegalArgumentException if there are not enough bytes for a long value
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getLongValue(byteOrder: ByteOrder): Long {
        Objects.requireNonNull(byteOrder)
        val result = getLongValue(internalOffset, byteOrder)
        internalOffset += 8
        return result
    }

    /**
     * Return a Long value using the specified byte order and offset position. This operation will not advance the internal offset to the next position.
     *
     * @param offset     Offset at which the Long value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return a Long value
     * @throws IllegalArgumentException if the offset is not valid for getting a long value
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getLongValue(offset: Int, byteOrder: ByteOrder): Long {
        Objects.requireNonNull(byteOrder)
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        if (offset + 8 > mValue.size) throw IllegalArgumentException(INVALID_OFFSET)
        if (byteOrder == LITTLE_ENDIAN) {
            var value = (0x00FF and mValue[offset + 7].toInt()).toLong()
            for (i in 6 downTo 0) {
                value = value shl 8
                value += (0x00FF and mValue[i + offset].toInt()).toLong()
            }
            return value
        } else if (byteOrder == BIG_ENDIAN) {
            var value = (0x00FF and mValue[offset].toInt()).toLong()
            for (i in 1..7) {
                value = value shl 8
                value += (0x00FF and mValue[i + offset].toInt()).toLong()
            }
            return value
        }
        throw IllegalArgumentException("invalid byte order")
    }

    /**
     * Return an Integer value of the specified type. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte(s) value
     * @return An Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     */
    fun getIntValue(formatType: Int): Int {
        val result = getIntValue(formatType, internalOffset, internalByteOrder)
        internalOffset += getTypeLen(formatType)
        return result
    }

    /**
     * Return an Integer value of the specified type and specified byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType the format type used to interpret the byte(s) value
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return an Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getIntValue(formatType: Int, byteOrder: ByteOrder): Int {
        Objects.requireNonNull(byteOrder)
        val result = getIntValue(formatType, internalOffset, byteOrder)
        internalOffset += getTypeLen(formatType)
        return result
    }

    /**
     * Return an Integer value of the specified type. This operation will not advance the internal offset to the next position.
     *
     * The formatType parameter determines how the byte array
     * is to be interpreted. For example, setting formatType to
     * [.FORMAT_UINT16] specifies that the first two bytes of the
     * byte array at the given offset are interpreted to generate the
     * return value.
     *
     * @param formatType The format type used to interpret the byte array.
     * @param offset     Offset at which the integer value can be found.
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return an Integer value
     * @throws IllegalArgumentException if there are not enough bytes for an Integer value
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getIntValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Int {
        Objects.requireNonNull(byteOrder)
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        if (offset + getTypeLen(formatType) > mValue.size) throw IllegalArgumentException(
            INVALID_OFFSET
        )
        when (formatType) {
            FORMAT_UINT8 -> return unsignedByteToInt(
                mValue[offset]
            )
            FORMAT_UINT16 -> return if (byteOrder == LITTLE_ENDIAN) unsignedBytesToInt(
                mValue.get(
                    offset
                ), mValue.get(offset + 1)
            ) else unsignedBytesToInt(mValue.get(offset + 1), mValue.get(offset))
            FORMAT_UINT32 -> return if (byteOrder == LITTLE_ENDIAN) unsignedBytesToInt(
                mValue.get(offset), mValue.get(offset + 1),
                mValue.get(offset + 2), mValue.get(offset + 3)
            ) else unsignedBytesToInt(
                mValue.get(offset + 3), mValue.get(offset + 2),
                mValue.get(offset + 1), mValue.get(offset)
            )
            FORMAT_SINT8 -> return unsignedToSigned(
                unsignedByteToInt(
                    mValue[offset]
                ), 8
            )
            FORMAT_SINT16 -> return if (byteOrder == LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    mValue.get(offset),
                    mValue.get(offset + 1)
                ), 16
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    mValue.get(offset + 1),
                    mValue.get(offset)
                ), 16
            )
            FORMAT_SINT32 -> return if (byteOrder == LITTLE_ENDIAN) unsignedToSigned(
                unsignedBytesToInt(
                    mValue.get(offset),
                    mValue.get(offset + 1), mValue.get(offset + 2), mValue.get(offset + 3)
                ), 32
            ) else unsignedToSigned(
                unsignedBytesToInt(
                    mValue.get(offset + 3),
                    mValue.get(offset + 2), mValue.get(offset + 1), mValue.get(offset)
                ), 32
            )
        }
        throw IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE)
    }

    /**
     * Return a float value of the specified format. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @return The float value at the position of the internal offset and set ByteOrder
     * @throws IllegalArgumentException if there are not enough bytes for an Float value
     */
    fun getFloatValue(formatType: Int): Float {
        val result = getFloatValue(formatType, internalOffset, internalByteOrder)
        internalOffset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a float value of the specified format and byte order. This operation will automatically advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     * @throws IllegalArgumentException if there are not enough bytes for an Float value
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getFloatValue(formatType: Int, byteOrder: ByteOrder): Float {
        Objects.requireNonNull(byteOrder)
        val result = getFloatValue(formatType, internalOffset, byteOrder)
        internalOffset += getTypeLen(formatType)
        return result
    }

    /**
     * Return a Float value of the specified format, offset and byte order. This operation will not advance the internal offset to the next position.
     *
     * @param formatType The format type used to interpret the byte array (FORMAT_SFLOAT or FORMAT_FLOAT)
     * @param byteOrder  the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @return The float value at the position of the internal offset
     * @throws IllegalArgumentException if there are not enough bytes for an Float value or if the formatType is not FORMAT_SFLOAT or FORMAT_FLOAT
     * @throws NullPointerException if the value of byteOrder is null
     */
    fun getFloatValue(formatType: Int, offset: Int, byteOrder: ByteOrder): Float {
        Objects.requireNonNull(byteOrder)
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        if (offset + getTypeLen(formatType) > mValue.size) throw IllegalArgumentException(
            INVALID_OFFSET
        )
        when (formatType) {
            FORMAT_SFLOAT -> return if (byteOrder == LITTLE_ENDIAN) bytesToFloat(
                mValue.get(offset),
                mValue.get(offset + 1)
            ) else bytesToFloat(mValue.get(offset + 1), mValue.get(offset))
            FORMAT_FLOAT -> return if (byteOrder == LITTLE_ENDIAN) bytesToFloat(
                mValue.get(offset), mValue.get(offset + 1),
                mValue.get(offset + 2), mValue.get(offset + 3)
            ) else bytesToFloat(
                mValue.get(offset + 3), mValue.get(offset + 2),
                mValue.get(offset + 1), mValue.get(offset)
            )
        }
        throw IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE)
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @return String value represented by the byte array
     */
    fun getStringValue(): String {
        return getStringValue(internalOffset)
    }

    /**
     * Return a String from this byte array. This operation will not advance the internal offset to the next position.
     *
     * @param offset Offset at which the string value can be found.
     * @return String value represented by the byte array
     * @throws IllegalArgumentException if the offset is invalid
     */
    fun getStringValue(offset: Int): String {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        if (mValue == null || offset > mValue.size) throw IllegalArgumentException(
            INVALID_OFFSET
        )

        // Copy all bytes
        val strBytes = ByteArray(mValue.size - offset)
        for (i in 0 until mValue.size - offset) strBytes[i] = mValue[offset + i]

        // Get rid of trailing zero/space bytes
        var j = strBytes.size
        while (j > 0 && (strBytes[j - 1] == 0 || strBytes[j - 1] == 0x20)) j--

        // Convert to string
        return String(strBytes, 0, j, StandardCharsets.ISO_8859_1)
    }

    /**
     * Return a the date represented by the byte array.
     *
     * The byte array must conform to the DateTime specification (year, month, day, hour, min, sec)
     *
     * @return the Date represented by the byte array
     * @throws IllegalArgumentException if there are not enough bytes to get a Date
     */
    fun getDateTime(): Date {
        val result = getDateTime(internalOffset)
        internalOffset += 7
        return result
    }

    /**
     * Get Date from characteristic with offset
     *
     * @param offset Offset of value
     * @return Parsed date from value
     * @throws IllegalArgumentException if there are not enough bytes to get a Date
     */
    fun getDateTime(offset: Int): Date {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        if (offset + 7 > mValue.size) throw IllegalArgumentException(INVALID_OFFSET)

        // DateTime is always in little endian
        var newOffset = offset
        val year = getIntValue(FORMAT_UINT16, newOffset, LITTLE_ENDIAN)
        newOffset += getTypeLen(FORMAT_UINT16)
        val month = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN)
        newOffset += getTypeLen(FORMAT_UINT8)
        val day = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN)
        newOffset += getTypeLen(FORMAT_UINT8)
        val hour = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN)
        newOffset += getTypeLen(FORMAT_UINT8)
        val min = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN)
        newOffset += getTypeLen(FORMAT_UINT8)
        val sec = getIntValue(FORMAT_UINT8, newOffset, LITTLE_ENDIAN)
        val calendar = GregorianCalendar(year, month - 1, day, hour, min, sec)
        return calendar.time
    }

    /**
     * Get the byte array
     *
     * @return the complete byte array
     */
    fun getValue(): ByteArray {
        return mValue
    }

    /**
     * Read bytes and return the ByteArray of the length passed.  This will increment the internal offset
     *
     * @return the byte array
     * @throws IllegalArgumentException if length is longer than the remaining bytes counting from the internal offset
     */
    fun getByteArray(length: Int): ByteArray {
        val array = Arrays.copyOfRange(mValue, internalOffset, internalOffset + length)
        internalOffset += length
        return array
    }

    /**
     * Set the locally stored value of this byte array
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @throws IllegalArgumentException if the formatType is invalid for an Integer
     */
    fun setIntValue(value: Int, formatType: Int, offset: Int) {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        prepareArray(offset + getTypeLen(formatType))
        var newValue = value
        var newOffset = offset
        when (formatType) {
            FORMAT_SINT8 -> {
                newValue = intToSignedBits(newValue, 8)
                mValue[newOffset] = (newValue and 0xFF).toByte()
            }
            FORMAT_UINT8 -> mValue[newOffset] = (newValue and 0xFF).toByte()
            FORMAT_SINT16 -> {
                newValue = intToSignedBits(newValue, 16)
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (newValue and 0xFF).toByte()
                    mValue[newOffset] = (newValue shr 8 and 0xFF).toByte()
                } else {
                    mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                    mValue[newOffset] = (newValue and 0xFF).toByte()
                }
            }
            FORMAT_UINT16 -> if (internalByteOrder == LITTLE_ENDIAN) {
                mValue[newOffset++] = (newValue and 0xFF).toByte()
                mValue[newOffset] = (newValue shr 8 and 0xFF).toByte()
            } else {
                mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                mValue[newOffset] = (newValue and 0xFF).toByte()
            }
            FORMAT_SINT32 -> {
                newValue = intToSignedBits(newValue, 32)
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (newValue and 0xFF).toByte()
                    mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                    mValue[newOffset++] = (newValue shr 16 and 0xFF).toByte()
                    mValue[newOffset] = (newValue shr 24 and 0xFF).toByte()
                } else {
                    mValue[newOffset++] = (newValue shr 24 and 0xFF).toByte()
                    mValue[newOffset++] = (newValue shr 16 and 0xFF).toByte()
                    mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                    mValue[newOffset] = (newValue and 0xFF).toByte()
                }
            }
            FORMAT_UINT32 -> if (internalByteOrder == LITTLE_ENDIAN) {
                mValue[newOffset++] = (newValue and 0xFF).toByte()
                mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                mValue[newOffset++] = (newValue shr 16 and 0xFF).toByte()
                mValue[newOffset] = (newValue shr 24 and 0xFF).toByte()
            } else {
                mValue[newOffset++] = (newValue shr 24 and 0xFF).toByte()
                mValue[newOffset++] = (newValue shr 16 and 0xFF).toByte()
                mValue[newOffset++] = (newValue shr 8 and 0xFF).toByte()
                mValue[newOffset] = (newValue and 0xFF).toByte()
            }
            else -> throw IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE)
        }
    }

    /**
     * Set byte array to an Integer with specified format. This will increment the internal offset
     *
     * @param value      New value for this byte array
     * @param formatType Integer format type used to transform the value parameter
     * @throws IllegalArgumentException if the formatType is invalid for an Integer
     */
    fun setIntValue(value: Int, formatType: Int) {
        setIntValue(value, formatType, internalOffset)
        internalOffset += getTypeLen(formatType)
    }

    /**
     * Set byte array to a long. This will increment the internal offset
     *
     * @param value New long value for this byte array
     */
    fun setLong(value: Long) {
        setLong(value, internalOffset)
        internalOffset += 8
    }

    /**
     * Set byte array to a long
     *
     * @param value  New long value for this byte array
     * @param offset Offset at which the value should be placed
     * @throws IllegalArgumentException if the offset is invalid
     */
    fun setLong(value: Long, offset: Int) {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        prepareArray(offset + 8)
        var newValue = value
        if (internalByteOrder == BIG_ENDIAN) {
            for (i in 7 downTo 0) {
                mValue[i + offset] = (newValue and 0xFF).toByte()
                newValue = newValue shr 8
            }
        } else {
            for (i in 0..7) {
                mValue[i + offset] = (newValue and 0xFF).toByte()
                newValue = newValue shr 8
            }
        }
    }

    /**
     * Set byte array to a float of the specified type.
     *
     * @param mantissa   Mantissa for this float value
     * @param exponent   exponent value for this float value
     * @param formatType Float format type used to transform the value parameter
     * @param offset     Offset at which the value should be placed
     * @throws IllegalArgumentException if the offset is invalid or formatType is invalid for a Float
     */
    fun setFloatValue(mantissa: Int, exponent: Int, formatType: Int, offset: Int) {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        prepareArray(offset + getTypeLen(formatType))
        var newMantissa = mantissa
        var newExponent = exponent
        var newOffset = offset
        when (formatType) {
            FORMAT_SFLOAT -> {
                newMantissa = intToSignedBits(newMantissa, 12)
                newExponent = intToSignedBits(newExponent, 4)
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (newMantissa and 0xFF).toByte()
                    mValue[newOffset] = (newMantissa shr 8 and 0x0F).toByte()
                    (mValue[newOffset] += (newExponent and 0x0F shl 4).toByte()).toByte()
                } else {
                    mValue[newOffset] = (newMantissa shr 8 and 0x0F).toByte()
                    (mValue[newOffset++] += (newExponent and 0x0F shl 4).toByte()).toByte()
                    mValue[newOffset] = (mantissa and 0xFF).toByte()
                }
            }
            FORMAT_FLOAT -> {
                newMantissa = intToSignedBits(newMantissa, 24)
                newExponent = intToSignedBits(newExponent, 8)
                if (internalByteOrder == LITTLE_ENDIAN) {
                    mValue[newOffset++] = (newMantissa and 0xFF).toByte()
                    mValue[newOffset++] = (newMantissa shr 8 and 0xFF).toByte()
                    mValue[newOffset++] = (newMantissa shr 16 and 0xFF).toByte()
                    (mValue[newOffset] += (newExponent and 0xFF).toByte()).toByte()
                } else {
                    (mValue[newOffset++] += (newExponent and 0xFF).toByte()).toByte()
                    mValue[newOffset++] = (newMantissa shr 16 and 0xFF).toByte()
                    mValue[newOffset++] = (newMantissa shr 8 and 0xFF).toByte()
                    mValue[newOffset] = (newMantissa and 0xFF).toByte()
                }
            }
            else -> throw IllegalArgumentException(UNSUPPORTED_FORMAT_TYPE)
        }
    }

    /**
     * Set byte array to a Float using a given precision, i.e. number of digits after the comma
     *
     * @param value     Float value to create byte[] from
     * @param precision number of digits after the comma to use
     */
    fun setFloatValue(value: Float, precision: Int) {
        val mantissa = (value * Math.pow(10.0, precision.toDouble())).toFloat()
        setFloatValue(mantissa.toInt(), -precision, FORMAT_FLOAT, internalOffset)
        internalOffset += getTypeLen(FORMAT_FLOAT)
    }

    /**
     * Set byte array to a string at current offset
     *
     * @param value String to be added to byte array
     * @throws NullPointerException if value is null
     */
    fun setString(value: String) {
        Objects.requireNonNull(value)
        setString(value, internalOffset)
        internalOffset += value.toByteArray().size
    }

    /**
     * Set byte array to a string at specified offset position
     *
     * @param value  String to be added to byte array
     * @param offset the offset to place the string at
     * @throws NullPointerException if value is null
     */
    fun setString(value: String, offset: Int) {
        Objects.requireNonNull(value)
        prepareArray(offset + value.length)
        val valueBytes = value.toByteArray()
        System.arraycopy(valueBytes, 0, mValue, offset, valueBytes.size)
    }

    /**
     * Set the locally stored value of this byte array.
     *
     * @param value New value for this byte array
     */
    fun setValue(value: ByteArray) {
        mValue = Objects.requireNonNull(value)
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @throws NullPointerException if calendar is null
     */
    fun setCurrentTime(calendar: Calendar) {
        Objects.requireNonNull(calendar)
        mValue = ByteArray(10)
        mValue[0] = calendar[Calendar.YEAR].toByte()
        mValue[1] = (calendar[Calendar.YEAR] shr 8).toByte()
        mValue[2] = (calendar[Calendar.MONTH] + 1).toByte()
        mValue[3] = calendar[Calendar.DATE].toByte()
        mValue[4] = calendar[Calendar.HOUR_OF_DAY].toByte()
        mValue[5] = calendar[Calendar.MINUTE].toByte()
        mValue[6] = calendar[Calendar.SECOND].toByte()
        mValue[7] = ((calendar[Calendar.DAY_OF_WEEK] + 5) % 7 + 1).toByte()
        mValue[8] = (calendar[Calendar.MILLISECOND] * 256 / 1000).toByte()
        mValue[9] = 1
    }

    /**
     * Sets the byte array to represent the current date in CurrentTime format
     *
     * @param calendar the calendar object representing the current date
     * @throws NullPointerException if calendar is null
     */
    fun setDateTime(calendar: Calendar) {
        Objects.requireNonNull(calendar)
        mValue = ByteArray(7)
        mValue[0] = calendar[Calendar.YEAR].toByte()
        mValue[1] = (calendar[Calendar.YEAR] shr 8).toByte()
        mValue[2] = (calendar[Calendar.MONTH] + 1).toByte()
        mValue[3] = calendar[Calendar.DATE].toByte()
        mValue[4] = calendar[Calendar.HOUR_OF_DAY].toByte()
        mValue[5] = calendar[Calendar.MINUTE].toByte()
        mValue[6] = calendar[Calendar.SECOND].toByte()
    }

    /**
     * Returns the size of a give value type.
     */
    private fun getTypeLen(formatType: Int): Int {
        return formatType and 0xF
    }

    /**
     * Convert a signed byte to an unsigned int.
     */
    private fun unsignedByteToInt(b: Byte): Byte {
        return b and 0xFF
    }

    /**
     * Convert signed bytes to a 16-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte): Int {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
    }

    /**
     * Convert signed bytes to a 32-bit unsigned int.
     */
    private fun unsignedBytesToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) shl 8)
                + (unsignedByteToInt(b2) shl 16) + (unsignedByteToInt(b3) shl 24))
    }

    /**
     * Convert signed bytes to a 16-bit short float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte): Float {
        val mantissa = unsignedToSigned(
            unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) and 0x0F shl 8), 12
        )
        val exponent = unsignedToSigned(unsignedByteToInt(b1) shl 4, 4)
        return (mantissa * Math.pow(10.0, exponent.toDouble())).toFloat()
    }

    /**
     * Convert signed bytes to a 32-bit short float value.
     */
    private fun bytesToFloat(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Float {
        val mantissa = unsignedToSigned(
            (unsignedByteToInt(b0)
                    + (unsignedByteToInt(b1) shl 8)
                    + (unsignedByteToInt(b2) shl 16)), 24
        )
        return (mantissa * Math.pow(10.0, b3.toDouble())).toFloat()
    }

    /**
     * Convert an unsigned integer value to a two's-complement encoded
     * signed value.
     */
    private fun unsignedToSigned(unsigned: Int, size: Int): Int {
        return if ((unsigned and (1 shl size - 1)) != 0) {
            -1 * ((1 shl size - 1) - (unsigned and ((1 shl size - 1) - 1)))
        } else unsigned
    }

    /**
     * Convert an integer into the signed bits of a given length.
     */
    private fun intToSignedBits(i: Int, size: Int): Int {
        return if (i < 0) {
            (1 shl size - 1) + (i and ((1 shl size - 1) - 1))
        } else i
    }

    /**
     * Get the value of the internal offset
     */
    fun getOffset(): Int {
        return internalOffset
    }

    /**
     * Set the value of the internal offset
     * @throws IllegalArgumentException if the offset is negative
     */
    fun setOffset(offset: Int) {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        internalOffset = offset
    }

    /**
     * Get the set byte order
     */
    fun getByteOrder(): ByteOrder {
        return internalByteOrder
    }

    private fun prepareArray(neededLength: Int) {
        if (mValue == null) mValue = ByteArray(neededLength)
        if (neededLength > mValue.size) {
            val largerByteArray = ByteArray(neededLength)
            System.arraycopy(mValue, 0, largerByteArray, 0, mValue.size)
            mValue = largerByteArray
        }
    }

    override fun toString(): String {
        return bytes2String(mValue)
    }

    companion object {
        private val INVALID_OFFSET = "invalid offset"
        private val UNSUPPORTED_FORMAT_TYPE = "unsupported format type"
        private val OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO =
            "offset must be greater or equal to zero"

        /**
         * Characteristic value format type uint8
         */
        val FORMAT_UINT8 = 0x11

        /**
         * Characteristic value format type uint16
         */
        val FORMAT_UINT16 = 0x12

        /**
         * Characteristic value format type uint32
         */
        val FORMAT_UINT32 = 0x14

        /**
         * Characteristic value format type sint8
         */
        val FORMAT_SINT8 = 0x21

        /**
         * Characteristic value format type sint16
         */
        val FORMAT_SINT16 = 0x22

        /**
         * Characteristic value format type sint32
         */
        val FORMAT_SINT32 = 0x24

        /**
         * Characteristic value format type sfloat (16-bit float)
         */
        val FORMAT_SFLOAT = 0x32

        /**
         * Characteristic value format type float (32-bit float)
         */
        val FORMAT_FLOAT = 0x34

        /**
         * Convert a byte array to a string
         *
         * @param bytes the bytes to convert
         * @return String object that represents the byte array
         */
        fun bytes2String(@Nullable bytes: ByteArray?): String {
            if (bytes == null) return ""
            val sb = StringBuilder()
            for (b: Byte in bytes) {
                sb.append(String.format("%02x", b and 0xff.toByte()))
            }
            return sb.toString()
        }

        /**
         * Convert a hex string to byte array
         *
         */
        fun string2bytes(@Nullable hexString: String?): ByteArray {
            if (hexString == null) return ByteArray(0)
            val result = ByteArray(hexString.length / 2)
            for (i in result.indices) {
                val index = i * 2
                result[i] = hexString.substring(index, index + 2).toInt(16).toByte()
            }
            return result
        }

        /**
         * Merge multiple arrays intro one array
         *
         * @param arrays Arrays to merge
         * @return Merge array
         */
        fun mergeArrays(vararg arrays: ByteArray): ByteArray {
            var size = 0
            for (array: ByteArray in arrays) {
                size += array.size
            }
            val merged = ByteArray(size)
            var index = 0
            for (array: ByteArray in arrays) {
                System.arraycopy(array, 0, merged, index, array.size)
                index += array.size
            }
            return merged
        }
    }
    /**
     * Create a BluetoothBytesParser containing the byte array, the offset and the byteOrder.
     *
     * @param value     the byte array
     * @param offset    the offset from which parsing will start
     * @param byteOrder the byte order, either LITTLE_ENDIAN or BIG_ENDIAN
     * @throws IllegalArgumentException if the offset is negative or the byteOrder is not LITTLE_ENDIAN or BIG_ENDIAN
     * @throws NullPointerException if the value or byteOrder are null
     */
    /**
     * Create a BluetoothBytesParser containing the byte array and sets the byteOrder to LITTLE_ENDIAN.
     *
     * @param value the byte array
     * @throws NullPointerException if the value is null
     */
    /**
     * Create a BluetoothBytesParser containing the byte array, the offset and the byteOrder to LITTLE_ENDIAN.
     *
     * @param value  the byte array
     * @param offset the offset from which parsing will start
     * @throws IllegalArgumentException if the offset is negative
     * @throws NullPointerException     if the value is null
     */
    init {
        if (offset < 0) throw IllegalArgumentException(
            OFFSET_MUST_BE_GREATER_OR_EQUAL_TO_ZERO
        )
        Objects.requireNonNull(byteOrder)
        if (!(byteOrder == LITTLE_ENDIAN || byteOrder == BIG_ENDIAN)) throw IllegalArgumentException(
            "unsupported ByteOrder value"
        )
        mValue = Objects.requireNonNull(value)
        internalOffset = offset
        internalByteOrder = byteOrder
    }
}