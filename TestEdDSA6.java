import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.OctetKeyPair;

public class TestEdDSA6 {
    public static void main(String[] args) throws Exception {
        String jwkJson = "{\"keys\":[{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"x\":\"cuyIdkFuE8qrhLqbtxEjGKyhvXC4iUH5xLgWfsASB0A\",\"kty\":\"OKP\",\"kid\":\"ae9335f5-d99f-4fca-9bcd-bf5cf01ed168\"}]}";
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        
        OctetKeyPair okp = (OctetKeyPair) jwkSet.getKeys().get(0);
        System.out.println("Got OKP: " + okp);
        
        Ed25519Verifier verifier = new Ed25519Verifier(okp);
        System.out.println("Created Ed25519Verifier: " + verifier);
        
        String dummyToken = "eyJhbGciOiJFZERTQSIsImtpZCI6ImFlOTMzNWY1LWQ5OWYtNGZjYS05YmNkLWJmNWNmMDFlZDE2OCJ9.eyJpYXQiOjE3ODAwMTEyODksIm5hbWUiOiJqb25hdGhhcyBzYWxlcyIsImVtYWlsIjoiam9uYXRoYXMwOUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJ1cGRhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImJhbm5lZCI6ZmFsc2UsImJhblJlYXNvbiI6bnVsbCwiYmFuRXhwaXJlcyI6bnVsbCwiaWQiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJzdWIiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJleHAiOjE3ODAwMTIxODksImlzcyI6Imh0dHBzOi8vZXAtc3RlZXAtbmlnaHQtYWkxanJmdWsubmVvbmF1dGguYy00LnVzLWVhc3QtMS5hd3MubmVvbi50ZWNoIiwiYXVkIjoiaHR0cHM6Ly9lcC1zdGVlcC1uaWdodC1haTFqcmZ1ay5uZW9uYXV0aC5jLTQudXMtZWFzdC0xLmF3cy5uZW9uLnRlY2gifQ.dGVzdF9zaWduYXR1cmU";
        SignedJWT signed = SignedJWT.parse(dummyToken);
        
        System.out.println("Verifying...");
        boolean result = signed.verify(verifier);
        System.out.println("Verification result: " + result);
    }
}
