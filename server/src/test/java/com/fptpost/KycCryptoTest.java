package com.fptpost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.security.KycCrypto;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KycCryptoTest {
    final SecureRandom random=new SecureRandom();
    String key(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    @Test void encryptionUsesRandomNoncesAndAuthenticatesTheOwnerAndPayload() throws Exception {
        var crypto=new KycCrypto(new ObjectMapper(),random,new ObjectMapper().writeValueAsString(Map.of("key1",key())),"key1",key());
        byte[] plain="sensitive test data".getBytes(StandardCharsets.UTF_8), first=crypto.encrypt(plain,"owner:1"), second=crypto.encrypt(plain,"owner:1");
        assertThat(first).isNotEqualTo(second).isNotEqualTo(plain);assertThat(crypto.decrypt(first,"owner:1")).isEqualTo(plain);
        assertThatThrownBy(()->crypto.decrypt(first,"owner:2")).isInstanceOf(AppException.class);
        first[first.length-1]^=1;assertThatThrownBy(()->crypto.decrypt(first,"owner:1")).isInstanceOf(AppException.class);
    }
    @Test void rotationKeepsOldCiphertextsReadableAndIdentityHashesStable() throws Exception {
        var mapper=new ObjectMapper();var keys=Map.of("old",key(),"new",key());String hash=key();
        var old=new KycCrypto(mapper,random,mapper.writeValueAsString(keys),"old",hash);
        var rotated=new KycCrypto(mapper,random,mapper.writeValueAsString(keys),"new",hash);
        byte[] plain="document".getBytes(StandardCharsets.UTF_8);
        assertThat(rotated.decrypt(old.encrypt(plain,"file:1"),"file:1")).isEqualTo(plain);
        assertThat(old.identityHash("123456789012")).isEqualTo(rotated.identityHash("123456789012"));
        assertThat(rotated.verify("owner:session:file:expires",rotated.sign("owner:session:file:expires"))).isTrue();
        assertThat(rotated.verify("different:session:file:expires",rotated.sign("owner:session:file:expires"))).isFalse();
    }
    @Test void missingKeysFailClosedAndNeverFallBackToPlaintext() {
        var crypto=new KycCrypto(new ObjectMapper(),random,"","","");
        assertThatThrownBy(()->crypto.encrypt(new byte[]{1},"file")).isInstanceOf(AppException.class);
        assertThatThrownBy(()->crypto.identityHash("123456789012")).isInstanceOf(AppException.class);
    }
}
