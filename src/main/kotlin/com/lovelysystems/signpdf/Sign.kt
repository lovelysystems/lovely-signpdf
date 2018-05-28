package com.lovelysystems.signpdf

import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.CMSTypedData
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.Selector
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.*

/**
 * Checks if the pdf content provided has a valid signature
 *
 * TODO: currently it is not checked if the document has been modified after signing
 */
fun checkSignature(pdfContent: ByteArray) {
    PDDocument.load(pdfContent).use { document ->
        val signatureDictionaries = document.signatureDictionaries
        if (signatureDictionaries.isEmpty()) {
            throw RuntimeException("no signature found")
        }
        for (sig in document.signatureDictionaries) {
            val contents = sig.cosObject.getDictionaryObject(COSName.CONTENTS) as COSString
            val buf = sig.getSignedContent(pdfContent)
            val signedData = CMSSignedData(CMSProcessableByteArray(buf), contents.bytes)
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
            break
        }
    }
}

class Sign(keyStoreContent: InputStream, password: CharArray) {

    private val privateKey: Key

    private val certChain: Array<out Certificate>

    private val gen: CMSSignedDataGenerator

    init {
        val keystore = KeyStore.getInstance("PKCS12")
        keystore.load(keyStoreContent, password)
        val alias = keystore.aliases().nextElement()
        privateKey = keystore.getKey(alias, password) as PrivateKey

        certChain = keystore.getCertificateChain(alias)
        val certList = ArrayList<Certificate>()
        certList.addAll(certChain)

        val certs = JcaCertStore(certList)
        gen = CMSSignedDataGenerator()

        if (privateKey.algorithm != "RSA") {
            throw RuntimeException("Private key must be RSA but found ${privateKey.algorithm}")
        }

        val cert = org.bouncycastle.asn1.x509.Certificate.getInstance(certChain[0].getEncoded())
        val sha1Signer = JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)

        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build()).build(
                sha1Signer,
                X509CertificateHolder(cert)
            )
        )
        gen.addCertificates(certs)
    }

    private val signatureInterface = SignatureInterface {
        val msg = CMSProcessableInputStream(it)
        val signedData = gen.generate(msg, false)
        signedData.encoded
    }

    fun sign(content: InputStream, output: OutputStream) {
        // create signature dictionary

        val document = PDDocument.load(content)

        val signature = PDSignature()
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
        signature.name = "Example User Hoschi"
        signature.location = "Los Angeles, CA well well"
        signature.reason = "Testing"
        // TODO extract the above details from the signing certificate? Reason as a parameter?

        // the signing date, needed for valid signature
        // TODO: ensure propper timezone
        signature.signDate = Calendar.getInstance()

        val signatureOptions = SignatureOptions()
        // Size can vary, but should be enough for purpose.
        signatureOptions.preferredSignatureSize = SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2
        // register signature dictionary and sign interface
        document.addSignature(signature, signatureInterface, signatureOptions)

        // write incremental (only for signing purpose)
        document.saveIncremental(output)
        document.close()
    }
}

/**
 * Wraps a InputStream into a CMSProcessable object for bouncy castle. It's a memory saving
 * alternative to the [CMSProcessableByteArray][org.bouncycastle.cms.CMSProcessableByteArray]
 * class.
 */
private class CMSProcessableInputStream(private val inputStream: InputStream) : CMSTypedData {

    companion object {
        private val ct: ASN1ObjectIdentifier = ASN1ObjectIdentifier(CMSObjectIdentifiers.data.id)
    }

    override fun getContent(): Any {
        return inputStream
    }

    override fun write(out: OutputStream) {
        // read the content only one time
        IOUtils.copy(inputStream, out)
        inputStream.close()
    }

    override fun getContentType(): ASN1ObjectIdentifier {
        return ct
    }
}