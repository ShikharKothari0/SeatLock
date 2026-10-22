package com.ShikharKothari0.SeatLock.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

@Component
public class RazorpayGateway {
    private final ObjectMapper json;
    private final String key,secret,webhookSecret;
    private final boolean enabled;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public RazorpayGateway(ObjectMapper json,@Value("${seatlock.payments.razorpay-enabled:false}") boolean enabled,
        @Value("${RAZORPAY_KEY_ID:}") String key,@Value("${RAZORPAY_KEY_SECRET:}") String secret,@Value("${RAZORPAY_WEBHOOK_SECRET:}") String webhookSecret) {
        this.json=json;this.enabled=enabled;this.key=key;this.secret=secret;this.webhookSecret=webhookSecret;
        if(enabled && (!key.startsWith("rzp_test_") || secret.isBlank() || webhookSecret.length()<32))
            throw new IllegalArgumentException("Razorpay requires test keys and a webhook secret of at least 32 characters");
    }
    public boolean enabled(){return enabled;}
    public String key(){return key;}
    public JsonNode call(String method,String path,Object body,String refundKey) {
        if(!enabled) throw new IllegalStateException("Razorpay test payments are disabled");
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1"+path)).timeout(Duration.ofSeconds(12))
                .header("Authorization","Basic "+Base64.getEncoder().encodeToString((key+":"+secret).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type","application/json");
            if(refundKey!=null) request.header("X-Refund-Idempotency",refundKey);
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300) throw new IllegalStateException("Payment provider could not complete the request");
            return json.readTree(response.body());
        } catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new IllegalStateException("Payment check interrupted");}
        catch(Exception ex) {throw new IllegalStateException("Payment provider unavailable; check the existing attempt again",ex);}
    }
    public boolean verifyCheckout(String order,String payment,String signature){return verify((order+"|"+payment).getBytes(StandardCharsets.UTF_8),signature,secret);}
    public boolean verifyWebhook(byte[] body,String signature){return enabled && verify(body,signature,webhookSecret);}
    static boolean verify(byte[] data,String signature,String secret) {
        try {
            if(signature==null || !signature.matches("[a-fA-F0-9]{64}")) return false;
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(data),HexFormat.of().parseHex(signature));
        } catch(Exception ex){return false;}
    }
}
