import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.JWSVerifier;
import java.util.List;
import java.security.Key;

public class TestEdDSA {
    public static void main(String[] args) throws Exception {
        String jwkJson = "{\"keys\":[{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"x\":\"cuyIdkFuE8qrhLqbtxEjGKyhvXC4iUH5xLgWfsASB0A\",\"kty\":\"OKP\",\"kid\":\"ae9335f5-d99f-4fca-9bcd-bf5cf01ed168\"}]}";
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        System.out.println("Parsed JWKSet: " + jwkSet.getKeys().size() + " keys");
        System.out.println("Key ID: " + jwkSet.getKeys().get(0).getKeyID());
        
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);
        JWSVerificationKeySelector<SecurityContext> keySelector = 
            new JWSVerificationKeySelector<>(JWSAlgorithm.EdDSA, jwkSource);
            
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        
        String dummyToken = "eyJhbGciOiJFZERTQSIsImtpZCI6ImFlOTMzNWY1LWQ5OWYtNGZjYS05YmNkLWJmNWNmMDFlZDE2OCJ9.eyJpYXQiOjE3ODAwMTEyODksIm5hbWUiOiJqb25hdGhhcyBzYWxlcyIsImVtYWlsIjoiam9uYXRoYXMwOUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJ1cGRhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImJhbm5lZCI6ZmFsc2UsImJhblJlYXNvbiI6bnVsbCwiYmFuRXhwaXJlcyI6bnVsbCwiaWQiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJzdWIiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJleHAiOjE3ODAwMTIxODksImlzcyI6Imh0dHBzOi8vZXAtc3RlZXAtbmlnaHQtYWkxanJmdWsubmVvbmF1dGguYy00LnVzLWVhc3QtMS5hd3MubmVvbi50ZWNoIiwiYXVkIjoiaHR0cHM6Ly9lcC1zdGVlcC1uaWdodC1haTFqcmZ1ay5uZW9uYXV0aC5jLTQudXMtZWFzdC0xLmF3cy5uZW9uLnRlY2gifQ.dGVzdF9zaWduYXR1cmU";
        
        SignedJWT signed = SignedJWT.parse(dummyToken);
        System.out.println("Parsed SignedJWT Header: " + signed.getHeader().toJSONObject());
        
        List<? extends Key> keys = keySelector.selectJWSKeys(signed.getHeader(), null);
        System.out.println("Keys selected by keySelector: " + keys.size());
        
        try {
            processor.process(signed, null);
            System.out.println("Process success!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
