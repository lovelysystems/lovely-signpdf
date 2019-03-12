/**
 * Creates SOAP requests with hash from a pdf document and send it to a server. If server sends a response with a signature
 * this will be add to a pdf.
 *
 * Created:
 * 03.12.13 KW49 14:51
 * </p>
 * Last Modification:
 * 17.02.2014 15:13
 * <p/>
 * Version:
 * 1.0.0
 * </p>
 * Copyright:
 * Copyright (C) 2013. All rights reserved.
 * </p>
 * License:
 * Licensed under the Apache License, Version 2.0 or later; see LICENSE.md
 * </p>
 * Author:
 * Swisscom (Schweiz) AG
 */

package com.swisscom.ais.itext;

import java.io.*;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.itextpdf.text.pdf.codec.Base64;
import com.swisscom.ais.itext.Include.RequestType;

public class Soap {

    /**
     * Constant for timestamp urn
     */
    private static final String _TIMESTAMP_URN = "urn:ietf:rfc:3161";

    /**
     * Constant for CMS urn
     */
    private static final String _CMS_URN = "urn:ietf:rfc:3369";

    /**
     * Path to configuration file. Can also set in constructor
     */
    private String _cfgPath = "signpdf.properties";

    /**
     * Properties from properties file
     */
    private Properties properties;

    /**
     * File path of private key
     */
    private String _privateKeyName;

    /**
     * File path of server certificate
     */
    private String _serverCertPath;

    /**
     * File patj of client certificate
     */
    private String _clientCertPath;

    /**
     * Url of dss server
     */
    private String _url;

    /**
     * Connection timeout in seconds
     */
    private int _timeout;

    /**
     * If set to true debug information will be print otherwise not
     */
    public static boolean _debugMode = false;

    /**
     * If set to true verbose information will be print otherwise not
     */
    public static boolean _verboseMode = false;
    
    /**
     * Constructor. Set parameter and load properties from file. Connection properties will be set and check if all needed
     * files exist
     *
     * @param verboseOutput    If true verbose information will be print out
     * @param debugMode        If true debug information will be print out
     * @param propertyFilePath Path of property file
     * @throws FileNotFoundException If a file do not exist. E.g. property file, certificate, input pdf etc
     */
    public Soap(boolean verboseOutput, boolean debugMode, @Nullable String propertyFilePath) throws FileNotFoundException {

        Soap._verboseMode = verboseOutput;
        Soap._debugMode = debugMode;

        if (propertyFilePath != null) {
            _cfgPath = propertyFilePath;

          properties = new Properties();
  
          try {
              properties.load(new FileReader(_cfgPath));
          } catch (IOException e) {
              throw new FileNotFoundException(("Could not load property file"));
          }
        }
        else { // propertyFilePath==null
          properties=System.getProperties();
        }

        setConnectionProperties();
        checkFilesExistsAndIsFile(new String[]{this._clientCertPath, this._privateKeyName, this._serverCertPath});

    }

    /**
     * Set connection properties from property file. Also convert timeout from seconds to milliseconds. If timeout can not
     * be readed from properties file it will use standard value 90 seconds
     */
    private void setConnectionProperties() {

        this._clientCertPath = properties.getProperty("CERT_FILE");
        this._privateKeyName = properties.getProperty("CERT_KEY");
        this._serverCertPath = properties.getProperty("SSL_CA");
        this._url = properties.getProperty("URL");
        try {
            this._timeout = Integer.parseInt(properties.getProperty("TIMEOUT_CON"));
            this._timeout *= 1000;
        } catch (NumberFormatException e) {
            this._timeout = 90 * 1000;
        }

    }

    /**
     * Read signing options from properties. Depending on parameters here will be decided which type of signature will be used.
     *
     * @param signatureType     Type of signature e.g. timestamp, ondemand or static
     * @param fileIn            File path of input pdf document
     * @param fileOut           File path of output pdf document which will be the signed one
     * @param signingReason     Reason for signing a document
     * @param signingLocation   Location where a document was signed
     * @param signingContact    Person who signed document
     * @param distinguishedName Information about signer e.g. name, country etc.
     * @param msisdn            Mobile id for sending message to signer
     * @param msg               Message which will be send to signer if msisdn is set
     * @param language          Language of message
     * @throws Exception If parameters are not set or signing failed
     */
    public void sign(@Nonnull Include.Signature signatureType, @Nonnull InputStream input, @Nonnull OutputStream output,
                     @Nullable String signingName, @Nullable String signingReason, @Nullable String signingLocation, @Nullable String signingContact,
                     @Nullable int certificationLevel, @Nullable String distinguishedName, @Nullable String msisdn,
                     @Nullable String msg, @Nullable String language, @Nullable String serialnumber)
            throws Exception {

    	// LATER throw a specific Exception and not the generic one
        Include.HashAlgorithm hashAlgo = Include.HashAlgorithm.valueOf(properties.getProperty("DIGEST_METHOD").trim().toUpperCase());

        String claimedIdentity = properties.getProperty("CUSTOMER");
        String claimedIdentityPropName = signatureType.equals(Include.Signature.ONDEMAND) ?
                "KEY_ONDEMAND" : signatureType.equals(Include.Signature.STATIC) ? "KEY_STATIC" : null;
        if (claimedIdentityPropName != null) {
            claimedIdentity = claimedIdentity.concat(":" + properties.getProperty(claimedIdentityPropName));
        }

        PDF pdf = new PDF(input, output, signingName, signingReason, signingLocation, signingContact, certificationLevel);

        try {
            String requestId = getRequestId();

            if (msisdn != null && msg != null && language != null && signatureType.equals(Include.Signature.ONDEMAND)) {
            	
            	// On-Demand signature WITH step-up
                if (_debugMode) {
                    System.out.println("Going to sign ondemand with mobile id");
                }
                Calendar signingTime = Calendar.getInstance();
                // Add 3 Minutes to move signing time within the OnDemand Certificate Validity
                // This is only relevant in case the signature does not include a timestamp
                signingTime.add(Calendar.MINUTE, 3); 
                
                // Read polling interval from the properties
                if (properties.getProperty("POLLING_INTERVAL") == null) {
                	throw new Exception("Polling interval is missing in the configuration.");
                }
                
                // Default values for polling interval and poll retries: 18000 milliseconds and 10 retries
                // These values are equal to the server timeout when using MID as step-up (180 seconds) 
                // For PwdOTP, higher values should be configured in signpdf.properties
                long pollingInterval = 18000;
                int pollRetries = 10;
                
                // Read configuration
                try {
	                if (properties.getProperty("POLLING_INTERVAL") != null) {
	                	pollingInterval = Long.parseLong(properties.getProperty("POLLING_INTERVAL"));
	                }
	                
	                if (properties.getProperty("POLL_RETRIES") != null) {
	                	pollRetries = Integer.parseInt(properties.getProperty("POLL_RETRIES"));
	                }
                
                } catch (NumberFormatException nfe) {
                	if (_debugMode) {
                		System.out.println("Error reading configuration. Using default value.");
                		System.out.println("Error message: " + nfe.getMessage());
                	}
                }
                           
                signDocumentOnDemandCertStepUp(
                		new PDF[]{pdf}, 
                		signingTime, 
                		hashAlgo, 
                		_url, 
                		claimedIdentity, 
                		distinguishedName, 
                		msisdn, 
                		msg, 
                		language, 
                		serialnumber, 
                		requestId,
                		pollingInterval,
                		pollRetries);
            
            } else if (signatureType.equals(Include.Signature.ONDEMAND)) {
                
            	// On-Demand signature WITHOUT step-up
            	if (_debugMode) {
                    System.out.println("Going to sign with ondemand");
                }
                Calendar signingTime = Calendar.getInstance();
                // Add 3 Minutes to move signing time within the OnDemand Certificate Validity
                // This is only relevant in case the signature does not include a timestamp
                signingTime.add(Calendar.MINUTE, 3);
                signDocumentOnDemandCert(new PDF[]{pdf}, hashAlgo, signingTime, _url, distinguishedName, claimedIdentity, requestId);
            
            } else if (signatureType.equals(Include.Signature.TIMESTAMP)) {
            	
            	// Timestamp only
                if (_debugMode) {
                    System.out.println("Going to sign only with timestamp");
                }
                signDocumentTimestampOnly(new PDF[]{pdf}, hashAlgo, Calendar.getInstance(), _url, claimedIdentity,
                        requestId);
                
            } else if (signatureType.equals(Include.Signature.STATIC)) {
            	
            	// Static signature
                if (_debugMode) {
                    System.out.println("Going to sign with static cert");
                }
                signDocumentStaticCert(new PDF[]{pdf}, hashAlgo, Calendar.getInstance(), _url, claimedIdentity, requestId);
                
            } else {
                throw new Exception("Wrong or missing parameters. Can not find a signature type.");
            }
        
        } catch (Exception e) {
            throw new Exception(e);
        } finally {
            pdf.close();
        }
    }

    /**
     * Create SOAP request message and sign document with on demand certificate and authenticate with MobileID or PwdOTP
     *
     * @param pdfs              Pdf input files
     * @param signDate          Date when document(s) will be signed
     * @param hashAlgo          Hash algorithm to use for signature
     * @param serverURI         Server uri where to send the request
     * @param claimedIdentity   Signers identity
     * @param distinguishedName Information about signer e.g. name, country etc.
     * @param phoneNumber       Phone number for declaration of will (step-up)
     * @param certReqMsg        Message which the signer get on his phone
     * @param certReqMsgLang    Language of message
     * @param requestId         An id for the request
     * @param pollingInterval	Interval between polling requests for asynchronous signing request.
     * @throws Exception If hash or request can not be generated or document can not be signed.
     */
    private void signDocumentOnDemandCertStepUp(@Nonnull PDF pdfs[], @Nonnull Calendar signDate, @Nonnull Include.HashAlgorithm hashAlgo,
                                                  @Nonnull String serverURI, @Nonnull String claimedIdentity,
                                                  @Nonnull String distinguishedName, @Nonnull String phoneNumber, @Nonnull String certReqMsg,
                                                  @Nonnull String certReqMsgLang, @Nonnull String certReqSerialNumber, String requestId,
                                                  @Nonnull long pollingInterval,
                                                  @Nonnull int pollRetries) throws Exception {
        String[] additionalProfiles;

        
        // ASYNCHRON and REDIRECT profile are needed for PwdOTP (for MID only ASYNCHRON is necessary)
        if (pdfs.length > 1) {
            additionalProfiles = new String[4];
        	additionalProfiles[3] = Include.AdditionalProfiles.BATCH.getProfileName();
        } else {
            additionalProfiles = new String[3];
        }
        
        additionalProfiles[0] = Include.AdditionalProfiles.ON_DEMAND_CERTIFICATE.getProfileName();
        additionalProfiles[1] = Include.AdditionalProfiles.REDIRECT.getProfileName();
        
        // With the new interface version (Reference Guide 2.x), all on-demand signatures with step-up must be asynchronous
        additionalProfiles[2] = Include.AdditionalProfiles.ASYNCHRON.getProfileName();
        
        int estimatedSize = getEstimatedSize(false);

        byte[][] pdfHash = new byte[pdfs.length][];
        for (int i = 0; i < pdfs.length; i++) {
            pdfHash[i] = pdfs[i].getPdfHash(signDate, estimatedSize, hashAlgo.getHashAlgorythm(), false);
        }

        SOAPMessage sigReqMsg = createRequestMessage(Include.RequestType.SignRequest, hashAlgo.getHashUri(), true,
                pdfHash, additionalProfiles,
                claimedIdentity, Include.SignatureType.CMS.getSignatureType(), distinguishedName, phoneNumber,
                certReqMsg, certReqMsgLang, certReqSerialNumber, null, requestId);

        // On-demand requests with step-up must be asynchronous
        signDocumentAsync(sigReqMsg, serverURI, pdfs, claimedIdentity, pollingInterval, pollRetries, estimatedSize, "Base64Signature");
    }
    
    /**
     * create SOAP request message and sign document with ondemand certificate but without mobile id
     *
     * @param pdfs               Pdf input files
     * @param hashAlgo           Hash algorithm to use for signature
     * @param signDate           Date when document(s) will be signed
     * @param serverURI          Server uri where to send the request
     * @param mobileIDStepUp     certificate request profile
     * @param distinguishedName  Information about signer e.g. name, country etc.
     * @param claimedIdentity    Signers identity
     * @param requestId          An id for the request
     * @throws Exception If hash or request can not be generated or document can not be signed.
     */
    private void signDocumentOnDemandCert(@Nonnull PDF[] pdfs, @Nonnull Include.HashAlgorithm hashAlgo, Calendar signDate, @Nonnull String serverURI,
                                          @Nonnull String distinguishedName, @Nonnull String claimedIdentity, String requestId)
            throws Exception {
    	
        String[] additionalProfiles;
        if (pdfs.length > 1) {
            additionalProfiles = new String[2];
            additionalProfiles[1] = Include.AdditionalProfiles.BATCH.getProfileName();
        } else {
            additionalProfiles = new String[1];
        }
        additionalProfiles[0] = Include.AdditionalProfiles.ON_DEMAND_CERTIFICATE.getProfileName();

        int estimatedSize = getEstimatedSize(false);

        byte[][] pdfHash = new byte[pdfs.length][];
        for (int i = 0; i < pdfs.length; i++) {
            pdfHash[i] = pdfs[i].getPdfHash(signDate, estimatedSize, hashAlgo.getHashAlgorythm(), false);
        }

        SOAPMessage sigReqMsg = createRequestMessage(Include.RequestType.SignRequest, hashAlgo.getHashUri(), true,
                pdfHash, additionalProfiles,
                claimedIdentity, Include.SignatureType.CMS.getSignatureType(), distinguishedName, null, null, null, null, null, requestId);

        signDocumentSync(sigReqMsg, serverURI, pdfs, estimatedSize, "Base64Signature");
    }

    /**
     * Create SOAP request message and sign document with static certificate
     *
     * @param pdfs              Pdf input files
     * @param hashAlgo          Hash algorithm to use for signature
     * @param signDate          Date when document(s) will be signed
     * @param serverURI         Server uri where to send the request
     * @param claimedIdentity   Signers identity
     * @param requestId         An id for the request
     * @throws Exception If hash or request can not be generated or document can not be signed.
     */
    private void signDocumentStaticCert(@Nonnull PDF[] pdfs, @Nonnull Include.HashAlgorithm hashAlgo, Calendar signDate, @Nonnull String serverURI,
                                        @Nonnull String claimedIdentity, String requestId)
            throws Exception {

        String[] additionalProfiles = null;
        if (pdfs.length > 1) {
            additionalProfiles = new String[1];
            additionalProfiles[0] = Include.AdditionalProfiles.BATCH.getProfileName();
        }

        int estimatedSize = getEstimatedSize(false);

        byte[][] pdfHash = new byte[pdfs.length][];
        for (int i = 0; i < pdfs.length; i++) {
            pdfHash[i] = pdfs[i].getPdfHash(signDate, estimatedSize, hashAlgo.getHashAlgorythm(), false);
        }

        SOAPMessage sigReqMsg = createRequestMessage(Include.RequestType.SignRequest, hashAlgo.getHashUri(), false,
                pdfHash, additionalProfiles,
                claimedIdentity, Include.SignatureType.CMS.getSignatureType(), null, null, null, null, null, null, requestId);

        signDocumentSync(sigReqMsg, serverURI, pdfs, estimatedSize, "Base64Signature");
    }

    /**
     * Create SOAP request message and add a timestamp to pdf
     *
     * @param pdfs              Pdf input files
     * @param hashAlgo          Hash algorithm to use for signature
     * @param signDate          Date when document(s) will be signed
     * @param serverURI         Server uri where to send the request
     * @param claimedIdentity   Signers identity
     * @param requestId         An id for the request
     * @throws Exception If hash or request can not be generated or document can not be signed.
     */
    private void signDocumentTimestampOnly(@Nonnull PDF[] pdfs, @Nonnull Include.HashAlgorithm hashAlgo, Calendar signDate,
                                           @Nonnull String serverURI, @Nonnull String claimedIdentity, String requestId)
            throws Exception {

        Include.SignatureType signatureType = Include.SignatureType.TIMESTAMP;

        String[] additionalProfiles;
        if (pdfs.length > 1) {
            additionalProfiles = new String[2];
            additionalProfiles[1] = Include.AdditionalProfiles.BATCH.getProfileName();
        } else {
            additionalProfiles = new String[1];
        }
        additionalProfiles[0] = Include.AdditionalProfiles.TIMESTAMP.getProfileName();

        int estimatedSize = getEstimatedSize(true);

        byte[][] pdfHash = new byte[pdfs.length][];
        for (int i = 0; i < pdfs.length; i++) {
            pdfHash[i] = pdfs[i].getPdfHash(signDate, estimatedSize, hashAlgo.getHashAlgorythm(), true);
        }

        SOAPMessage sigReqMsg = createRequestMessage(Include.RequestType.SignRequest, hashAlgo.getHashUri(), false,
                pdfHash, additionalProfiles, claimedIdentity, signatureType.getSignatureType(),
                null, null, null, null, null, null, requestId);

        signDocumentSync(sigReqMsg, serverURI, pdfs, estimatedSize, "RFC3161TimeStampToken");
    }

    /**
     * Send SOAP request to server and sign document if server send signature
     *
     * @param sigReqMsg     SOAP request message which will be send to the server
     * @param serverURI     Uri of server
     * @param pdfs          Pdf input file
     * @param estimatedSize Estimated size of external signature
     * @param signNodeName  Name of node where to find the signature
     * @throws Exception If hash can not be generated or document can not be signed.
     */
    private void signDocumentSync(@Nonnull SOAPMessage sigReqMsg, @Nonnull String serverURI, @Nonnull PDF[] pdfs,
                                  int estimatedSize, String signNodeName) throws Exception {

    	String sigResponse = sendRequest(sigReqMsg, serverURI);
        ArrayList<String> responseResult = getTextFromXmlText(sigResponse, "ResultMajor");
        boolean singingSuccess = sigResponse != null && responseResult != null && Include.RequestResult.Success.getResultUrn().equals(responseResult.get(0));

        if (!singingSuccess) {
            throw new Exception();
        }
        
        // Retrieve the Revocation Information (OCSP/CRL validation information)
        ArrayList<String> crl = getTextFromXmlText(sigResponse, "sc:CRL");
        ArrayList<String> ocsp = getTextFromXmlText(sigResponse, "sc:OCSP");

        ArrayList<String> signHashes = getTextFromXmlText(sigResponse, signNodeName);
        signDocuments(signHashes, ocsp, crl, pdfs, estimatedSize, signNodeName.equals("RFC3161TimeStampToken"));
    }
    
    /**
     * Send SOAP request for asynchronous signing to server
     *
     * @param sigReqMsg     SOAP request message which will be send to the server
     * @param serverURI     Uri of server
     * @param pdfs          Pdf input file
     * @param estimatedSize Estimated size of external signature
     * @param signNodeName  Name of node where to find the signature
     * @throws Exception If hash can not be generated or document can not be signed.
     */
    private void signDocumentAsync(
    		@Nonnull SOAPMessage sigReqMsg, 
    		@Nonnull String serverURI, 
    		@Nonnull PDF[] pdfs,
    		@Nonnull String claimedIdentity,
    		@Nonnull long interval,
    		@Nonnull int retries,
            int estimatedSize, 
            String signNodeName) throws Exception {

        String sigResponse = sendRequest(sigReqMsg, serverURI);
        ArrayList<String> responseResult = getTextFromXmlText(sigResponse, "ResultMajor");
        
        // The response to an asynchronous request is "pending"
        boolean pending = sigResponse != null && responseResult != null && Include.RequestResult.Pending.getResultUrn().equals(responseResult.get(0));

        // Parse ResponseID and ConsentURL from the response (if available)
        ArrayList<String> responseId_array = getTextFromXmlText(sigResponse, "async:ResponseID");
        ArrayList<String> consentUrl_array = getTextFromXmlText(sigResponse, "sc:ConsentURL");
        
        String responseId = responseId_array.get(0);
        
        String consentUrl = null;
        // Is there a consent URL available in the response? 
        if (consentUrl_array != null && !consentUrl_array.isEmpty()) {
        	consentUrl = consentUrl_array.get(0);
        }

        if (pending) {
            if (_debugMode || _verboseMode) {
                System.out.println("Request pending with responseID: " + responseId);
                System.out.println("Starting the polling with polling interval: " + interval + " milliseconds.");
            }

            // Create polling request message
            SOAPMessage pollReqMsg = createPendingMessage(RequestType.PendingRequest, claimedIdentity, responseId);

            // Start the polling
            poll(pollReqMsg, serverURI, responseId, interval, retries, pdfs, estimatedSize, signNodeName);
        } else {
            System.out.print("FAILED to get successful AIS SigResponse");
        }

        if (sigResponse != null && _verboseMode) {
            logSigningResponse(sigResponse, responseResult);
        }
    }

    /**
     * Poll the server until the asynchronous request does not return pending anymore.
     * @param pollReqMsg
     * @param serverURI
     * @param interval: time between polling requests, in milliseconds.
     * @throws Exception
     */
    private void poll(
    		@Nonnull SOAPMessage pollReqMsg,
    		@Nonnull String serverURI,
    		@Nonnull String responseId,
    		@Nonnull long interval,
    		@Nonnull int maxRetries,
    		@Nonnull PDF[] pdfs,
            int estimatedSize,
            String signNodeName) throws Exception {

    	// Send poll request
    	String sigResponse = sendRequest(pollReqMsg, serverURI);
        ArrayList<String> responseResult = getTextFromXmlText(sigResponse, "ResultMajor");

        boolean pending = (responseResult != null && Include.RequestResult.Pending.getResultUrn().equals(responseResult.get(0)));

    	// Loop while response is pending and max number of retries wasn't reached
    	int retries = 0;
        while (pending && retries < maxRetries) {
    		// Delay between polling requests: sleep during the given interval.
    		TimeUnit.MILLISECONDS.sleep(interval);
    		if (_debugMode) {
    			System.out.println("Retry " + retries + " - Polling with RequestID " + responseId + "...");
    		}
    		sigResponse = sendRequest(pollReqMsg, serverURI);
    		responseResult = getTextFromXmlText(sigResponse, "ResultMajor");
    		pending = (responseResult != null && Include.RequestResult.Pending.getResultUrn().equals(responseResult.get(0)));
    		retries++;
    	}

    	boolean signingSuccess = sigResponse != null && responseResult != null && Include.RequestResult.Success.getResultUrn().equals(responseResult.get(0));

    	if (_debugMode || _verboseMode) {

    		// Print a message if there was a timeout before completing the step-up
            if (pending && retries == maxRetries) {
            	System.out.println("Timeout - maximum number of retries (=" + maxRetries + ") was reached.");
            }

    		// Get PDF input file names for message output
            if (!signingSuccess) {
                System.out.print("FAILED to get successful AIS SigResponse");
            } else {
                System.out.print("SUCCEEDED to get AIS SigResponse");
            }

            if (sigResponse != null && _verboseMode) {
                logSigningResponse(sigResponse, responseResult);
            }
    	}

        if (!signingSuccess) {
            throw new Exception();
        }

        // Retrieve the Revocation Information (OCSP/CRL validation information)
        ArrayList<String> crl = getTextFromXmlText(sigResponse, "sc:CRL");
        ArrayList<String> ocsp = getTextFromXmlText(sigResponse, "sc:OCSP");

        ArrayList<String> signHashes = getTextFromXmlText(sigResponse, signNodeName);
        signDocuments(signHashes, ocsp, crl, pdfs, estimatedSize, signNodeName.equals("RFC3161TimeStampToken"));
    }

    /**
     * Add signature to pdf
     *
     * @param signHashes    Arraylist with Base64 encoded signatures
     * @param ocsp          Arraylist with Base64 encoded ocsp responses
     * @param crl           Arraylist with Base64 encoded crl responses
     * @param pdfs          Pdf which will be signed
     * @param estimatedSize Estimated size of external signature
     * @throws Exception If adding signature to pdf failed.
     */
    private void signDocuments(@Nonnull ArrayList<String> signHashes, ArrayList<String> ocsp, ArrayList<String> crl, @Nonnull PDF[] pdfs, int estimatedSize, boolean timestampOnly) throws Exception {
        int counter = 0;
        for (String signatureHash : signHashes) {
        	
          pdfs[counter].createSignedPdf(Base64.decode(signatureHash), estimatedSize);

          //if (timestampOnly) - Removed since we need to add the TS RI for CMS signatures as well
          //	pdfs[counter].addValidationInformation(ocsp, crl);;

          counter++;
        }
    }

    /**
     * Get text from a node from a xml text
     *
     * @param soapResponseText Text where to search
     * @param nodeName         Name of the node which text should be returned
     * @return If nodes with searched node names exist it will return an array list containing text from nodes
     * @throws IOException                  If any IO errors occur
     * @throws SAXException                 If any parse errors occur
     * @throws ParserConfigurationException If a DocumentBuilder cannot be created which satisfies the configuration requested
     */
    @Nullable
    private ArrayList<String> getTextFromXmlText(String soapResponseText, String nodeName) throws IOException, SAXException, ParserConfigurationException {
        Element element = getNodeList(soapResponseText);

        return getNodesFromNodeList(element, nodeName);
    }

    /**
     * Get nodes text content
     *
     * @param element
     * @param nodeName
     * @return if nodes with searched node names exist it will return an array list containing text from value from nodes
     */
    @Nullable
    private ArrayList<String> getNodesFromNodeList(@Nonnull Element element, @Nonnull String nodeName) {
        ArrayList<String> returnlist = null;
        NodeList nl = element.getElementsByTagName(nodeName);

        for (int i = 0; i < nl.getLength(); i++) {
            if (nodeName.equals(nl.item(i).getNodeName())) {
                if (returnlist == null) {
                    returnlist = new ArrayList<String>();
                }
                returnlist.add(nl.item(i).getTextContent());
            }

        }

        return returnlist;
    }

    /**
     * Get a xml string as an xml element object
     *
     * @param xmlString String to convert e.g. a server request or response
     * @return org.w3c.dom.Element from XML String
     * @throws ParserConfigurationException If a DocumentBuilder cannot be created which satisfies the configuration requested
     * @throws IOException                  If any IO errors occur
     * @throws SAXException                 If any parse errors occur
     */
    private Element getNodeList(@Nonnull String xmlString) throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        ByteArrayInputStream bis = new ByteArrayInputStream(xmlString.getBytes());
        Document doc = db.parse(bis);

        return doc.getDocumentElement();
    }

    /**
     * Create SOAP message object for server request. Will print debug information if debug is set to true
     *
     * @param reqType                  Type of request message e.g. singing or pending request
     * @param digestMethodAlgorithmURL Uri of hash algorithm
     * @param mobileIDStepUp           certificate request profile. Only necessary when on demand certificate is needed
     * @param hashList                 Hashes from documents which should be signed
     * @param additionalProfiles       Urn of additional profiles e.g. ondemand certificate, timestamp signature, batch process etc.
     * @param claimedIdentity          Signers identity / profile
     * @param signatureType            Urn of signature type e.g. signature type cms or timestamp
     * @param distinguishedName        Information about signer e.g. name, country etc.
     * @param phoneNumber              Mobile id for on demand certificates with mobile id request
     * @param certReqMsg               Message which will be send to phone number if set
     * @param certReqMsgLang           Language from message which will be send to mobile id
     * @param responseId               Only necessary when asking the signing status on server
     * @param requestId                Request id to identify signature in response
     * @return SOAP response from server. Depending on request profile it can be a signarure, signing status information or an error
     * @throws SOAPException If there is an error creating SOAP message
     * @throws IOException   If there is an error writing debug information
     */
    private SOAPMessage createRequestMessage(@Nonnull Include.RequestType reqType, @Nonnull String digestMethodAlgorithmURL,
                                             boolean mobileIDStepUp, @Nonnull byte[][] hashList,
                                             String[] additionalProfiles, String claimedIdentity,
                                             @Nonnull String signatureType, String distinguishedName,
                                             String phoneNumber, String certReqMsg, String certReqMsgLang,
                                             String certReqSerialNumber, String responseId, String requestId) throws SOAPException, IOException {

        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        // SOAP Envelope
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.removeNamespaceDeclaration("SOAP-ENV");
        envelope.setPrefix("soap");
        envelope.addAttribute(new QName("xmlns"), "urn:oasis:names:tc:dss:1.0:core:schema");
        envelope.addNamespaceDeclaration("dsig", "http://www.w3.org/2000/09/xmldsig#");
        envelope.addNamespaceDeclaration("sc", "http://ais.swisscom.ch/1.0/schema");
        envelope.addNamespaceDeclaration("ais", "http://service.ais.swisscom.com/");

        //SOAP Header
        SOAPHeader soapHeader = envelope.getHeader();
        soapHeader.removeNamespaceDeclaration("SOAP-ENV");
        soapHeader.setPrefix("soap");

        // SOAP Body
        SOAPBody soapBody = envelope.getBody();
        soapBody.removeNamespaceDeclaration("SOAP-ENV");
        soapBody.setPrefix("soap");

        SOAPElement signElement = soapBody.addChildElement("sign", "ais");

        SOAPElement requestElement = signElement.addChildElement(reqType.getRequestType());
        requestElement.addAttribute(new QName("Profile"), reqType.getUrn());
        requestElement.addAttribute(new QName("RequestID"), requestId);
        SOAPElement inputDocumentsElement = requestElement.addChildElement("InputDocuments");

        SOAPElement digestValueElement;
        SOAPElement documentHashElement;
        SOAPElement digestMethodElement;

        for (int i = 0; i < hashList.length; i++) {
            documentHashElement = inputDocumentsElement.addChildElement("DocumentHash");
            if (hashList.length > 1)
                documentHashElement.addAttribute(new QName("ID"), String.valueOf(i));
            digestMethodElement = documentHashElement.addChildElement("DigestMethod", "dsig");
            digestMethodElement.addAttribute(new QName("Algorithm"), digestMethodAlgorithmURL);
            digestValueElement = documentHashElement.addChildElement("DigestValue", "dsig");

            String s = com.itextpdf.text.pdf.codec.Base64.encodeBytes(hashList[i], Base64.DONT_BREAK_LINES);
            digestValueElement.addTextNode(s);
        }

        if (additionalProfiles != null || claimedIdentity != null || signatureType != null) {
            SOAPElement optionalInputsElement = requestElement.addChildElement("OptionalInputs");

            SOAPElement additionalProfileelement;
            if (additionalProfiles != null) {
                for (String additionalProfile : additionalProfiles) {
                    additionalProfileelement = optionalInputsElement.addChildElement("AdditionalProfile");
                    additionalProfileelement.addTextNode(additionalProfile);
                }
            }

            if (claimedIdentity != null) {
                SOAPElement claimedIdentityElement = optionalInputsElement.addChildElement("ClaimedIdentity", "");
                SOAPElement claimedIdNameElement = claimedIdentityElement.addChildElement("Name");
                claimedIdNameElement.addTextNode(claimedIdentity);
            }

            if (mobileIDStepUp) {
                SOAPElement certificateRequestElement = optionalInputsElement.addChildElement("CertificateRequest", "sc");
                if (distinguishedName != null) {
                    SOAPElement distinguishedNameElement = certificateRequestElement.addChildElement("DistinguishedName", "sc");
                    distinguishedNameElement.addTextNode(distinguishedName);
                    if (phoneNumber != null) {
                        SOAPElement stepUpAuthorisationElement = certificateRequestElement.addChildElement("StepUpAuthorisation", "sc");
                    	SOAPElement mobileIdElement = stepUpAuthorisationElement.addChildElement("Phone", "sc");
                        SOAPElement msisdnElement = mobileIdElement.addChildElement("MSISDN", "sc");
                        msisdnElement.addTextNode(phoneNumber);
                        SOAPElement certReqMsgElement = mobileIdElement.addChildElement("Message", "sc");
                        certReqMsgElement.addTextNode(certReqMsg);
                        SOAPElement certReqMsgLangElement = mobileIdElement.addChildElement("Language", "sc");
                        certReqMsgLangElement.addTextNode(certReqMsgLang);
                        if (certReqSerialNumber != null) {
                        	SOAPElement certReqMsgSerialNumberElement = mobileIdElement.addChildElement("SerialNumber", "sc");
                        	certReqMsgSerialNumberElement.addTextNode(certReqSerialNumber);
                        }
                    }
                }
            }

            if (signatureType != null) {
                SOAPElement signatureTypeElement = optionalInputsElement.addChildElement("SignatureType");
                signatureTypeElement.addTextNode(signatureType);
            }

            if (!signatureType.equals(_TIMESTAMP_URN)) {
                SOAPElement addTimeStampelement = optionalInputsElement.addChildElement("AddTimestamp");
                addTimeStampelement.addAttribute(new QName("Type"), _TIMESTAMP_URN);
            }

            // Set PADES as Signature Standard
            // Signature standard only applies to CMS signatures
            if (signatureType.equals(_CMS_URN)) {
            	SOAPElement addSignatureStandardElement = optionalInputsElement.addChildElement("SignatureStandard", "sc");
            	addSignatureStandardElement.setValue("PADES");
            } 
            
            // Always add revocation information
            SOAPElement addRevocationElement = optionalInputsElement.addChildElement("AddRevocationInformation", "sc");
			
            // Type="BOTH" means PADES+CADES
            // PADES = signed attribute according to PAdES
            // CADES = unsigned attribute according to CAdES
            // PADES-attributes are signed and cannot be post-added to an already signed RFC3161-TimeStampToken
            // So the RevocationInformation (RI) of a trusted timestamp will be delivered via OptionalOutputs
         	// and they shall be added to the Adobe DSS in order to enable LTV for a Timestamp
            
            // For CMS signatures the revocation information type will match the signature standard (PADES)
             
            // Since for timestamping-only there is no signature standard, 
            // the revocation type must be explicitely set
            if (signatureType.equals(_TIMESTAMP_URN)) {
            	addRevocationElement.addAttribute(new QName("Type"), "BOTH");
            }

            if (responseId != null) {
                SOAPElement responseIdElement = optionalInputsElement.addChildElement("ResponseID");
                responseIdElement.addTextNode(responseId);
            }
        }

        soapMessage.saveChanges();

        if (_debugMode) {
            System.out.print("\nRequest SOAP Message:\n");
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            soapMessage.writeTo(ba);
            String msg = new String(ba.toByteArray());
            System.out.println(getPrettyFormatedXml(msg, 2));
        }

        return soapMessage;
    }
    
    /**
     * Analog to the create request message method, this one returns a polling request for asynchronous methods.
     * 
     * @param reqType
     * @param responseId
     * @return
     * @throws SOAPException
     * @throws IOException
     */
    private SOAPMessage createPendingMessage(
    		@Nonnull Include.RequestType reqType, 
    		String claimedIdentity,
    		String responseId) throws SOAPException, IOException {
    	
    	MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        // SOAP Envelope
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.removeNamespaceDeclaration("SOAP-ENV");
        envelope.setPrefix("soap");
        envelope.addAttribute(new QName("xmlns"), "urn:oasis:names:tc:dss:1.0:core:schema");
        envelope.addNamespaceDeclaration("dsig", "http://www.w3.org/2000/09/xmldsig#");
        envelope.addNamespaceDeclaration("sc", "http://ais.swisscom.ch/1.0/schema");
        envelope.addNamespaceDeclaration("ais", "http://service.ais.swisscom.com/");
        
        // Add async namespace
        envelope.addNamespaceDeclaration("async", "urn:oasis:names:tc:dss:1.0:profiles:asynchronousprocessing:1.0");
        
        //SOAP Header
        SOAPHeader soapHeader = envelope.getHeader();
        soapHeader.removeNamespaceDeclaration("SOAP-ENV");
        soapHeader.setPrefix("soap");

        // SOAP Body
        SOAPBody soapBody = envelope.getBody();
        soapBody.removeNamespaceDeclaration("SOAP-ENV");
        soapBody.setPrefix("soap");

        SOAPElement pendingElement = soapBody.addChildElement("pending", "ais");
        
        SOAPElement requestElement = pendingElement.addChildElement(reqType.getRequestType(), "async");
        requestElement.addAttribute(new QName("Profile"), reqType.getUrn());
        
        // Optional Inputs
        SOAPElement optionalInputsElement = requestElement.addChildElement("OptionalInputs");

        // Claimed Identity
        if (claimedIdentity != null) {
        	SOAPElement claimedIdentityElement = optionalInputsElement.addChildElement("ClaimedIdentity", "");
        	SOAPElement claimedIdNameElement = claimedIdentityElement.addChildElement("Name");
        	claimedIdNameElement.addTextNode(claimedIdentity);
        }
        
        // async:ResponseID
        SOAPElement responseIdElement = optionalInputsElement.addChildElement("ResponseID", "async");
        responseIdElement.addTextNode(responseId);

        soapMessage.saveChanges();

        if (_debugMode) {
            System.out.print("\nRequest SOAP Message:\n");
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            soapMessage.writeTo(ba);
            String msg = new String(ba.toByteArray());
            System.out.println(getPrettyFormatedXml(msg, 2));
        }

        return soapMessage;
    }

    /**
     * Creating connection object and send request to server. If debug is set to true it will print response message.
     *
     * @param soapMsg Message which will be send to server
     * @param urlPath Url of server where to send the request
     * @return Server response
     * @throws Exception If creating connection ,sending request or reading response failed
     */
    @Nullable
    private String sendRequest(@Nonnull SOAPMessage soapMsg, @Nonnull String urlPath) throws Exception {

        URLConnection conn = new Connect(urlPath, _privateKeyName, _serverCertPath, _clientCertPath, _timeout, _debugMode).getConnection();
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setRequestMethod("POST");
        }
        
        conn.setAllowUserInteraction(true);
        conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        conn.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        soapMsg.writeTo(baos);
        String msg = baos.toString();

        out.write(msg);
        out.flush();
        if (out != null) {
            out.close();
        }

        String line = "";
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        String response = "";
        while ((line = in.readLine()) != null) {
            response = response.length() > 0 ? response + " " + line : response + line;
        }

        if (in != null) {
            in.close();
        }

        if (_debugMode) {
            System.out.println("\nSOAP response message:\n" + getPrettyFormatedXml(response, 2));
        }

        return response;
    }

    /**
     * Calculate size of signature
     *
     * @param isTimestampOnly    
     * @return Calculated size of external signature as int
     */
    private int getEstimatedSize(boolean isTimestampOnly) {
    	if (isTimestampOnly)
    		return 15000;
    	else 
    		return 30000;
    }

    /**
     * Check if given files exist and are files
     *
     * @param filePaths Files to check
     * @throws FileNotFoundException If file will not be found or is not readable
     */
    private void checkFilesExistsAndIsFile(@Nonnull String[] filePaths) throws FileNotFoundException {

        File file;
        for (String filePath : filePaths) {
            file = new File(filePath);
            if (!file.isFile() || !file.canRead()) {
                throw new FileNotFoundException("File not found or is not a file or not readable: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Convert a xml text which is not formated to a pretty format
     *
     * @param input  Input text
     * @param indent Set indent from left
     * @return Pretty formated xml
     */
    public String getPrettyFormatedXml(@Nonnull String input, int indent) {

        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);

            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Generate a new request id with actually time and a 3 digit random number. Output looks like 22.01.2014 17:10:26:0073122
     *
     * @return Request id as String
     */
    public String getRequestId() {
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss:SSSS");
        int randomNumber = (int) (Math.random() * 1000);
        return (df.format(new Date()).concat(String.valueOf(randomNumber)));
    }

    /**
     * Dumps the signing response details to console
     * @param sigResponse
     * @param responseResult
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    private void logSigningResponse(String sigResponse, ArrayList<String> responseResult) throws IOException, SAXException, ParserConfigurationException {

        ArrayList<String> resultMinor = getTextFromXmlText(sigResponse, "ResultMinor");
        ArrayList<String> errorMsg = getTextFromXmlText(sigResponse, "ResultMessage");

        if (responseResult != null || resultMinor != null || errorMsg != null) {
            System.out.println(" with following details:");
        }

        if (responseResult != null) {
            for (String s : responseResult) {
                if (s.length() > 0) {
                    System.out.println(" Result major: " + s);
                }
            }
        }

        if (resultMinor != null) {
            for (String s : resultMinor) {
                if (s.length() > 0) {
                    System.out.println(" Result minor: " + s);
                }
            }
        }

        if (errorMsg != null) {
            for (String s : errorMsg) {
                if (s.length() > 0) {
                    System.out.println(" Result message: " + s);
                }
            }
        }

        // Newline
        System.out.println("");
    }

}

