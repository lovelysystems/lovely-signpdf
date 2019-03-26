package com.lovelysystems.signpdf.signer

import java.io.InputStream

interface Signer {
    fun sign(documentStream: InputStream): ByteArray?
}
