package com.ShikharKothari0.SeatLock.service;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
class RazorpayGatewayTest {
    @Test void verifiesHmacAndRejectsTampering(){
        byte[] body="The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        String signature="f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        assertTrue(RazorpayGateway.verify(body,signature,"key"));
        assertFalse(RazorpayGateway.verify("changed".getBytes(StandardCharsets.UTF_8),signature,"key"));
        assertFalse(RazorpayGateway.verify(body,signature,"wrong"));
        assertFalse(RazorpayGateway.verify(body,"invalid","key"));
    }
    @Test void refusesLiveKeysAndMissingConfiguration(){
        assertThrows(IllegalArgumentException.class,()->new RazorpayGateway(new ObjectMapper(),true,"rzp_live_example","secret","w".repeat(32)));
        assertThrows(IllegalArgumentException.class,()->new RazorpayGateway(new ObjectMapper(),true,"rzp_test_example","","w".repeat(32)));
        assertThrows(IllegalArgumentException.class,()->new RazorpayGateway(new ObjectMapper(),true,"rzp_test_example","secret",""));
        assertDoesNotThrow(()->new RazorpayGateway(new ObjectMapper(),false,"","",""));
    }
}
