package com.universalremote.adapters.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Subconjunto mínimo de protobuf (varint + length-delimited) suficiente para los mensajes de
 * Android TV Remote v2; evita arrastrar protobuf-lite y su generador de código.
 */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long) = apply {
        tag(field, WIRE_VARINT)
        varint(out, value)
    }

    fun int(field: Int, value: Int) = int(field, value.toLong())

    fun bool(field: Int, value: Boolean) = int(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, WIRE_LENGTH)
        varint(out, value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = varint(out, ((field shl 3) or wire).toLong())

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH = 2
        const val WIRE_FIXED32 = 5

        fun varint(out: OutputStream, value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write(v.toInt())
        }
    }
}

/** Mensaje decodificado: número de campo → valores (Long para varint, ByteArray para length-delimited). */
internal class ProtoMessage(private val fields: Map<Int, List<Any>>) {
    fun has(field: Int): Boolean = field in fields
    fun int(field: Int): Long? = fields[field]?.firstOrNull() as? Long
    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = mutableMapOf<Int, MutableList<Any>>()
            var pos = 0
            fun readVarint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (pos >= data.size) throw IOException("Varint truncado")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IOException("Varint demasiado largo")
                }
            }
            while (pos < data.size) {
                val key = readVarint()
                val field = (key ushr 3).toInt()
                val value: Any = when ((key and 0x7).toInt()) {
                    ProtoWriter.WIRE_VARINT -> readVarint()
                    ProtoWriter.WIRE_LENGTH -> {
                        val len = readVarint().toInt()
                        if (len < 0 || pos + len > data.size) throw IOException("Campo truncado")
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    ProtoWriter.WIRE_FIXED64 -> { pos += 8; continue }
                    ProtoWriter.WIRE_FIXED32 -> { pos += 4; continue }
                    else -> throw IOException("Tipo de campo protobuf no soportado")
                }
                fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

/** Ambos canales (6466/6467) enmarcan cada mensaje con su longitud en varint. */
internal object ProtoFraming {
    const val MAX_MESSAGE_BYTES = 64 * 1024

    fun write(out: OutputStream, message: ByteArray) {
        val buffer = ByteArrayOutputStream(message.size + 5)
        ProtoWriter.varint(buffer, message.size.toLong())
        buffer.write(message)
        out.write(buffer.toByteArray())
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var shift = 0
        var length = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("El TV cerró la conexión")
            length = length or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw IOException("Longitud de mensaje inválida")
        }
        if (length > MAX_MESSAGE_BYTES) throw IOException("Mensaje demasiado grande: $length")
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) throw EOFException("El TV cerró la conexión")
            read += n
        }
        return data
    }
}
