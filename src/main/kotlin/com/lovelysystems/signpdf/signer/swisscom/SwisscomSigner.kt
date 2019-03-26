package com.lovelysystems.signpdf.signer.swisscom

import com.lovelysystems.signpdf.signer.Signer
import java.io.InputStream
import java.security.MessageDigest

class SwisscomSigner(soap: Soap): Signer {
    private val soap: Soap = soap

    override fun sign(documentStream: InputStream): ByteArray? {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        var i = documentStream.read()
        while(i != -1) {
            messageDigest.update(i.toByte())
            i = documentStream.read()
        }
        return soap.getSignature(messageDigest.digest())
    }
}