package com.example.nfc

class T4tApduEngine {

  enum class SelectedFile {
    NONE,
    CAPABILITY_CONTAINER,
    NDEF_FILE,
  }

  data class ApduOutcome(
    val responseApdu: ByteArray,
    val completedNdefRead: Boolean = false,
    val completedNdefWrite: ByteArray? = null,
    val rejectedBecauseStandby: Boolean = false,
  )

  private var selectedFile: SelectedFile = SelectedFile.NONE
  private var writeBuffer = ByteArray(2 + NdefMessageCodec.MAX_NDEF_BYTES)
  private var writeSessionDirty = false

  fun reset() {
    selectedFile = SelectedFile.NONE
    writeSessionDirty = false
  }

  fun processApdu(
    commandApdu: ByteArray,
    isEmulationEnabled: Boolean,
    ndefMessageBytes: ByteArray,
    isReadOnly: Boolean = true,
  ): ApduOutcome {
    if (commandApdu.size < 4) {
      return ApduOutcome(SW_WRONG_LENGTH)
    }

    val cla = commandApdu[0].toInt() and 0xFF
    val ins = commandApdu[1].toInt() and 0xFF
    val p1 = commandApdu[2].toInt() and 0xFF
    val p2 = commandApdu[3].toInt() and 0xFF

    if (cla != 0x00) {
      return ApduOutcome(SW_CLA_NOT_SUPPORTED)
    }

    return when (ins) {
      INS_SELECT -> handleSelect(commandApdu, p1, p2, isEmulationEnabled, ndefMessageBytes, isReadOnly)
      INS_READ_BINARY -> handleReadBinary(commandApdu, p1, p2, isEmulationEnabled, ndefMessageBytes, isReadOnly)
      INS_UPDATE_BINARY -> handleUpdateBinary(commandApdu, p1, p2, isEmulationEnabled, ndefMessageBytes, isReadOnly)
      else -> ApduOutcome(SW_INS_NOT_SUPPORTED)
    }
  }

  private fun handleSelect(
    apdu: ByteArray,
    p1: Int,
    p2: Int,
    isEmulationEnabled: Boolean,
    ndefMessageBytes: ByteArray,
    isReadOnly: Boolean,
  ): ApduOutcome {
    if (apdu.size < 5) return ApduOutcome(SW_WRONG_LENGTH)
    val lc = apdu[4].toInt() and 0xFF
    if (apdu.size < 5 + lc) return ApduOutcome(SW_WRONG_LENGTH)
    val data = apdu.copyOfRange(5, 5 + lc)

    val canAnswerAid = isEmulationEnabled && (ndefMessageBytes.isNotEmpty() || !isReadOnly)

    if (p1 == 0x04 && p2 == 0x00) {
      if (!data.contentEquals(NDEF_AID)) {
        selectedFile = SelectedFile.NONE
        return ApduOutcome(SW_FILE_NOT_FOUND)
      }
      if (!canAnswerAid) {
        selectedFile = SelectedFile.NONE
        return ApduOutcome(SW_FILE_NOT_FOUND, rejectedBecauseStandby = true)
      }
      selectedFile = SelectedFile.NONE
      writeSessionDirty = false
      return ApduOutcome(SW_OK)
    }

    if (p1 == 0x00 && (p2 == 0x0C || p2 == 0x00)) {
      if (!canAnswerAid) {
        selectedFile = SelectedFile.NONE
        return ApduOutcome(SW_FILE_NOT_FOUND, rejectedBecauseStandby = true)
      }
      return when {
        data.contentEquals(CC_FILE_ID) -> {
          selectedFile = SelectedFile.CAPABILITY_CONTAINER
          ApduOutcome(SW_OK)
        }
        data.contentEquals(NDEF_FILE_ID) -> {
          selectedFile = SelectedFile.NDEF_FILE
          syncWriteBuffer(ndefMessageBytes)
          ApduOutcome(SW_OK)
        }
        else -> {
          selectedFile = SelectedFile.NONE
          ApduOutcome(SW_FILE_NOT_FOUND)
        }
      }
    }

    return ApduOutcome(SW_WRONG_P1_P2)
  }

  private fun handleReadBinary(
    apdu: ByteArray,
    p1: Int,
    p2: Int,
    isEmulationEnabled: Boolean,
    ndefMessageBytes: ByteArray,
    isReadOnly: Boolean,
  ): ApduOutcome {
    if (!isEmulationEnabled || (ndefMessageBytes.isEmpty() && isReadOnly)) {
      return ApduOutcome(SW_CONDITIONS_NOT_SATISFIED)
    }

    val fileBytes =
      when (selectedFile) {
        SelectedFile.CAPABILITY_CONTAINER -> buildCapabilityContainer(isReadOnly)
        SelectedFile.NDEF_FILE -> buildNdefFile(ndefMessageBytes)
        SelectedFile.NONE -> return ApduOutcome(SW_COMMAND_NOT_ALLOWED)
      }

    val offset = ((p1 and 0x7F) shl 8) or p2
    if (offset > fileBytes.size) {
      return ApduOutcome(SW_WRONG_P1_P2)
    }

    val requestedLe =
      if (apdu.size >= 5) {
        val rawLe = apdu[4].toInt() and 0xFF
        if (rawLe == 0) 256 else rawLe
      } else {
        fileBytes.size - offset
      }

    val available = fileBytes.size - offset
    val bytesToRead = minOf(requestedLe, available)
    val slice = fileBytes.copyOfRange(offset, offset + bytesToRead)

    val response = ByteArray(slice.size + 2)
    System.arraycopy(slice, 0, response, 0, slice.size)
    response[slice.size] = SW_OK[0]
    response[slice.size + 1] = SW_OK[1]

    val completedNdefRead =
      selectedFile == SelectedFile.NDEF_FILE &&
        fileBytes.size > 2 &&
        offset + bytesToRead >= fileBytes.size &&
        bytesToRead > 0

    return ApduOutcome(responseApdu = response, completedNdefRead = completedNdefRead)
  }

  private fun handleUpdateBinary(
    apdu: ByteArray,
    p1: Int,
    p2: Int,
    isEmulationEnabled: Boolean,
    ndefMessageBytes: ByteArray,
    isReadOnly: Boolean,
  ): ApduOutcome {
    if (!isEmulationEnabled || isReadOnly || selectedFile != SelectedFile.NDEF_FILE) {
      return ApduOutcome(SW_SECURITY_STATUS_NOT_SATISFIED)
    }
    if (apdu.size < 5) return ApduOutcome(SW_WRONG_LENGTH)
    val lc = apdu[4].toInt() and 0xFF
    if (apdu.size < 5 + lc) return ApduOutcome(SW_WRONG_LENGTH)

    val offset = ((p1 and 0x7F) shl 8) or p2
    if (offset + lc > writeBuffer.size) {
      return ApduOutcome(SW_WRONG_P1_P2)
    }

    if (!writeSessionDirty) {
      syncWriteBuffer(ndefMessageBytes)
      writeSessionDirty = true
    }

    System.arraycopy(apdu, 5, writeBuffer, offset, lc)

    val nlen = ((writeBuffer[0].toInt() and 0xFF) shl 8) or (writeBuffer[1].toInt() and 0xFF)
    val completedWrite =
      if (offset == 0 && lc >= 2 && nlen > 0 && nlen <= NdefMessageCodec.MAX_NDEF_BYTES) {
        writeSessionDirty = false
        writeBuffer.copyOfRange(2, 2 + nlen)
      } else {
        null
      }

    return ApduOutcome(responseApdu = SW_OK, completedNdefWrite = completedWrite)
  }

  private fun syncWriteBuffer(ndefMessageBytes: ByteArray) {
    writeBuffer.fill(0)
    val clamped = minOf(ndefMessageBytes.size, NdefMessageCodec.MAX_NDEF_BYTES)
    writeBuffer[0] = ((clamped ushr 8) and 0xFF).toByte()
    writeBuffer[1] = (clamped and 0xFF).toByte()
    if (clamped > 0) {
      System.arraycopy(ndefMessageBytes, 0, writeBuffer, 2, clamped)
    }
  }

  private fun buildNdefFile(ndefMessageBytes: ByteArray): ByteArray {
    val nlen = ndefMessageBytes.size
    return ByteArray(2 + nlen).apply {
      this[0] = ((nlen ushr 8) and 0xFF).toByte()
      this[1] = (nlen and 0xFF).toByte()
      System.arraycopy(ndefMessageBytes, 0, this, 2, nlen)
    }
  }

  companion object {
    private const val INS_SELECT = 0xA4
    private const val INS_READ_BINARY = 0xB0
    private const val INS_UPDATE_BINARY = 0xD6

    val NDEF_AID =
      byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
    val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
    val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

    fun buildCapabilityContainer(isReadOnly: Boolean): ByteArray =
      byteArrayOf(
        0x00,
        0x0F,
        0x20,
        0x00,
        0xFF.toByte(),
        0x00,
        0xFF.toByte(),
        0x04,
        0x06,
        0xE1.toByte(),
        0x04,
        0x08,
        0x00,
        0x00,
        if (isReadOnly) 0xFF.toByte() else 0x00,
      )

    val CAPABILITY_CONTAINER_FILE = buildCapabilityContainer(isReadOnly = true)

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
    val SW_SECURITY_STATUS_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())
    val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
    val SW_COMMAND_NOT_ALLOWED = byteArrayOf(0x69, 0x86.toByte())
    val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_WRONG_P1_P2 = byteArrayOf(0x6A, 0x86.toByte())
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
  }
}
