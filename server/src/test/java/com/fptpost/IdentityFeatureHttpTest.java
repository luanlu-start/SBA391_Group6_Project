package com.fptpost;

import com.fasterxml.jackson.databind.*;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identityfeatures;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.data.mongodb.auto-index-creation=false",
        "app.registration.requests-per-minute=1000", "management.health.mail.enabled=false", "app.authentication.session-cleanup-cron=-"
})
@AutoConfigureMockMvc
abstract class IdentityFeatureHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionRepository sessions;
    @Autowired OtpCodeRepository otps;
    @Autowired ContactChangeRepository contacts;
    @Autowired AddressRepository addresses;
    @Autowired AccountDataRequestRepository dataRequests;
    @Autowired ConsentRepository consents;
    @Autowired KycRequestRepository kycRequests;
    @Autowired KycFileRepository kycFiles;
    @Autowired KycIdentityClaimRepository kycClaims;
    @Autowired IdentityAuditLogRepository auditLogs;
    @Autowired CourierProfileRepository couriers;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean Clock identityClock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    final List<MimeMessage> delivered = new CopyOnWriteArrayList<>();
    static final AtomicInteger sequence = new AtomicInteger();
    static final Path files = temporaryDirectory();
    User user;
    String token;
    String ip;
    static final String encryptionKey = randomKey(), hashKey = randomKey();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.files.directory", files::toString);
        registry.add("KYC_ENCRYPTION_KEYS", () -> "{\"test\":\"" + encryptionKey + "\"}");
        registry.add("KYC_ACTIVE_KEY_ID", () -> "test"); registry.add("KYC_HASH_KEY", () -> hashKey);
    }
    private static String randomKey() { byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key); return Base64.getEncoder().encodeToString(key); }
    private static Path temporaryDirectory() {
        try { return Files.createTempDirectory("fpost-identity-tests-"); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    @BeforeEach void setupIdentity() throws Exception {
        int number = sequence.incrementAndGet(); ip = "192.0.2." + number;
        now.set(Instant.parse("2026-10-20T12:00:00Z"));
        when(identityClock.instant()).thenAnswer(invocation -> now.get());
        reset(mailSender); delivered.clear();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> { MimeMessage message = invocation.getArgument(0); message.saveChanges(); delivered.add(message); return null; })
                .when(mailSender).send(any(MimeMessage.class));
        couriers.deleteAll(); auditLogs.deleteAll(); kycClaims.deleteAll(); kycFiles.deleteAll(); kycRequests.deleteAll(); consents.deleteAll();
        contacts.deleteAll(); addresses.deleteAll(); dataRequests.deleteAll(); sessions.deleteAll(); otps.deleteAll(); profiles.deleteAll(); users.deleteAll();
        user = createUser("member" + number + "@example.com", "+849" + String.format(Locale.ROOT, "%08d", number));
        token = access(login(user.getEmail(), "OldPassword123!", 200));
    }
    User createUser(String email, String phone) {
        return new TransactionTemplate(transactions).execute(transaction -> {
            User created = users.saveAndFlush(User.builder().publicId(UUID.randomUUID()).email(email).phone(phone)
                    .passwordHash(encoder.encode("OldPassword123!")).status(UserStatus.ACTIVE).emailVerifiedAt(now.get())
                    .createdAt(now.get()).updatedAt(now.get()).roles(new HashSet<>(Set.of(roles.findByCode("SENDER").orElseThrow()))).build());
            profiles.saveAndFlush(UserProfile.builder().user(created).fullName("Người Dùng").dob(LocalDate.of(1990,1,1)).adultConfirmedAt(now.get()).build());
            return created;
        });
    }
    MvcResult send(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mvc.perform(request.with(r -> { r.setRemoteAddr(ip); return r; })).andExpect(status().is(status)).andReturn();
    }
    MvcResult write(String method, String path, Object payload, String bearer, int status) throws Exception {
        var request = switch (method) { case "PATCH" -> patch(path); case "PUT" -> put(path); default -> post(path); };
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return send(request.contentType("application/json").content(json.writeValueAsBytes(payload)), status);
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    JsonNode data(MvcResult result) throws Exception { return body(result).path("data"); }
    String access(MvcResult result) throws Exception { return data(result).path("accessToken").asText(); }
    MvcResult login(String email, String password, int status) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn(); var value = data(csrf);
        return send(post("/api/v1/auth/login").cookie(AuthenticationHttpTest.cookie(csrf,"XSRF-TOKEN"))
                .header(value.path("headerName").asText(),value.path("token").asText()).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("identifier",email,"password",password))),status);
    }
    String mailText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return (String)part.getContent();
        if (part.getContent() instanceof Multipart parts) for(int i=0;i<parts.getCount();i++){String text=mailText(parts.getBodyPart(i));if(text!=null)return text;}
        return null;
    }
    String otp() throws Exception {
        var match=Pattern.compile("(?<!\\d)\\d{6}(?!\\d)").matcher(mailText(delivered.getLast()));
        if(!match.find())throw new AssertionError("Missing email OTP");return match.group();
    }
    MockMultipartFile image(String field, String format) throws Exception {
        var out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),format,out);
        return new MockMultipartFile(field,"photo."+format,"image/"+format,out.toByteArray());
    }
}
