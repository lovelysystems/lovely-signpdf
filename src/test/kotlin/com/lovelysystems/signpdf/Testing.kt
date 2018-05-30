package com.lovelysystems.signpdf

import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.util.Selector

/**
 * Checks if the first signature in the pdf is valid and returns it
 *
 * TODO: currently it is not checked if the document has been modified after signing
 */
fun validateAndGetFirstSignature(pdfContent: ByteArray): PDSignature {
    PDDocument.load(pdfContent).use { document ->
        val signatureDictionaries = document.signatureDictionaries
        if (signatureDictionaries.isEmpty()) {
            throw RuntimeException("no signature found")
        }
        val sig = signatureDictionaries.first()
        val contents = sig.cosObject.getDictionaryObject(COSName.CONTENTS) as COSString
        val buf = sig.getSignedContent(pdfContent)
        val signedData =
            CMSSignedData(CMSProcessableByteArray(buf), contents.bytes)
        val certificatesStore = signedData.certificates
        val signers = signedData.signerInfos.signers
        val signerInformation = signers.iterator().next()
        @Suppress("UNCHECKED_CAST")
        val matches = certificatesStore.getMatches(signerInformation.sid as Selector<X509CertificateHolder>)
        val certificateHolder = matches.iterator().next() as X509CertificateHolder
        val certFromSignedData = JcaX509CertificateConverter().getCertificate(certificateHolder)
        if (!signerInformation.verify(JcaSimpleSignerInfoVerifierBuilder().build(certFromSignedData))) {
            throw RuntimeException("Signature verification failed")
        }
        return sig
    }
}
