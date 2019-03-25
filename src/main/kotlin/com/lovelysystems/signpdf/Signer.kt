package com.lovelysystems.signpdf

interface Signer {
    fun sign(documentHash: ByteArray): ByteArray?
}
