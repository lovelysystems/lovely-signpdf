/**
 * Created:
 * 18.12.13 KW 51 10:42
 * </p>
 * Last Modification:
 * 18.02.2014 13:47
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
 * </p>
 * **********************************************************************************************************
 * This is a wrapper class for the 'Soap' class                                                             *
 * Only program arguments will be handled                                                                   *
 * At least 'Soap' will be called with arguments                                                        *
 * **********************************************************************************************************
 */

package com.swisscom.ais.itext;

import javax.annotation.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;

public class SignPDF {
    /**
     * The value is used to decide if verbose information should be print
     */
    static boolean verboseMode = false;

    /**
     * The value is used to decide if debug information should be print
     */
    static boolean debugMode = false;

    /**
     * The signature type. E.g. timestamp, sign, ...
     */
    Include.Signature signature = null;

    /**
     * Path to pdf which get a signature
     */
    InputStream pdfToSign = null;

    /**
     * Path to output document with generated signature
     */
    OutputStream signedPDF = null;

    /**
     * Reason for signing a document.
     */
    String signingReason = null;

    /**
     * Location where a document was signed
     */
    String signingLocation = null;

    /**
     * Person who signed the document
     */
    String signingContact = null;
    
    /**
     * Certification Level
     */
    int certificationLevel = 0;

    /**
     * Distinguished name contains information about signer. Needed for ondemand signature
     */
    String distinguishedName = null;

    /**
     * Mobile phone number to send a message when signing a document. Needed for signing with MobileID/PwdOTP.
     */
    String msisdn = null;

    /**
     * Message which will be send to mobile phone with mobile id. Needed for signing with MobileID/PwdOTP.
     */
    String msg = null;

    /**
     * Language of the message which will be send to the mobile phone (or shown under the consent URL). Needed for signing with MobileID/PwdOTP.
     */
    String language = null;
    
    /**
     * MobileID/PwdOTP Serial Number
     */
    String serialnumber = null;

    /**
     * Path for properties file. Needed if standard path will not be used.
     */
    String propertyFilePath = null;
    
    /**
     * Main method to start AIS. This will parse given parameters e.g. input file, output file etc. and start signature
     * process. Furthermore this method prints error message if signing failed. See usage part in README to know how to
     * use it.
     *
     * @param args Arguments that will be parsed. See useage part in README for more details.
     */
    public static void sign(String[] args) {

        SignPDF ais = new SignPDF();
        try {
            ais.runSigning(args);
        } catch (Exception e) {
            if (debugMode || verboseMode) {
                e.printStackTrace();
            }
            System.exit(1);
        }

    }
    
    /**
     * Parse given parameters, check if all necessary parameters exist and if there are not unnecessary parameters.
     * If there are problems with parameters application will abort with exit code 1.
     * After all checks are done signing process will start.
     *
     * @param params argument list as described for sign metho
     */
    public void runSigning(String[] params) throws Exception {
        /*
        parseParameters(params);
        checkNecessaryParams();
        checkUnnecessaryParams();
        */

        //parse signature
        if (signature.equals(Include.Signature.SIGN) && distinguishedName != null) {
            signature = Include.Signature.ONDEMAND;
        } else if (signature.equals(Include.Signature.SIGN) && distinguishedName == null) {
            signature = Include.Signature.STATIC;
        }

        //start signing
        if (propertyFilePath == null) 
        	System.err.println("Property File not found. Add '-config=VALUE'-parameter with correct path");
        
        Soap dss_soap = new Soap(verboseMode, debugMode, propertyFilePath);
        dss_soap.sign(signature, pdfToSign, signedPDF, null, signingReason, signingLocation, signingContact, certificationLevel, distinguishedName, msisdn, msg, language, serialnumber);
    }
    
    private void printUsage() {
    	printUsage(null);
    }

    /**
     * Prints usage and exits
     */
    private void printUsage(String error) {
    	if (error != null && (debugMode || verboseMode)) {
    		printError(error);
    	}
    	System.out.println("\nUsage: SignPDF [OPTIONS]");
    	System.out.println();
    	System.out.println("OPTIONS");
    	System.out.println();
    	System.out.println("  -infile=VALUE           - Source Filename, PDF to be signed");
    	System.out.println("  -outfile=VALUE          - Target Filename, signed PDF");
    	System.out.println();
    	System.out.println("  ### TIMESTAMP SIGNATURES ###");
    	System.out.println("  -type=timestamp         - Signature Type RFC 3161");
    	System.out.println();  
    	System.out.println("  ### SIGNATURES WITH STATIC CERTIFICATES ###");
    	System.out.println("  -type=sign              - Signature Type RFC 3369");
    	System.out.println();
    	System.out.println("  ### SIGNATURES WITH ON DEMAND CERTIFICATES ###");
    	System.out.println("  -type=sign              - Signature Type RFC 3369");
    	System.out.println("  -dn=VALUE               - Subject Distinguished Name for the On Demand Certificate");
    	System.out.println("                            Supported attributes, separated by a comma:");
    	System.out.println("                            [mandatory]");
    	System.out.println("                             - cn or CommonName");
    	System.out.println("                             - c or CountryName");
    	System.out.println("                            [optional]");
    	System.out.println("                             - EmailAddress");
    	System.out.println("                             - FivenName");
    	System.out.println("                             - l or LocalityName");
    	System.out.println("                             - ou or OrganizationalUnitName");
    	System.out.println("                             - o or OrganizationName");
    	System.out.println("                             - SerialNumber");
    	System.out.println("                             - st or StateOrProvinceName");
    	System.out.println("                             - sn or Surname");
    	System.out.println("  Optional Mobile ID Authorization:");
    	System.out.println("  -stepUpMsisdn=VALUE        - Phone number (requires -dn -stepUpMsg -stepUpLang)");
    	System.out.println("  -stepUpMsg=VALUE           - Message to be displayed (requires -dn -stepUpMsisdn -stepUpLang)");
    	System.out.println("                            A placeholder #TRANSID# may be used anywhere in the message to include a unique transaction id");
    	System.out.println("  -stepUpLang=VALUE          - Language of the message to be displayed (requires -dn -stepUpMsisdn -stepUpMsg)");
    	System.out.println("                            supported values:");
    	System.out.println("                             - en (english)");
    	System.out.println("                             - de (deutsch)");
    	System.out.println("                             - fr (français)");
    	System.out.println("                             - it (italiano)");
    	System.out.println("  -stepUpSerialNumber=VALUE  - Optional: Verify the step-up SerialNumber (16 chars; starting with 'MIDCHE' or 'SAS01')");
    	System.out.println("                            Document will only be signed if it matched the actual SerialNumber");
    	System.out.println();
    	System.out.println("  ### ADOBE PDF SETTINGS ###");
    	System.out.println("  -reason=VALUE           - Signing Reason");
    	System.out.println("  -location=VALUE         - Signing Location");
    	System.out.println("  -contact=VALUE          - Signing Contact");
    	System.out.println("  -certlevel=VALUE        - Certify the PDF, at most one certification per PDF is allowed");
    	System.out.println("                             Supported values:");
    	System.out.println("                             - 1 (no further changes allowed)");
    	System.out.println("                             - 2 (form filling and further signing allowed)");
    	System.out.println("                             - 3 (form filling, annotations and further signing allowed)");
    	System.out.println();
    	System.out.println("  ### DEBUG OPTIONS ###");
    	System.out.println("  -v                      - Verbose output");
    	System.out.println("  -vv                     - More Verbose output");
    	System.out.println("  -config=VALUE           - Custom path to the properties file (signpdf.properties)");
    	System.out.println();                           
    	System.out.println("EXAMPLES");
    	System.out.println();
    	System.out.println("  [timestamp]");
    	System.out.println("    java SignPDF -type=timestamp -infile=sample.pdf -outfile=signed.pdf");
    	System.out.println("    java SignPDF -v -type=timestamp -infile=sample.pdf -outfile=signed.pdf");
    	System.out.println();
    	System.out.println("  [sign with static certificate]");
    	System.out.println("    java SignPDF -type=sign -infile=sample.pdf -outfile=signed.pdf");
    	System.out.println("    java SignPDF -v -config=/tmp/signpdf.properties -type=sign -infile=sample.pdf -outfile=signed.pdf -reason=Approved -location=Berne -contact=alice@acme.com");
    	System.out.println();
    	System.out.println("  [sign with on demand certificate]");
    	System.out.println("    java SignPDF -type=sign -infile=sample.pdf -outfile=signed.pdf -dn='cn=Alice Smith,c=CH'");
    	System.out.println();
    	System.out.println("  [sign with on demand certificate and mobile id authorization]");
    	System.out.println("    java SignPDF -v -type=sign -infile=sample.pdf -outfile=signed.pdf -dn='cn=Alice Smith,c=CH' -stepUpMsisdn=41792080350 -stepUpMsg='acme.com: Sign the PDF? (#TRANSID#)' -stepUpLang=en");
    	System.out.println("    java SignPDF -v -type=sign -infile=sample.pdf -outfile=signed.pdf -dn='cn=Alice Smith,c=CH' -stepUpMsisdn=41792080350 -stepUpMsg='acme.com: Sign the PDF? (#TRANSID#)' -stepUpLang=en -stepUpSerialNumber=MIDCHE2EG8NAWUB3");
    }

    /**
     * Prints error message
     *
     * @param error Message that should print
     */
    private void printError(@Nonnull String error) {
    	// do not use error output stream to ensure proper order of all println's
    	if (error != null && error != "")
    		System.out.println("Error: " + error);
    }

    /**
     * Parse given parameters. If an error occurs application with exit with code 1. If debug and/or verbose mode is set
     * an error message will be shown
     * @param args
    private void parseParameters(String[] args) throws Exception {
    	
    	// args can never be null. It would just be of size zero.

        String param;
        boolean type = false, infile = false, outfile = false; 
        for (int i = 0; i < args.length; i++) {

            param = args[i].toLowerCase();

            if (param.contains("-type=")) {
                String signatureString = null;
                try {
                    signatureString = args[i].substring(args[i].indexOf("=") + 1).trim().toUpperCase();
                    signature = Include.Signature.valueOf(signatureString);
                    type = true;
                } catch (IllegalArgumentException e) {
                    if (debugMode || verboseMode) {
                        printError(signatureString + " is not a valid signature.");
                    }
                    printUsage();
                }
            } else if (param.contains("-infile=")) {
                pdfToSign = args[i].substring(args[i].indexOf("=") + 1).trim();
                File pdfToSignFile = new File(pdfToSign);
                if (!pdfToSignFile.isFile() || !pdfToSignFile.canRead()) {
                    if (debugMode || verboseMode) {
                        printError("File " + pdfToSign + " is not a file or can not be read.");
                    }
                    throw new Exception("File " + pdfToSign + " is not a file or can not be read.");
                }
                infile = true;
			} else if (param.contains("-outfile=")) {
				signedPDF = args[i].substring(args[i].indexOf("=") + 1).trim();
				String errorMsg = null;
				if (signedPDF.equals(pdfToSign)) {
					errorMsg = "Source file equals target file.";
				} else if (new File(signedPDF).isFile()) {
					errorMsg = "Target file exists.";
				} else {
					try {
						new File(signedPDF);
					} catch (Exception e) {
						errorMsg = "Can not create target file in given path.";
					}
				}
				if (errorMsg != null) {
					if (debugMode || verboseMode) {
						printError(errorMsg);
					}
					throw new Exception(errorMsg);
                }
				outfile = true;
            } else if (param.contains("-reason")) {
                signingReason = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-location")) {
                signingLocation = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-contact")) {
                signingContact = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-certlevel")) {
                try {
                	certificationLevel = Integer.parseInt(args[i].substring(args[i].indexOf("=") + 1).trim());
    				if (certificationLevel < 1 || certificationLevel > 3)
    					throw new Exception();
    			} catch (Exception e) {
    				if (debugMode || verboseMode) {
                        printUsage("-certlevel value not between 1..3");
                    }
    			}
            } else if (param.contains("-dn=")) {
                distinguishedName = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-stepupmsisdn=")) {
                msisdn = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-stepupmsg=")) {
                msg = args[i].substring(args[i].indexOf("=") + 1).trim();
                String transId = getNewTransactionId();
                msg = msg.replaceAll("#TRANSID#", transId);
            } else if (param.contains("-stepuplang=")) {
                language = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-stepupserialnumber=")) {
            	serialnumber = args[i].substring(args[i].indexOf("=") + 1).trim();
            } else if (param.contains("-config=")) {
                propertyFilePath = args[i].substring(args[i].indexOf("=") + 1).trim();
                File propertyFile = new File(propertyFilePath);
                if (!propertyFile.isFile() || !propertyFile.canRead()) {
                    if (debugMode || verboseMode) {
                        printError("Property file path is set but file does not exist or can not read it: "+propertyFilePath);
                    }
                    throw new Exception("Property file path is set but file does not exist or can not read it: "+propertyFilePath);
                }
            } else if (args[i].toLowerCase().contains("-vv")) {
            	debugMode = true;
            } else if (param.contains("-v")) {
            	verboseMode = true;
            }
        }
        
        // Check existence of mandatory arguments
        if (!type) {
        	printUsage("Mandatory option -type is missing");
        } else if (!infile) {
        	printUsage("Mandatory option -infile is missing");
        } else if (!outfile) {
        	printUsage("Mandatory option -outfile is missing");
        } 
        
    }

    /**
     * Check if needed parameters are given. If not method will print an error and exit with code 1
    private void checkNecessaryParams() throws Exception {

        if (pdfToSign == null) {
            if (debugMode || verboseMode) {
                printError("Input file does not exist.");
            }
            throw new Exception("Input file does not exist.");
        }

        if (signedPDF == null) {
            if (debugMode || verboseMode) {
                printError("Output file does not exist.");
            }
            throw new Exception("Output file does not exist.");
        }
    }

    /**
     * This method checks if there are unnecessary parameters. If there are some it will print the usage of parameters
     * and exit with code 1 (e.g. DN is given for signing with timestamp)
    private void checkUnnecessaryParams() {

        if (signature.equals(Include.Signature.TIMESTAMP)) {
            if (distinguishedName != null || msisdn != null || msg != null || language != null) {
                if (debugMode || verboseMode) {
                    printUsage();
                }
            }
        } else {
            if (!(distinguishedName == null && msisdn == null && msg == null && language == null ||
                    distinguishedName != null && msisdn == null && msg == null && language == null ||
                    distinguishedName != null && msisdn != null && msg != null && language != null)) {
                if (debugMode || verboseMode) {
                    printUsage();
                }
            }
        }
    }
    
    /**
     * Return a unique transaction id
     * @return transaction id
     */
    private String getNewTransactionId() {
    	// secure, easy but a little bit more expensive way to get a random alphanumeric string
        return new BigInteger(30, new SecureRandom()).toString(32);
    }   

}
