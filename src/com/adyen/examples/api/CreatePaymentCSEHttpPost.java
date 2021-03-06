package com.adyen.examples.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * Create Client-Side Encryption Payment (HTTP Post)
 * 
 * Merchants that require more stringent security protocols or do not want the additional overhead of managing their PCI
 * compliance, may decide to implement Client-Side Encryption (CSE). This is particularly useful for Mobile payment
 * flows where only cards are being offered, as it may result in faster load times and an overall improvement to the
 * shopper flow. The Adyen Hosted Payment Page (HPP) provides the most comprehensive level of PCI compliancy and you do
 * not have any PCI obligations. Using CSE reduces your PCI scope when compared to implementing the API without
 * encryption.
 * 
 * If you would like to implement CSE, please provide the completed PCI Self Assessment Questionnaire (SAQ) A to the
 * Adyen Support Team (support@adyen.com). The form can be found here:
 * https://www.pcisecuritystandards.org/security_standards/documents.php?category=saqs
 * 
 * Please note: using our API requires a web service user. Set up your Webservice user:
 * Adyen CA >> Settings >> Users >> ws@Company. >> Generate Password >> Submit
 * 
 * @link /2.API/HttpPost/CreatePaymentCSE
 * @author Created by Adyen - Payments Made Easy
 */

@WebServlet(urlPatterns = { "/2.API/HttpPost/CreatePaymentCSE" })
public class CreatePaymentCSEHttpPost extends HttpServlet {

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		// Generate current time server-side and set it as request attribute
		request.setAttribute("generationTime", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()));

		// Forward request to corresponding JSP page
		request.getRequestDispatcher("/2.API/create-payment-cse.jsp").forward(request, response);

	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		/**
		 * HTTP Post settings
		 * - apiUrl: URL of the Adyen API you are using (Test/Live)
		 * - wsUser: your web service user
		 * - wsPassword: your web service user's password
		 */
		String apiUrl = "https://pal-test.adyen.com/pal/adapter/httppost";
		String wsUser = "YourWSUser";
		String wsPassword = "YourWSPassword";

		/**
		 * Create HTTP Client (using Apache HttpComponents library) and set up Basic Authentication
		 */
		CredentialsProvider provider = new BasicCredentialsProvider();
		UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(wsUser, wsPassword);
		provider.setCredentials(AuthScope.ANY, credentials);

		HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();

		/**
		 * A payment can be submitted with a HTTP Post request to the API, containing the following variables:
		 * 
		 * <pre>
		 * - action                      : Payment.authorise
		 * - paymentRequest
		 *   - merchantAccount           : The merchant account for which you want to process the payment
		 *   - amount
		 *       - currency              : The three character ISO currency code.
		 *       - value                 : The transaction amount in minor units (e.g. EUR 1,00 = 100).
		 *   - reference                 : Your reference for this payment.
		 *   - shopperIP                 : The shopper's IP address. (recommended)
		 *   - shopperEmail              : The shopper's email address. (recommended)
		 *   - shopperReference          : An ID that uniquely identifes the shopper, such as a customer id. (recommended)
		 *   - fraudOffset               : An integer that is added to the normal fraud score. (optional)
		 *   - additionalData.card.encrypted.json: The encrypted card catched by the POST variables.
		 * </pre>
		 */
		List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
		Collections.addAll(postParameters,
			new BasicNameValuePair("action", "Payment.authorise"),

			new BasicNameValuePair("paymentRequest.merchantAccount", "YourMerchantAccount"),
			new BasicNameValuePair("paymentRequest.reference",
				"TEST-PAYMENT-" + new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(new Date())),
			new BasicNameValuePair("paymentRequest.shopperIP", "123.123.123.123"),
			new BasicNameValuePair("paymentRequest.shopperEmail", "test@example.com"),
			new BasicNameValuePair("paymentRequest.shopperReference", "YourReference"),
			new BasicNameValuePair("paymentRequest.fraudOffset", "0"),

			new BasicNameValuePair("paymentRequest.amount.currency", "EUR"),
			new BasicNameValuePair("paymentRequest.amount.value", "199"),

			new BasicNameValuePair("paymentRequest.additionalData.card.encrypted.json",
				request.getParameter("adyen-encrypted-data"))
			);

		/**
		 * Send the HTTP Post request with the specified variables.
		 */
		HttpPost httpPost = new HttpPost(apiUrl);
		httpPost.setEntity(new UrlEncodedFormEntity(postParameters));

		HttpResponse httpResponse = client.execute(httpPost);
		String paymentResponse = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");

		/**
		 * Keep in mind that you should handle errors correctly.
		 * If the Adyen platform does not accept or store a submitted request, you will receive a HTTP response with
		 * status code 500 Internal Server Error. The fault string can be found in the paymentResponse.
		 */
		if (httpResponse.getStatusLine().getStatusCode() == 500) {
			throw new ServletException(paymentResponse);
		}
		else if (httpResponse.getStatusLine().getStatusCode() != 200) {
			throw new ServletException(httpResponse.getStatusLine().toString());
		}

		/**
		 * If the payment passes validation a risk analysis will be done and, depending on the outcome, an authorisation
		 * will be attempted. You receive a payment response with the following fields:
		 * 
		 * <pre>
		 * - pspReference    : Adyen's unique reference that is associated with the payment.
		 * - resultCode      : The result of the payment. Possible values: Authorised, Refused, Error or Received.
		 * - authCode        : The authorisation code if the payment was successful. Blank otherwise.
		 * - refusalReason   : Adyen's mapped refusal reason, populated if the payment was refused.
		 * </pre>
		 */
		Map<String, String> paymentResult = parseQueryString(paymentResponse);
		PrintWriter out = response.getWriter();

		out.println("Payment Result:");
		out.println("- pspReference: " + paymentResult.get("paymentResult.pspReference"));
		out.println("- resultCode: " + paymentResult.get("paymentResult.resultCode"));
		out.println("- authCode: " + paymentResult.get("paymentResult.authCode"));
		out.println("- refusalReason: " + paymentResult.get("paymentResult.refusalReason"));

	}

	/**
	 * Parse the result of the HTTP Post request (will be returned in the form of a query string)
	 */
	private Map<String, String> parseQueryString(String queryString) throws UnsupportedEncodingException {
		Map<String, String> parameters = new HashMap<String, String>();
		String[] pairs = queryString.split("&");

		for (String pair : pairs) {
			String[] keyval = pair.split("=");
			parameters.put(URLDecoder.decode(keyval[0], "UTF-8"), URLDecoder.decode(keyval[1], "UTF-8"));
		}

		return parameters;
	}

}
