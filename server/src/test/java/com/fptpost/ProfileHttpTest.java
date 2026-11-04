package com.fptpost;

import com.fptpost.modules.identity.enums.*;
import jakarta.mail.internet.MimeMessage;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ProfileHttpTest extends IdentityFeatureHttpTest {
    Map<String,Object> address(String label, boolean isDefault) { return Map.of("label",label,"addressText","600 Nguyễn Văn Cừ, Cần Thơ","lat",10.0299,"lng",105.7705,"isDefault",isDefault); }
    com.fasterxml.jackson.databind.JsonNode contact(String type, String value) throws Exception {
        return data(write("PATCH","/api/v1/me/contact",Map.of("type",type,"value",value,"currentPassword","OldPassword123!"),token,200));
    }
    @Test void profileUpdatesOnlyEditableFieldsAndChecksAge() throws Exception {
        var updated=data(write("PUT","/api/v1/me/profile",Map.of("fullName","  Nguyễn   Văn An ","dob","1995-10-20","bio","Giới thiệu","email","hacker@example.com","roles",List.of("ADMIN")),token,200));
        assertThat(updated.path("fullName").asText()).isEqualTo("Nguyễn Văn An");
        assertThat(updated.path("email").asText()).isEqualTo(user.getEmail());
        assertThat(updated.path("roles").toString()).doesNotContain("ADMIN");
        write("PUT","/api/v1/me/profile",Map.of("fullName","Tên hợp lệ","dob","2010-10-20"),token,400);
        write("PUT","/api/v1/me/profile",Map.of("fullName"," "),token,400);
        write("PUT","/api/v1/me/profile",Map.of("fullName","Người khác"),null,401);
    }
    @Test void avatarIsValidatedFromContentAndOnlyVisibleToItsOwner() throws Exception {
        for(String format:List.of("png","jpg")){
            send(multipart("/api/v1/me/avatar").file(image("file",format)).header("Authorization","Bearer "+token),200);
            send(get("/api/v1/me/avatar").header("Authorization","Bearer "+token),200);
        }
        var forged=new MockMultipartFile("file","evil.jpg","image/jpeg","<html>payload</html>".getBytes());
        send(multipart("/api/v1/me/avatar").file(forged).header("Authorization","Bearer "+token),400);
        send(multipart("/api/v1/me/avatar").file(new MockMultipartFile("file","large.png","image/png",new byte[5*1024*1024+1]))
                .header("Authorization","Bearer "+token),413);
        var other=createUser("avatar@example.com","+84888888888");String otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        send(get("/api/v1/me/avatar").header("Authorization","Bearer "+otherToken),404);
        send(get("/api/v1/me/avatar"),401);
        assertThat(data(send(get("/api/v1/me").header("Authorization","Bearer "+token),200)).path("avatarAvailable").asBoolean()).isTrue();
    }
    @Test void addressCrudKeepsOneDefaultAndPreventsCrossAccountAccess() throws Exception {
        var first=data(write("POST","/api/v1/me/addresses",address("Nhà",false),token,201));
        assertThat(first.path("isDefault").asBoolean()).isTrue();
        var second=data(write("POST","/api/v1/me/addresses",address("Trường",true),token,201));
        assertThat(data(send(get("/api/v1/me/addresses").header("Authorization","Bearer "+token),200)).findValues("isDefault").stream().filter(v->v.asBoolean()).count()).isEqualTo(1);
        var other=createUser("address@example.com","+84888888887");String otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        write("PUT","/api/v1/me/addresses/"+first.path("id").asText(),address("Không được sửa",true),otherToken,404);
        send(delete("/api/v1/me/addresses/"+first.path("id").asText()).header("Authorization","Bearer "+otherToken),404);
        send(delete("/api/v1/me/addresses/"+second.path("id").asText()).header("Authorization","Bearer "+token),200);
        assertThat(data(send(get("/api/v1/me/addresses").header("Authorization","Bearer "+token),200)).get(0).path("isDefault").asBoolean()).isTrue();
        var bad=new HashMap<>(address("Sai tọa độ",false));bad.put("lat",91);write("POST","/api/v1/me/addresses",bad,token,400);
    }
    @Test void parallelAddressCreationDoesNotCreateTwoDefaults() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);var jobs=new ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++)jobs.add(executor.submit(()->{start.await();return write("POST","/api/v1/me/addresses",address("Nhà",true),token,201).getResponse().getStatus();}));
            start.countDown();for(var job:jobs)assertThat(job.get(10,TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(addresses.findByUserIdOrderByIdAsc(user.getId()).stream().filter(a->a.isDefaultAddress()).count()).isEqualTo(1);
    }
    @Test void privacyRequestsAreIdempotentTicketsAndDoNotDeleteTheAccount() throws Exception {
        var first=data(write("POST","/api/v1/me/delete-request",Map.of("reason","Không sử dụng nữa"),token,200));
        var repeat=data(write("POST","/api/v1/me/delete-request",Map.of(),token,200));
        assertThat(first.path("id")).isEqualTo(repeat.path("id"));
        assertThat(Instant.parse(first.path("dueAt").asText())).isEqualTo(now.get().plus(Duration.ofDays(30)));
        write("POST","/api/v1/me/data-export",Map.of(),token,200);
        assertThat(data(send(get("/api/v1/me/data-requests").header("Authorization","Bearer "+token),200))).hasSize(2);
        assertThat(users.findById(user.getId()).orElseThrow().isDeleted()).isFalse();
    }
    @Test void emailChangeWaitsForOtpThenGrantsBadgeAndRevokesOldSessions() throws Exception {
        String next="newmember@fpt.edu.vn";var pending=contact("EMAIL",next);String code=otp();
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
        assertThat(delivered.getLast().getAllRecipients()[0].toString()).isEqualTo(next);
        write("POST","/api/v1/me/contact/verify",Map.of("challengeId",pending.path("challengeId").asText(),"otp",code),token,200);
        assertThat(users.findById(user.getId()).orElseThrow().isFptBadge()).isTrue();
        send(get("/api/v1/me").header("Authorization","Bearer "+token),401);
        login(user.getEmail(),"OldPassword123!",401); login(next,"OldPassword123!",200);
    }
    @Test void phoneChangeUsesCurrentEmailForAuthorizationWithoutMarkingThePhoneVerified() throws Exception {
        var pending=contact("PHONE","0987654321");
        assertThat(delivered.getLast().getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        write("POST","/api/v1/me/contact/verify",Map.of("challengeId",pending.path("challengeId").asText(),"otp",otp()),token,200);
        var saved=users.findById(user.getId()).orElseThrow();assertThat(saved.getPhone()).isEqualTo("+84987654321");assertThat(saved.getPhoneVerifiedAt()).isNull();
    }
    @Test void badOtpAttemptsPersistAndResendHasCooldownAndReplacesTheChallenge() throws Exception {
        var first=contact("EMAIL","updated@example.com");String actual=otp();String wrong=actual.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++)write("POST","/api/v1/me/contact/verify",Map.of("challengeId",first.path("challengeId").asText(),"otp",wrong),token,400);
        assertThat(otps.findFirstByUserIdAndTypeOrderByCreatedAtDescIdDesc(user.getId(),OtpType.EMAIL_CHANGE).orElseThrow().getTries()).isEqualTo(5);
        write("POST","/api/v1/me/contact/"+first.path("challengeId").asText()+"/resend",Map.of(),token,429);
        now.set(now.get().plusSeconds(60));var second=data(write("POST","/api/v1/me/contact/"+first.path("challengeId").asText()+"/resend",Map.of(),token,200));
        write("POST","/api/v1/me/contact/verify",Map.of("challengeId",first.path("challengeId").asText(),"otp",actual),token,409);
        now.set(now.get().plusSeconds(300));write("POST","/api/v1/me/contact/verify",Map.of("challengeId",second.path("challengeId").asText(),"otp",otp()),token,410);
    }
    @Test void contactChangesRejectWrongPasswordDuplicatesAndOtherOwners() throws Exception {
        write("PATCH","/api/v1/me/contact",Map.of("type","EMAIL","value","new@example.com","currentPassword","WrongPassword123!"),token,400);
        var other=createUser("used@example.com","+84888888886");
        write("PATCH","/api/v1/me/contact",Map.of("type","EMAIL","value",other.getEmail(),"currentPassword","OldPassword123!"),token,409);
        var challenge=contact("EMAIL","university@evilfpt.edu.vn");String code=otp();
        var otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        write("POST","/api/v1/me/contact/verify",Map.of("challengeId",challenge.path("challengeId").asText(),"otp",code),otherToken,404);
        write("POST","/api/v1/auth/otp/verify",Map.of("challengeId",challenge.path("challengeId").asText(),"otp",code),null,404);
        write("POST","/api/v1/me/contact/verify",Map.of("challengeId",challenge.path("challengeId").asText(),"otp",code),token,200);
        assertThat(users.findById(user.getId()).orElseThrow().isFptBadge()).isFalse();
    }
    @Test void contactDeliveryFailureRollsBackThePendingChallenge() throws Exception {
        doThrow(new MailSendException("Unavailable")).when(mailSender).send(any(MimeMessage.class));
        write("PATCH","/api/v1/me/contact",Map.of("type","EMAIL","value","new@example.com","currentPassword","OldPassword123!"),token,503);
        assertThat(contacts.count()).isZero();assertThat(otps.count()).isZero();
        assertThat(users.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
    }
}
