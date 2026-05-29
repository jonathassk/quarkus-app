import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.Key;
import java.util.List;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class TestEdDSA4 {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        String jwkJson = "{\"keys\":[{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"x\":\"cuyIdkFuE8qrhLqbtxEjGKyhvXC4iUH5xLgWfsASB0A\",\"kty\":\"OKP\",\"kid\":\"ae9335f5-d99f-4fca-9bcd-bf5cf01ed168\"}]}";
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        
        com.nimbusds.jose.jwk.JWK key = jwkSet.getKeys().get(0);
        System.out.println("Match class: " + key.getClass().getName());
        com.nimbusds.jose.jwk.OctetKeyPair okp = (com.nimbusds.jose.jwk.OctetKeyPair) key;
        try {
            System.out.println("To Public Key: " + okp.toPublicKey());
        } catch (Exception e) { e.printStackTrace(); }
    }
}
