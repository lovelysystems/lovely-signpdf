package com.lovelysystems.signpdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.*

class PDF(inputStream: InputStream, outputStream: OutputStream, signName: String, signReason: String, signLocation: String, signContact: String, certificationLevel: Int) {

    private val document = PDDocument.load(inputStream)

    private val certificationLevel = certificationLevel

    private val outputStream = outputStream

    private val signName = signName

    private val signReason = signReason

    private val signLocation = signLocation

    private val signContact = signContact

    private var externalSigningSupport: ExternalSigningSupport? = null

    fun getPdfHash(signDate: Calendar, estimatedSize: Int, hashAlgorithm: String, isTimestampOnly: Boolean): ByteArray {
        val signature = PDSignature()
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
        signature.name = signName
        signature.location = signLocation
        signature.reason = signReason
        signature.contactInfo = signContact
        signature.signDate = Calendar.getInstance()

        val signatureOptions = SignatureOptions()
        // Size can vary, but should be enough for purpose.
        signatureOptions.preferredSignatureSize = SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2
        // register signature dictionary and sign interface
        document.addSignature(signature, signatureOptions)
        externalSigningSupport = document.saveIncrementalForExternalSigning(outputStream)
        val stream = externalSigningSupport!!.content

        val messageDigest = MessageDigest.getInstance("SHA-256")
        var i = stream.read()
        while(i != -1) {
            messageDigest.update(i.toByte())
            i = stream.read()
        }
        return messageDigest.digest()
    }

    fun createSignedPdf(externalSignature: ByteArray, estimatedSize: Int) {
        externalSigningSupport!!.setSignature(externalSignature)
    }

    fun close() {
        document.close()
    }
}
