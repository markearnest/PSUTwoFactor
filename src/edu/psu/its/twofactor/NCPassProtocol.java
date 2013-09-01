package edu.psu.its.twofactor;

/*
 * ncPassProtocol.java
 *
 * Class to build byte array streams for communication with
 * PassGo's NCPASS to validate SecurID tokens.
 * 
 * Author: Mark Allen Earnest (mxe20@psu.edu)
 *
 * Copyright 2006 The Pennsylvania State University 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific 
 * language governing permissions and limitations under the License.
 */
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Hashtable;

/**
 * Methods for building and parsing messages sent to and received from the NCPASS TLI interface for the purpose of validating RSA SecureID tokens.
 * 
 * @author Mark Earnest mark@mystikos.org
 */
public class NCPassProtocol {

	/**
	 * 
	 */
	private final String transID;

	/**
	 * Sole Constructor, generates a six digit random transaction ID and stores it in a String
	 */
	public NCPassProtocol() {
		SecureRandom generator = new SecureRandom();
		transID = "" + generator.nextInt(999999);
	}

	/**
	 * Return string label of NCPASS authentication code
	 * 
	 * @param code contains NCPASS AuthenticationCode
	 * 
	 * @return authentication code label
	 */
	private String authenticationCode(int code) {
		switch (code) {
			case 0:
				return "Authentication Successful";
			case 10:
				return "Authentication Failed";
			case 20:
				return "Registration Failed";
			case 30:
				return "Reregistration Failed";
			case 40:
				return "PIN Change Failed (unassigned token)";
			case 41:
				return "Incorrect Token Type";
			case 42:
				return "PIN Change Failed";
			case 50:
				return "Authentication Not Checked";
			default:
				return "Unknown Authentication Code";
		}
	}

	/**
	 * Builds the NCPASS TLI handshake. This handshake is sent to the NCPASS server to initiate the SecureID token check
	 * 
	 * @param appID application id
	 * 
	 * @return byte array containing the handshake initiator to be sent to NCPASS
	 * @throws UnsupportedEncodingException EBCDIC not supported
	 * @throws IOException unable to write to output stream
	 */
	public byte[] buildHandShake(String appID) throws UnsupportedEncodingException, IOException {
		ByteArrayOutputStream handShakeStream = new ByteArrayOutputStream();
		short length;
		byte[] handShake;
		byte[] lengthBits = new byte[2];
		// build header, process code 0
		handShakeStream.write(buildHeader(transID, 0));
		// appID and length
		handShakeStream.write(convertParameter(appID));
		// sysID and length
		handShakeStream.write(convertParameter("NCTLI"));
		// password for EXIT45 *not used*
		handShakeStream.write((byte) 0);
		handShakeStream.write((byte) 0);
		// direction ID
		handShakeStream.write(convertParameter("1"));
		// put length of handshake at the beginning
		handShake = handShakeStream.toByteArray();
		length = (short) (handShake.length + 2);
		handShakeStream.reset();
		lengthBits[0] = (byte) (length >>> 8);
		lengthBits[1] = (byte) length;
		handShakeStream.write(lengthBits);
		handShakeStream.write(handShake);
		return handShakeStream.toByteArray();
	}

	/**
	 * Builds the header common to all NCPASS TLI communications
	 * 
	 * @param transIdStr contains the random transaction ID generated by the constructor
	 * @param processCode contains the processcode telling the server which type of query is to follow
	 * 
	 * @return byte array containing the NCPASS TLI header
	 * @throws IOException unable to write to output stream
	 */
	private byte[] buildHeader(String transIdStr, int processCode) throws IOException {
		ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
		String sProcessCode = "SE0" + processCode;
		headerStream.write("OS".getBytes("Cp1047"));
		headerStream.write(transIdStr.getBytes("Cp1047"));
		headerStream.write(sProcessCode.getBytes("Cp1047"));
		return headerStream.toByteArray();
	}

	/**
	 * Builds the NCPASS TLI query for authenticating an RSA SecureID token / UserID pair. While this data is sent in the clear (NCPASS does not support SSL) the userid and SecureID number are not subject to a replay attack as once a number is validated it cannot be used again until the token cycles
	 * the number.
	 * 
	 * @param userID userID containing the user's principal name
	 * @param secureID secureID containing the SecureID number
	 * 
	 * @return byte array containing the query to be sent to NCPASS
	 * @throws UnsupportedEncodingException EBCDIC not supported
	 * @throws IOException unable to write to output stream
	 */
	public byte[] buildRequest(String userID, String secureID) throws UnsupportedEncodingException, IOException {
		ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
		short length;
		byte[] request;
		byte[] lengthBits = new byte[2];
		// build header, process code 3
		requestStream.write(buildHeader(transID, 3));
		// userID and length
		requestStream.write(convertParameter(userID));
		// remote user *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// current password *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// token challenge *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// token response (SecurID number)
		requestStream.write(convertParameter(secureID));
		// token serial number *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// token type (11 = SDA SecurID standard)
		requestStream.write((byte) 0);
		requestStream.write((byte) 2);
		requestStream.write((byte) 0);
		requestStream.write((byte) 11);
		// new token challenge *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// new token response *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// P card PIN *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// requestor ID
		requestStream.write(convertParameter("TCP"));
		// terminal/node
		requestStream.write(convertParameter("WEBTERM"));
		// target *not used*
		requestStream.write((byte) 0);
		requestStream.write((byte) 0);
		// target Supplementary
		requestStream.write(convertParameter("TLI"));
		requestStream.write((byte) 0);
		// put length of request at the beginning of stream
		request = requestStream.toByteArray();
		length = (short) (request.length + 2); // + 2 includes length field
		requestStream.reset();
		lengthBits[0] = (byte) (length >>> 8);
		lengthBits[1] = (byte) length;
		requestStream.write(lengthBits);
		requestStream.write(request);
		return requestStream.toByteArray();
	}

	/**
	 * Creates a NCPASS TLI formatted parameter. The parameter is converted to EBCDIC (code page 1047) The length of the parameter is placed before the parameter in the stream
	 * <p>
	 * Example: {0x00, 0x05, 'H', 'E', 'L', 'L', 'O'}
	 * <p>
	 * Note: The length needs to be in big endian, which is fortunately what the java virtual machine uses, so no byte order conversion is necessary
	 * 
	 * @param parameter contains a string to be converted to a NCPASS TLI parameter
	 * 
	 * @return byte array containing the converted parameter
	 * @throws IOException unable to write to output stream
	 */
	private byte[] convertParameter(String parameter) throws IOException {
		ByteArrayOutputStream parameterStream = new ByteArrayOutputStream();
		short length;
		byte[] lengthBits = new byte[2];
		byte[] parameterArray = parameter.getBytes("Cp1047");
		length = (short) parameterArray.length;
		lengthBits[0] = (byte) (length >>> 8);
		lengthBits[1] = (byte) length;
		parameterStream.write(lengthBits);
		parameterStream.write(parameterArray);
		return parameterStream.toByteArray();
	}

	/**
	 * Decodes the NCPASS TLI handshake response and returns a hashtable containing all of the returned data fields.
	 * 
	 * @param handshake byte array containing handshake response from NCPASS
	 * 
	 * @return Hashtable containing decoded parameters from NCPASS response
	 * @throws UnsupportedEncodingException EBCDIC not supported
	 */
	public Hashtable decodeHandshake(byte[] handshake) throws UnsupportedEncodingException {
		Hashtable handshakeResult = new Hashtable();
		handshakeResult = decodeHeader(handshake);
		int length = 0;
		int c = 14;
		length = getParameterHex(handshake[c++], handshake[c++]);
		if (length != 0) {
			handshakeResult.put("SystemID", getParameterEBCDIC(handshake, c, length));
		}
		c = c + length;
		length = getParameterHex(handshake[c++], handshake[c++]);
		if (length != 0) {
			handshakeResult.put("CPUID", getParameterEBCDIC(handshake, c, length));
		}
		c = c + length;
		length = getParameterHex(handshake[c++], handshake[c++]);
		if (length != 0) {
			handshakeResult.put("Password", getParameterEBCDIC(handshake, c, length));
		}
		c = c + length;
		length = getParameterHex(handshake[c++], handshake[c++]);
		if (length != 0) {
			handshakeResult.put("DirectionID", getParameterEBCDIC(handshake, c, length));
		}
		c = c + length;
		handshakeResult.put("bytesprocessed", Integer.toString(c));
		return handshakeResult;
	}

	/**
	 * Decodes the header of any message returned by the NCPASS TLI interface.
	 * 
	 * @param handshake byte array containing handshake
	 * 
	 * @return the decoded data fields
	 * @throws UnsupportedEncodingException EBCDIC not supported
	 */
	private Hashtable decodeHeader(byte[] handshake) throws UnsupportedEncodingException {
		Hashtable headerResult = new Hashtable();
		int c = 0; // byte array index
		headerResult.put("length", Integer.toString(getParameterHex(handshake[c++], handshake[c++])));
		headerResult.put("OS", getParameterEBCDIC(handshake, c, 2));
		c = c + 2;
		headerResult.put("transactionid", getParameterEBCDIC(handshake, c, 6));
		c = c + 6;
		headerResult.put("processcode", getParameterEBCDIC(handshake, c, 4));
		c = c + 4; // processcode takes up two bytes, followed by two nulls
		return headerResult;
	}

	/**
	 * Decodes the NCPASS query response and returns a hashtable containing all of the returned data fields. Also contains text explinations of the Validation and AuthenticationResult codes
	 * 
	 * @param response byte array containing NCPASS query response
	 * 
	 * @return Hashtable containing decoded parameters from NCPASS response
	 * @throws UnsupportedEncodingException EBCDIC not supported
	 */
	public Hashtable decodeResponse(byte[] response) throws UnsupportedEncodingException {
		Hashtable requestResult = new Hashtable();
		requestResult = decodeHeader(response);
		int length = 0;
		int c = 14;
		length = getParameterHex(response[c++], response[c++]);
		if (length != 0) {
			requestResult.put("ValidationResultCode", Integer.toString(getParameterHex(response[c++], response[c++])));
			requestResult.put("ValidationResult", validationCode(Integer.parseInt((String) requestResult.get("ValidationResultCode"))));
		}
		length = getParameterHex(response[c++], response[c++]);
		if (length != 0) {
			requestResult.put("AuthenticationResultCode", Integer.toString(getParameterHex(response[c++], response[c++])));
			requestResult.put("AuthenticationResult", authenticationCode(Integer.parseInt((String) requestResult.get("AuthenticationResultCode"))));
		}
		length = getParameterHex(response[c++], response[c++]);
		if (length != 0) {
			requestResult.put("Message", getParameterEBCDIC(response, c, length));
		} else {
			c++; // if no message field, there will be an extra character to
		}
		// bypass
		c = c + length;
		length = getParameterHex(response[c++], response[c++]);
		if (length != 0) {
			requestResult.put("HostUserID", getParameterEBCDIC(response, c, length));
		}
		c = c + length;
		length = getParameterHex(response[c++], response[c++]);
		if (length != 0) {
			requestResult.put("RemoteUserID", getParameterEBCDIC(response, c, length));
		}
		c = c + length;
		requestResult.put("bytesprocessed", Integer.toString(c));
		return requestResult;
	}

	/**
	 * Takes a section of the NCPASS generated byte array and converts it from EBDCIC to an ASCII String
	 * 
	 * @param resultArray the byte array containing the string
	 * @param start the location of the beginning of the string in the byte array
	 * @param length length the length of the string
	 * 
	 * @return String containing the converted string
	 * @throws UnsupportedEncodingException no EBCDIC support
	 */
	private String getParameterEBCDIC(byte[] resultArray, int start, int length) throws UnsupportedEncodingException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		for (int x = 0; x < length; x++) {
			stream.write(resultArray[start + x]);
		}
		return stream.toString("Cp1047");
	}

	/**
	 * Returns an integer build from two bytes
	 * 
	 * @param a contains low order byte
	 * @param b contains high order byte
	 * 
	 * @return the result
	 */
	private int getParameterHex(byte a, byte b) {
		return 0xFFFF & (a << 8 | b);
	}

	/**
	 * Return string label of NCPASS validation code
	 * 
	 * @param code contains NCPASS ValidationCode
	 * 
	 * @return validation code label
	 */
	private String validationCode(int code) {
		switch (code) {
			case 0:
				return "Validation Successful";
			case 2:
				return "Invalid Terminal ID";
			case 3:
				return "Invalid Login/Logon Time";
			case 4:
				return "Unknown Userid";
			case 5:
				return "Validation Successful (with RACF) PassTicket)";
			case 6:
				return "No Slot Available";
			case 10:
				return "Invalid Password";
			case 20:
				return "Password Expired";
			case 30:
				return "New Password Invalid";
			case 40:
				return "PIN Change Required";
			case 50:
				return "Other Rejection";
			default:
				return "Unknown Validation Code";
		}
	}
}