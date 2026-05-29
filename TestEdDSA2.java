import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;

public class TestEdDSA2 {
    public static void main(String[] args) throws Exception {
        String jwkJson = "{\"keys\":[{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"x\":\"cuyIdkFuE8qrhLqbtxEjGKyhvXC4iUH5xLgWfsASB0A\",\"kty\":\"OKP\",\"kid\":\"ae9335f5-d99f-4fca-9bcd-bf5cf01ed168\"}]}";
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        JWK key = jwkSet.getKeys().get(0);
        
        String dummyToken = "eyJhbGciOiJFZERTQSIsImtpZCI6ImFlOTMzNWY1LWQ5OWYtNGZjYS05YmNkLWJmNWNmMDFlZDE2OCJ9.eyJpYXQiOjE3ODAwMTEyODksIm5hbWUiOiJqb25hdGhhcyBzYWxlcyIsImVtYWlsIjoiam9uYXRoYXMwOUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJ1cGRhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImJhbm5lZCI6ZmFsc2UsImJhblJlYXNvbiI6bnVsbCwiYmFuRXhwaXJlcyI6bnVsbCwiaWQiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJzdWIiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJleHAiOjE3ODAwMTIxODksImlzcyI6Imh0dHBzOi8vZXAtc3RlZXAtbmlnaHQtYWkxanJmdWsubmVvbmF1dGguYy00LnVzLWVhc3QtMS5hd3MubmVvbi50ZWNoIiwiYXVkIjoiaHR0cHM6Ly9lcC1zdGVlcC1uaWdodC1haTFqcmZ1ay5uZW9uYXV0aC5jLTQudXMtZWFzdC0xLmF3cy5uZW9uLnRlY2gifQ.dGVzdF9zaWduYXR1cmU";
        SignedJWT signed = SignedJWT.parse(dummyToken);
        
        System.out.println("Key KTY: " + key.getKeyType());
        System.out.println("Key ALG: " + key.getAlgorithm());
        System.out.println("Key USE: " + key.getKeyUse());
        System.out.println("Key KID: " + key.getKeyID());
        
        System.out.println("Header ALG: " + signed.getHeader().getAlgorithm());
        System.out.println("Header KID: " + signed.getHeader().getKeyID());
        
        JWKMatcher matcher = JWKMatcher.forJWSHeader(signed.getHeader());
        System.out.println("Matcher matches key? " + matcher.matches(key));
        
        // JWSVerificationKeySelector explicitly creates a matcher. Let's see how:
        com.nimbusds.jose.proc.JWSVerificationKeySelector<?> keySelector = 
            new com.nimbusds.jose.proc.JWSVerificationKeySelector<>(JWSAlgorithm.EdDSA, new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(jwkSet));
            
        System.out.println("Selected keys: " + keySelector.selectJWSKeys(signed.getHeader(), null));
    }
}
