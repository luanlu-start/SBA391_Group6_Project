package com.fptpost;

import com.fptpost.modules.identity.enums.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class KycHttpTest extends IdentityFeatureHttpTest {
    @Test void acceptsWebpByActualImageContent() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/fixtures/kyc.webp")) { bytes = input.readAllBytes(); }
        var response = send(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(valid())))
                .file(new MockMultipartFile("front","photo.webp","image/webp",bytes)).file(image("back","png")).file(image("portrait","jpg"))
                .header("Authorization","Bearer "+token),201);
        assertThat(data(response).path("request").path("files").toString()).contains("image/webp");
    }
    Map<String,Object> valid() { return new HashMap<>(Map.of("idNumber","123456789012","fullName","Người Dùng","dob","1990-01-01",
            "issuedDate","2022-01-01","expiryDate","2036-01-01","consentAccepted",true,"policyVersion","kyc-v1")); }
    MvcResult submit(Map<String,Object> input, String bearer, int expected) throws Exception {
        return send(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(input)))
                .file(image("front","png")).file(image("back","jpg")).file(image("portrait","png"))
                .header("Authorization","Bearer "+bearer),expected);
    }
    void rejectLatest() { var request=kycRequests.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId()).orElseThrow();request.setStatus(KycStatus.REJECTED);request.setRejectReason("Ảnh chưa rõ");request.setReviewedAt(now.get());kycRequests.saveAndFlush(request); }
    @Test void successfulSubmissionEncryptsFilesAndIdAndRecordsConsentWithoutGrantingCourier() throws Exception {
        var response=body(submit(valid(),token,201));
        assertThat(response.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(response.toString()).doesNotContain("123456789012","storageKey","idNumberEnc","idNumberHash");
        assertThat(response.path("data").path("request").path("idNumberMasked").asText()).isEqualTo("********9012");
        assertThat(response.path("data").path("request").path("files")).hasSize(3);
        var saved=kycRequests.findAll().getFirst();assertThat(saved.getIdNumberEnc()).doesNotContain("123456789012");
        assertThat(saved.getIdNumberHash()).hasSize(64).doesNotContain("123456789012");
        for(var file:kycFiles.findAll())assertThat(Files.readAllBytes(files.resolve(file.getStorageKey()+".bin"))[0]).isEqualTo((byte)1);
        assertThat(consents.existsByUserIdAndPolicyTypeAndPolicyVersion(user.getId(),"KYC","kyc-v1")).isTrue();
        assertThat(auditLogs.findAll()).anyMatch(log->log.getAction()==IdentityAuditAction.KYC_SUBMITTED);
        assertThat(data(send(get("/api/v1/me").header("Authorization","Bearer "+token),200)).path("roles").toString()).doesNotContain("COURIER");
    }
    @Test void missingConsentMissingImagesBadDatesAndProfileMismatchAreRejected() throws Exception {
        for(String key:List.of("consentAccepted","fullName","dob","expiryDate","policyVersion")){
            var input=valid();input.put(key,switch(key){case "consentAccepted"->false;case "fullName"->"Người khác";case "dob"->"2015-01-01";case "expiryDate"->"2020-01-01";default->"old-version";});
            submit(input,token,key.equals("policyVersion")?409:400);
        }
        send(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(valid())))
                .file(image("front","png")).file(image("back","png")).header("Authorization","Bearer "+token),400);
        send(get("/api/v1/me/kyc"),401);assertThat(kycRequests.count()).isZero();
    }
    @Test void rejectsForgedImageContentAndOversizedPicturesBeforeWritingFiles() throws Exception {
        send(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(valid())))
                .file(new MockMultipartFile("front","file.png","image/png","<script>evil()</script>".getBytes(StandardCharsets.UTF_8)))
                .file(image("back","png")).file(image("portrait","png")).header("Authorization","Bearer "+token),400);
        send(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(valid())))
                .file(new MockMultipartFile("front","huge.jpg","image/jpeg",new byte[5*1024*1024+1]))
                .file(image("back","png")).file(image("portrait","png")).header("Authorization","Bearer "+token),413);
        assertThat(kycRequests.count()).isZero();
    }
    @Test void duplicateIdCannotReserveAnotherAccountsDocumentAndCreatesAReviewFlag() throws Exception {
        submit(valid(),token,201);
        var other=createUser("duplicate@example.com","+84888888880");var otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        assertThat(body(submit(valid(),otherToken,422)).path("code").asText()).isEqualTo("KYC_ID_DUPLICATED");
        assertThat(kycRequests.count()).isEqualTo(1);assertThat(kycFiles.count()).isEqualTo(3);assertThat(kycClaims.count()).isEqualTo(1);
        assertThat(auditLogs.findAll()).anyMatch(log->log.getAction()==IdentityAuditAction.KYC_DUPLICATE_ID && log.getActorPublicId().equals(other.getPublicId()));
    }
    @Test void concurrentSubmissionsByDifferentAccountsCannotOverwriteAnIdentityClaim() throws Exception {
        var other=createUser("parallel@example.com","+84888888881");String second=access(login(other.getEmail(),"OldPassword123!",200));
        var input=valid();var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var jobs=new ArrayList<Future<Integer>>();for(String bearer:List.of(token,second))jobs.add(executor.submit(()->{
                start.await();return mvc.perform(multipart("/api/v1/me/kyc").file(new MockMultipartFile("request","","application/json",json.writeValueAsBytes(input)))
                        .file(image("front","png")).file(image("back","png")).file(image("portrait","png"))
                        .header("Authorization","Bearer "+bearer).with(r->{r.setRemoteAddr(ip);return r;})).andReturn().getResponse().getStatus();
            }));start.countDown();assertThat(List.of(jobs.get(0).get(15,TimeUnit.SECONDS),jobs.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,422);
        }
        assertThat(kycRequests.count()).isEqualTo(1);assertThat(kycClaims.count()).isEqualTo(1);assertThat(kycFiles.count()).isEqualTo(3);
    }
    @Test void pendingSubmissionBlocksAnotherRequestAndLocksIdentityFields() throws Exception {
        submit(valid(),token,201);submit(valid(),token,409);
        write("PUT","/api/v1/me/profile",Map.of("fullName","Tên khác","dob","1990-01-01"),token,409);
        write("PUT","/api/v1/me/profile",Map.of("fullName","Người Dùng","dob","1990-01-01","bio","Giới thiệu mới"),token,200);
    }
    @Test void signedImagesRequireOwnershipSessionAndUnexpiredSignatureAndAreAudited() throws Exception {
        var pending=data(submit(valid(),token,201));String id=pending.path("request").path("files").get(0).path("id").asText();
        String url=data(write("POST","/api/v1/me/kyc/files/"+id+"/link",Map.of(),token,200)).path("url").asText();
        var image=send(get("/api/v1"+url).header("Authorization","Bearer "+token),200);
        assertThat(image.getResponse().getContentAsByteArray()).isNotEmpty();assertThat(image.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(auditLogs.findAll()).anyMatch(log->log.getAction()==IdentityAuditAction.KYC_FILE_VIEWED);
        String anotherSession=access(login(user.getEmail(),"OldPassword123!",200));send(get("/api/v1"+url).header("Authorization","Bearer "+anotherSession),403);
        var other=createUser("viewer@example.com","+84888888882");String otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        write("POST","/api/v1/me/kyc/files/"+id+"/link",Map.of(),otherToken,404);
        send(get("/api/v1"+url).header("Authorization","Bearer "+otherToken),404);send(get("/api/v1"+url),401);
        send(get("/api/v1"+url.replace("signature=","signature=0")).header("Authorization","Bearer "+token),403);
        now.set(now.get().plusSeconds(300));send(get("/api/v1"+url).header("Authorization","Bearer "+token),403);
    }
    @Test void resubmissionLimitCountsSuccessfulRequestsWithinSevenDays() throws Exception {
        for(int i=0;i<3;i++){submit(valid(),token,201);rejectLatest();now.set(now.get().plusSeconds(1));}
        var response=submit(valid(),token,429);assertThat(response.getResponse().getHeader("Retry-After")).isNotBlank();
        now.set(now.get().plus(Duration.ofDays(7)));token=access(login(user.getEmail(),"OldPassword123!",200));submit(valid(),token,201);
        assertThat(kycRequests.count()).isEqualTo(4);
    }
}
