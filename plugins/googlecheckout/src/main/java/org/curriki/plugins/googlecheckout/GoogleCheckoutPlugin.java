package org.curriki.plugins.googlecheckout;


import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Api;
import com.xpn.xwiki.api.XWiki;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.plugin.XWikiDefaultPlugin;
import com.xpn.xwiki.plugin.XWikiPluginInterface;
import com.xpn.xwiki.plugin.mailsender.MailSenderPluginApi;
import com.xpn.xwiki.render.XWikiPluginRenderer;
import com.xpn.xwiki.render.XWikiRenderer;
import com.xpn.xwiki.render.XWikiRenderingEngine;
import com.xpn.xwiki.web.XWikiMessageTool;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

import javax.naming.Context;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

public class GoogleCheckoutPlugin extends XWikiDefaultPlugin implements XWikiPluginInterface {

    private static final Log LOG = LogFactory.getLog(GoogleCheckoutPlugin.class);

    private String merchant="669895943580289", key="Ea0jLLapBsYxX2hRvapowg", host="hoplahup.homeip.net";
    private URL checkoutURL = new URL("https://sandbox.google.com/checkout/api/checkout/v2/merchantCheckout/Donations/" + merchant),
            orderInfoURL = new URL("https://sandbox.google.com/checkout/api/checkout/v2/reports/Merchant/" + merchant);

    private static final String ORDERPROP_user ="user",
            ORDERPROP_serialNumber = "serialNumber",
            ORDERPROP_lastHistoryState ="lastOrderState",
            ORDERPROP_financialState = "financialState",
            ORDERPROP_fulfillmentState = "fulfillmentState",
            ORDERPROP_amount = "amount",
            ORDERPROP_date = "date";
    private static ThreadLocal<NumberFormat> currencies = new ThreadLocal<NumberFormat>() {protected NumberFormat initialValue() {
            return new DecimalFormat("########.##");
        }};
    private static final String checkoutNSuri = "http://checkout.google.com/schema/2";

    private static final String DOCNAME_orderList = "GCheckout.OrderList",
            DOCNAME_oldOrderList = "GCheckout.OldOrderList",
            DOCNAME_orderClass = "GCheckout.GChOrder",
            DOCNAME_donationTrackClass = "Registration.DonationTrack";

    private static Namespace checkoutNS = Namespace.getNamespace("co", checkoutNSuri);

    private Map<String,String> languages = null;


    public GoogleCheckoutPlugin(String name, String className, XWikiContext xcontext) throws Exception {
        super(name, className, xcontext);
        languages = new HashMap<String, String>();
        languages.put("eng","en");
        languages.put("fra","fr");
        languages.put("rus", "ru");
        languages.put("spa", "es");
        languages.put("deu", "de");
        languages.put("por", "pt");
        languages.put("nld", "nl");
        languages.put("ces", "cs");
        languages.put("eus", "mk");
        languages.put("zho", "zh");
        languages.put("cha", "ch");
         // TODO: a few more 
    }


    public String getName()
    {
        return "googleCheckout";
    }

    public Api getPluginApi(XWikiPluginInterface plugin, XWikiContext context)
    {
        return new GoogleCheckoutPluginApi((GoogleCheckoutPlugin) plugin, context);
    }





    HttpClient client = null;
    private PostMethod createCheckoutPost(URL url) {
        if(client==null) {
            client = new HttpClient();
            //Protocol myhttps = new Protocol("https", new EasySSLProtocolSocketFactory());
            //client.getHostConfiguration().setHost("sandbox.google.com", 443, myhttps)
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(
                    new AuthScope(checkoutURL.getHost(), checkoutURL.getPort(), AuthScope.ANY_REALM),
                    new UsernamePasswordCredentials(merchant, key));
        }
        PostMethod post = new PostMethod(url.toExternalForm());
        post.setDoAuthentication(true);
        return post;
    }

    public String getCheckoutRedirect(String userName, String amount) throws IOException {

        List<String> errors = new ArrayList<String>();
        if(userName==null) {
            errors.add("missing-username");
        }
        if(amount==null)
            errors.add("missing-amount");

        if(!errors.isEmpty()) {
            StringBuilder msgs = new StringBuilder("errors:");
            for(String error: errors) msgs.append("googlecheckout.errors.").append(error).append(' ');
            return msgs.toString();
        }

        PostMethod post = createCheckoutPost(checkoutURL);

        // TODO: make that template static
        String cart = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<checkout-shopping-cart xmlns=\""+checkoutNSuri +"\">\n" +
                "  <shopping-cart>\n" +
                "    <items>\n" +
                "      <item>\n" +
                "        <item-name>Curriki Corporate Membership</item-name>\n" +
                "        <item-description>Donation to ensure the corporate membership of user\n" +
                "            "+userName.substring(6) +"\n" +
                "            http://"+host +"</item-description>\n" +
                "        <unit-price currency=\"USD\">"+amount+"</unit-price>\n" +
                "        <quantity>1</quantity>\n" +
                "      </item>\n" +
                "    </items>\n" +
                "    <merchant-private-data >Username:"+userName+"\n" +
                "    </merchant-private-data>\n" +
                "  </shopping-cart>\n" +
                "  <checkout-flow-support>\n" +
                "    <merchant-checkout-flow-support>\n" +
                // TODO: what to do as continue shopping URL?
                "      <continue-shopping-url>http://"+host+"/xwiki/bin/view/GCheckout/BackFromGCheckout?user="+userName+"</continue-shopping-url>\n" +
                //"      <edit-cart-url >http://"+host+"/xwiki/bin/view/GCheckout/BackFromGCheckout?user="+userName+"</edit-cart-url>\n" +
                "    </merchant-checkout-flow-support>\n" +
                "  </checkout-flow-support>\n" +
                "</checkout-shopping-cart>";

        LOG.warn("Request: " + cart);
        post.setRequestBody(cart);
        post.setRequestHeader("Content-Type","application/xml;charset=UTF-8");
        post.setRequestHeader("Accept","application/xml;charset=UTF-8");

        int status = client.executeMethod(post);
        if(status!=200) throw new IllegalStateException("Error " + post.getStatusText());
        String responseString = post.getResponseBodyAsString();
        org.jdom.Element response = parseToElement(responseString);
        //initiateOrderState(response, userName);
        //def response = new XmlParser().parseText("<x><a>blabla</a></x>");
        LOG.warn("response: ");
        return ((Element) response.getChildren().get(0)).getTextTrim();// ./redirect-url/checkout-redirect/text()
    }


    public String processNotificationAPICall(HttpServletRequest request, HttpServletResponse response, XWiki xwiki, XWikiMessageTool msg, String urlToHere) {
        String serialNumber = request.getParameter("serial-number");
        LOG.warn("Received notification from : " + request.getRemoteHost() + " params: " + request.getParameterMap());
        String processStatus = orderNotification(serialNumber, xwiki, msg, urlToHere);
        int status = Integer.parseInt(processStatus.substring(0,3));
        processStatus = processStatus.substring(3);
        if(status==200) {
            response.setContentType("application/xml;charset=utf-8");
            return "<notification-acknowledgment xmlns='http://checkout.google.com/schema/2' "+
                    "serial-number='"+serialNumber+"' />";
        } else {
            response.setStatus(status, processStatus);
            return "<error>" + processStatus + "</error>";
        }
    }


    private Element parseToElement(String completeDoc) {
        try {
            return new SAXBuilder().build(new StringReader(completeDoc)).getRootElement();
        } catch (Exception e) {
            throw new RuntimeException("Parsing issue: " + e, e);
        }
    }

    private Object[] obtainOrderHistory(String serialNumber) throws IOException {
        PostMethod post = createCheckoutPost(orderInfoURL);
        post.setRequestBody("<notification-history-request xmlns='http://checkout.google.com/schema/2'> " +
                "<serial-number>"+serialNumber+"</serial-number>" +
                "</notification-history-request>");
        int status = client.executeMethod(post);
        String responseText = post.getResponseBodyAsString(32768);
        LOG.warn(responseText);
        if(status==200) {
            Element n= parseToElement(responseText);
            return new Object[]{n, responseText};
        } else
            throw new IllegalStateException(post.getStatusCode() + " " + post.getStatusText());
    }

    // the central method of processing
    public String orderNotification(String historyStepSerialNumber, XWiki xwiki, XWikiMessageTool msg, String urlToHere) {
        try {
            Object[] r= obtainOrderHistory(historyStepSerialNumber);
            Element node = (Element) r[0];
            String fullDoc = (String) r[1];
            LOG.warn("Obtained History: " + fullDoc);
            updateOrderListDoc(node, fullDoc, historyStepSerialNumber, xwiki, msg, urlToHere);
            return "200 OK";
        } catch (Exception ex) {
            LOG.warn("Issue at order processing.", ex);
            String status = ex.getMessage();
            if(! (status.matches("[0-9][0-9][0-9].*")))
                return "500 " + status;
            else return status;
        }

    }

    private static Map<String,XPath> expressions = new HashMap<String, XPath>();

    private static XPath getOrPutXPath(String s) {
        try {
            XPath exp = expressions.get(s);
            if(exp==null) {
                exp = XPath.newInstance(s);
                exp.addNamespace(checkoutNS);
                expressions.put(s,exp);
            }
            return exp;
        } catch (JDOMException e) {
            e.printStackTrace();
            throw new IllegalStateException("XPath expressions should be ok.", e);
        }
    }

    private static Element selectSingleEltXPath(String s, Element elt) {
        try {
            XPath exp = getOrPutXPath(s);
            return (Element) exp.selectSingleNode(elt);
        } catch (JDOMException e) {
            e.printStackTrace();
            throw new IllegalStateException("XPath expressions should be ok.", e);
        }
    }


    private static List selectMultipleXPath(String s, Element elt) {
        try {
            XPath exp = getOrPutXPath(s);
            return exp.selectNodes(elt);
        } catch (JDOMException e) {
            e.printStackTrace();
            throw new IllegalStateException("XPath expressions should be ok.", e);
        }
    }

    /**
     *
     * @param node the recevied history document's root
     * @param orderStateDoc the full String (for storage)
     * @param historyStepSerialNumber the serial-number indicated
     * @param xwiki the wiki we're living in
     */
    public void updateOrderListDoc(Element node, String orderStateDoc, String historyStepSerialNumber, XWiki xwiki, XWikiMessageTool msg, String urlToHere) throws Exception {

        try {
            Document orderList = xwiki.getDocument(DOCNAME_orderList);
            String orderNumber = selectSingleEltXPath("./co:order-summary/co:google-order-number", node).getTextNormalize();
            float amount = currencies.get().parse(selectSingleEltXPath("./co:order-summary/co:order-total", node).getTextNormalize()).longValue();
            com.xpn.xwiki.api.Object orderObj = orderList.getObject(DOCNAME_orderClass,ORDERPROP_serialNumber, orderNumber);
            String userName;
            if(orderObj==null) {
                int i = orderList.createNewObject(DOCNAME_orderClass);
                orderObj = orderList.getObject(DOCNAME_orderClass, i);
                orderObj.set(ORDERPROP_serialNumber, orderNumber);
                userName = selectSingleEltXPath("//co:merchant-private-data", node).getTextNormalize();
                orderObj.set(ORDERPROP_amount, amount);
                if(userName.startsWith("Username:")) userName = userName.substring("Username:".length());
                orderObj.set(ORDERPROP_user, userName);
            }

            // note last state
            orderObj.set(ORDERPROP_lastHistoryState, orderStateDoc);
            orderObj.set(ORDERPROP_financialState,
                    selectSingleEltXPath("//co:financial-order-state",node).getTextNormalize());
            orderObj.set(ORDERPROP_fulfillmentState,
                    selectSingleEltXPath("//co:fulfillment-order-state",node).getTextNormalize());
            orderObj.set(ORDERPROP_date, new Date());
            orderList.saveWithProgrammingRights("Receiving payment for order " + orderNumber);


            // finished?

            // see http://code.google.com/intl/fr/apis/checkout/developer/Google_Checkout_XML_Donation_API_Notification_API.html#tag_financial-order-state
            // we should get CHARGED at some point but I always got CHARGING and received the mails
            boolean finished = ("CHARGEABLE".equals(orderObj.get(ORDERPROP_financialState)));
            userName = (String) orderObj.get(ORDERPROP_user);
            LOG.warn("financialState is " + orderObj.get(ORDERPROP_financialState));

            if(finished) {
                LOG.warn("Finished checkout for user " + userName);
                Document userDoc = xwiki.getDocument(userName);
                com.xpn.xwiki.api.Object userObj = userDoc.getObject("XWiki.XWikiUsers");
                if(userObj!=null) {
                    // if email is there and matches, activate
                    Set<String> doneEmails = new HashSet<String>();
                    for(Object emailElt : selectMultipleXPath("//co:email", node)) {
                        String email = ((Element) emailElt).getTextNormalize();
                        if(email!=null && email.equals(userObj.get("email"))) {
                            if(doneEmails.contains(email)) continue;
                            doneEmails.add(email);
                            LOG.warn("activating user's email " + email);
                            // TODO: only if not yet active
                            userObj.set("email_undeliverable",0);
                            userObj.set("active", 1);
                            userDoc.saveWithProgrammingRights("Validating thanks to payment.");
                            sendConfirmationEmail(xwiki, userDoc.getName(),
                                    (String) userObj.get("email"), languages.get((String) userObj.getProperty("language").getValue()), (String) userObj.get("memberType"),
                                    msg, urlToHere);
                        }
                    }

                    // update user-donation-object
                    com.xpn.xwiki.api.Object donationTrackObj = userDoc.getObject(DOCNAME_donationTrackClass);
                    if(donationTrackObj==null) {
                        userDoc.createNewObject(DOCNAME_donationTrackClass);
                        donationTrackObj = userDoc.getObject(DOCNAME_donationTrackClass);
                    }
                    donationTrackObj.set("lastDonation", currencies.get().format(amount));
                    donationTrackObj.set("lastDonationDate", new Date());

                    String totalS = (String) donationTrackObj.get("totalDonation");
                    float totalDonation = 0;
                    if(totalS!=null && totalS.length()>0)
                        totalDonation = Float.parseFloat(totalS);
                    donationTrackObj.set("totalDonation", currencies.get().format(totalDonation + amount));
                    userDoc.saveWithProgrammingRights("Received donation");

                }

                archiveOrder(xwiki, orderNumber);
                LOG.warn("Finished successfully processing notification : " + historyStepSerialNumber);

            }
        } catch (Exception ex) {
            LOG.warn("Issue at processing order: ", ex);
        }
    }

    private void sendConfirmationEmail(XWiki xwiki, String username, String email, String lang, String memberType, XWikiMessageTool msg, String urlToHere) throws Exception {
        String subjectMsgKey = "registration.email.welcome";

        String emailDocName = "corporate".equals(memberType) ?
                "CorporateRegCompleteEmail" :"MemberRegCompleteEmail";

        long time=System.currentTimeMillis();
        MailSenderPluginApi mailsender = (MailSenderPluginApi) xwiki.getPlugin("mailsender");



        Object emailDocO = null;
        URL url = new URL( new URL(urlToHere),
                "/xwiki/bin/view/Registration/" + emailDocName + "?xpage=plain&language=" + lang + "&username=" + username);
        LOG.info("Fetching " + url + " as mail body.");
        emailDocO = url.getContent();
        if(emailDocO instanceof InputStream) {
            emailDocO = org.apache.commons.io.IOUtils.toString((InputStream) emailDocO, "utf-8");
        }
        String text = (String) emailDocO;

        LOG.warn("Sending mail for purpose " + subjectMsgKey + " to " + email + " with page " + emailDocName + '.');
        System.out.println("Took: " + (System.currentTimeMillis()-time) + " ms to prepare email body.");
        time=System.currentTimeMillis();

        String from = msg.get("registration.email");
        if(from==null || from.length()==0) from="webmaster@curriki.org";
        if(!msg.get("registration.email.name").equals("registration.email.name"))
            from = msg.get("registration.email.name") + "<" + from + ">";

        mailsender.sendHtmlMessage(from, email, null, null,
                msg.get(subjectMsgKey), text, text.replaceAll("<[^>]*>",""), Collections.emptyList());
        System.out.println("Took: " + (System.currentTimeMillis()-time) + " ms to send email.");
    }

    public String archiveOrder(XWiki xwiki, String serialNumber) throws XWikiException{
        try {
            // move to finished orders
            Document orderList = xwiki.getDocument(DOCNAME_orderList);
            Document oldOrders = xwiki.getDocument(DOCNAME_oldOrderList);

            com.xpn.xwiki.api.Object finishedOrder = oldOrders.getObject(DOCNAME_orderClass,oldOrders.createNewObject(DOCNAME_orderClass));
            com.xpn.xwiki.api.Object order = orderList.getObject(DOCNAME_orderClass,ORDERPROP_serialNumber, serialNumber);
            if(order==null || order.getProperties()==null)
                return "error.order-not-found";


            for(com.xpn.xwiki.api.Element propElt: order.getProperties()) {
                String propName = propElt.getName();
                //LOG.warn("Copying property " + propElt + " for name " + propName);
                finishedOrder.set(propName, order.get(propName));
            }

            oldOrders.saveWithProgrammingRights("Archiving " + serialNumber);
            orderList.removeObject(order);
            orderList.saveWithProgrammingRights("Archiving " + serialNumber);
            return "ok-archived";
        } catch (XWikiException e) {
            LOG.warn("Issue at archiving " + e);
            return "archive-error";
        }
    }
}