package com.lovelysystems.signpdf.signer.swisscom

import org.bouncycastle.util.encoders.Base64
import org.w3c.dom.Element
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.soap.MessageFactory
import javax.xml.soap.SOAPMessage
import kotlin.math.roundToInt

class Soap(claimIdentity: String, connector: SwisscomConnector) {
    private val claimedIdentity: String = claimIdentity

    private val digestMethodAlgorithmURL: String = "http://www.w3.org/2001/04/xmlenc#sha256"

    private val signatureType: String = "urn:ietf:rfc:3369"

    private val requestResultSuccess = "urn:oasis:names:tc:dss:1.0:resultmajor:Success"

    private val connector: SwisscomConnector = connector


    fun getSignature(documentHash: ByteArray): ByteArray? {
        val soapMessage = createRequestMessage(documentHash)
        val response = sendRequest(soapMessage)
        val responseResult = getTextFromXmlText(response, "ResultMajor")
        val singingSuccess = responseResult != null && requestResultSuccess.equals(responseResult.get(0))

        if (!singingSuccess) {
            throw Exception()
        }

        return Base64.decode(getTextFromXmlText(response, "Base64Signature")?.get(0))

    }


    private fun createRequestMessage(documentHash: ByteArray): SOAPMessage {
        val requestId = getRequestId()
        val messageFactory = MessageFactory.newInstance()
        val soapMessage = messageFactory.createMessage()
        val soapPart = soapMessage.soapPart

        val envelope = soapPart.envelope
        envelope.removeNamespaceDeclaration("SOAP-ENV")
        envelope.setPrefix("soap")
        envelope.addAttribute(QName("xmlns"), "urn:oasis:names:tc:dss:1.0:core:schema")
        envelope.addNamespaceDeclaration("dsig", "http://www.w3.org/2000/09/xmldsig#")
        envelope.addNamespaceDeclaration("sc", "http://ais.swisscom.ch/1.0/schema")
        envelope.addNamespaceDeclaration("ais", "http://service.ais.swisscom.com/")

        //SOAP Header
        val soapHeader = envelope.header
        soapHeader.removeNamespaceDeclaration("SOAP-ENV")
        soapHeader.setPrefix("soap")

        // SOAP Body
        val soapBody = envelope.getBody()
        soapBody.removeNamespaceDeclaration("SOAP-ENV")
        soapBody.setPrefix("soap")

        val signElement = soapBody.addChildElement("sign", "ais")

        val requestElement = signElement.addChildElement("SignRequest")
        requestElement.addAttribute(QName("Profile"), "http://ais.swisscom.ch/1.1")
        requestElement.addAttribute(QName("RequestID"), requestId)
        val inputDocumentsElement = requestElement.addChildElement("InputDocuments")

        val documentHashElement = inputDocumentsElement.addChildElement("DocumentHash")
        val digestMethodElement = documentHashElement.addChildElement("DigestMethod", "dsig")
        digestMethodElement.addAttribute(QName("Algorithm"), digestMethodAlgorithmURL)
        val digestValueElement = documentHashElement.addChildElement("DigestValue", "dsig")

        val s = String(Base64.encode(documentHash))
        digestValueElement.addTextNode(s)

        val optionalInputsElement = requestElement.addChildElement("OptionalInputs")
        val claimedIdentityElement = optionalInputsElement.addChildElement("ClaimedIdentity", "")
        val claimedIdNameElement = claimedIdentityElement.addChildElement("Name")
        claimedIdNameElement.addTextNode(claimedIdentity)

        val signatureTypeElement = optionalInputsElement.addChildElement("SignatureType")
        signatureTypeElement.addTextNode(signatureType)

        val addSignatureStandardElement = optionalInputsElement.addChildElement("SignatureStandard", "sc")
        addSignatureStandardElement.setValue("PADES")

        // Always add revocation information
        optionalInputsElement.addChildElement("AddRevocationInformation", "sc")

        soapMessage.saveChanges()

        return soapMessage
    }

    private fun sendRequest(soapMsg: SOAPMessage): String {
        val conn = connector.getConnection()!!
        if (conn is HttpsURLConnection) {
            conn.requestMethod = "POST"
        }

        conn.allowUserInteraction = true
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/xml charset=utf-8")

        val out = OutputStreamWriter(conn.getOutputStream())
        val baos = ByteArrayOutputStream()

        soapMsg.writeTo(baos)
        val msg = baos.toString()

        out.write(msg)
        out.flush()

        out.close()

        val ir = BufferedReader(InputStreamReader(conn.getInputStream()))

        var response = ""
        var line = ir.readLine()
        while (line != null) {
            response = if (response.isNotEmpty()) "$response $line" else "$response$line"
            line = ir.readLine()
        }

        ir.close()

        return response

    }

    private fun getTextFromXmlText(soapResponseText: String, nodeName: String): ArrayList<String>? {
        val element = getNodeList(soapResponseText);

        return getNodesFromNodeList(element, nodeName);
    }

    private fun getNodesFromNodeList(element: Element, nodeName: String): ArrayList<String>? {
        var returnlist: ArrayList<String>? = null;
        val nl = element.getElementsByTagName(nodeName);

        for (i in 0..(nl.length - 1)) {
            if (nodeName.equals(nl.item(i).getNodeName())) {
                if (returnlist == null) {
                    returnlist = ArrayList<String>();
                }
                returnlist.add(nl.item(i).getTextContent());
            }

        }

        return returnlist;
    }

    private fun getNodeList(xmlString: String): Element {

        val dbf = DocumentBuilderFactory.newInstance();
        val db = dbf.newDocumentBuilder();
        val bis = ByteArrayInputStream(xmlString.toByteArray());
        val doc = db.parse(bis);

        return doc.getDocumentElement();
    }

    private fun getRequestId(): String {
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm:ss:SSSS")
        val randomNumber = (Math.random() * 1000).roundToInt()
        return df.format(Date()) + randomNumber.toString()
    }
}