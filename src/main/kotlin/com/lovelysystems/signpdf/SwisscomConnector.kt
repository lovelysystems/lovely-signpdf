package com.lovelysystems.signpdf

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStream
import java.lang.Exception
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.Security
import java.security.cert.*
import java.util.*
import javax.net.ssl.*

class SwisscomConnector(url: String, keyStoreContent: InputStream, password: CharArray, timeout: Int = 90) {

    private val url: String = url

    private val timeout: Int = timeout

    private val serverCert: Certificate

    private val clientCert: Certificate

    private val privateKey: PrivateKey


    init {
        val keystore = KeyStore.getInstance("JKS")
        keystore.load(keyStoreContent, password)
        serverCert = keystore.getCertificate("aisca")
        clientCert = keystore.getCertificate("lovely")
        privateKey = keystore.getKey("lovely", password) as PrivateKey
        Security.addProvider(BouncyCastleProvider())
    }

    fun getConnection(): URLConnection? {
        val context = SSLContext.getInstance("TLS")
        context.init(arrayOf(object : X509KeyManager {
            override fun getClientAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
                return arrayOf(clientCert.toString())
            }

            override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String> {
                return arrayOf(serverCert.toString())
            }

            override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?): String {
                return serverCert.toString()
            }

            override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?): String {
                return clientCert.toString()
            }

            override fun getCertificateChain(p0: String?): Array<X509Certificate> {
                return arrayOf(clientCert as X509Certificate)
            }

            override fun getPrivateKey(p0: String?): PrivateKey {
                return privateKey
            }
        }), arrayOf(object : X509TrustManager {

            var trustedIssures: Array<out X509Certificate>? = null

            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
                // not relevant here
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain!!.size < 2)
                    throw CertificateException("Error when validating server certificate")

                val certToVerify = chain[0]
                val cf = CertificateFactory.getInstance("X.509")
                val cp = cf.generateCertPath(arrayListOf(certToVerify))

                val trustAnchor = TrustAnchor(chain[1], null)

                try {
                    val cpv = CertPathValidator.getInstance("PKIX")

                    val pkixParams = PKIXParameters(Collections.singleton(trustAnchor))
                    pkixParams.isRevocationEnabled = false

                    cpv.validate(cp, pkixParams)
                            ?: throw CertificateException("Error when validating server certificate")

                    trustedIssures = chain
                } catch (e: Exception) {
                    throw CertificateException("Error when validating server certificate")
                }

            }

            override fun getAcceptedIssuers(): Array<out X509Certificate>? {
                return trustedIssures
            }

        }), null)
        val sslFactory = context.socketFactory
        val url = URL(url)
        val connection = url.openConnection()
        if (connection is HttpsURLConnection)
            (connection as HttpsURLConnection).sslSocketFactory = sslFactory

        connection.connectTimeout = timeout
        return connection
    }
}