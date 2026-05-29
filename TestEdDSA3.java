import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.Key;
import java.util.List;

public class TestEdDSA3 {
    public static void main(String[] args) throws Exception {
        String jwkJson = "{\"keys\":[{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"x\":\"cuyIdkFuE8qrhLqbtxEjGKyhvXC4iUH5xLgWfsASB0A\",\"kty\":\"OKP\",\"kid\":\"ae9335f5-d99f-4fca-9bcd-bf5cf01ed168\"}]}";
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        
        String dummyToken = "eyJhbGciOiJFZERTQSIsImtpZCI6ImFlOTMzNWY1LWQ5OWYtNGZjYS05YmNkLWJmNWNmMDFlZDE2OCJ9.eyJpYXQiOjE3ODAwMTEyODksIm5hbWUiOiJqb25hdGhhcyBzYWxlcyIsImVtYWlsIjoiam9uYXRoYXMwOUBnbWFpbC5jb20iLCJlbWFpbFZlcmlmaWVkIjp0cnVlLCJjcmVhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJ1cGRhdGVkQXQiOiIyMDI2LTA1LTIyVDAwOjM4OjAwLjA4NVoiLCJyb2xlIjoiYXV0aGVudGljYXRlZCIsImJhbm5lZCI6ZmFsc2UsImJhblJlYXNvbiI6bnVsbCwiYmFuRXhwaXJlcyI6bnVsbCwiaWQiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJzdWIiOiIwY2Y3MWY2OS00ZjcwLTRmNDYtYTE3OC02YTkyZDkzOWViNjkiLCJleHAiOjE3ODAwMTIxODksImlzcyI6Imh0dHBzOi8vZXAtc3RlZXAtbmlnaHQtYWkxanJmdWsubmVvbmF1dGguYy00LnVzLWVhc3QtMS5hd3MubmVvbi50ZWNoIiwiYXVkIjoiaHR0cHM6Ly9lcC1zdGVlcC1uaWdodC1haTFqcmZ1ay5uZW9uYXV0aC5jLTQudXMtZWFzdC0xLmF3cy5uZW9uLnRlY2gifQ.dGVzdF9zaWduYXR1cmU";
        SignedJWT signed = SignedJWT.parse(dummyToken);
        
        com.nimbusds.jose.proc.JWSVerificationKeySelector<?> keySelector = 
            new com.nimbusds.jose.proc.JWSVerificationKeySelector<>(JWSAlgorithm.EdDSA, new ImmutableJWKSet<>(jwkSet));
            
        System.out.println("Trying to call createKeySelector...");
        
        List<com.nimbusds.jose.jwk.JWK> matches = keySelector.getJWKSource().get(new com.nimbusds.jose.jwk.JWKSelector(com.nimbusds.jose.jwk.JWKMatcher.forJWSHeader(signed.getHeader())), null);
        System.out.println("Matches from JWKSource: " + matches.size());
        
        // Let's manually convert
        for(com.nimbusds.jose.jwk.JWK match : matches) {
            System.out.println("Match class: " + match.getClass().getName());
            if (match instanceof com.nimbusds.jose.jwk.OctetKeyPair) {
                com.nimbusds.jose.jwk.OctetKeyPair okp = (com.nimbusds.jose.jwk.OctetKeyPair) match;
                try {
                    System.out.println("To Public Key: " + okp.toPublicKey());
                } catch (Exception e) { e.printStackTrace(); }
            }
            try {
                System.out.println("To Java Key (KeyConverter): " + com.nimbusds.jose.jwk.KeyConverter.toJavaKeys(java.util.Collections.singletonList(match)));
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
