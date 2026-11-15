package com.example.nfc

object NdefUriEncoder {

  const val MAX_NDEF_BYTES = NdefMessageCodec.MAX_NDEF_BYTES

  fun normalizeUri(rawInput: String): String {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return ""
    val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*").matches(trimmed)
    return if (hasScheme) trimmed else "https://$trimmed"
  }

  fun isValidUri(uri: String): Boolean {
    if (uri.isBlank() || uri.any { it.isWhitespace() }) return false
    val schemeSeparator = uri.indexOf(':')
    if (schemeSeparator <= 0) return false
    val remainder = uri.substring(schemeSeparator + 1).removePrefix("//")
    return remainder.isNotBlank()
  }

  fun encodeUriNdefMessage(rawUri: String): ByteArray {
    val normalized = normalizeUri(rawUri)
    if (normalized.isEmpty()) return ByteArray(0)
    return NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url(normalized)))
  }
}
