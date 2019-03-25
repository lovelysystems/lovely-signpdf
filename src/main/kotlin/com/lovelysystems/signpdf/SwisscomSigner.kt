package com.lovelysystems.signpdf

class SwisscomSigner(soap: Soap): Signer {
    private val soap: Soap = soap

    override fun sign(documentHash: ByteArray): ByteArray? {
        return soap.getSignature(documentHash)
    }
}