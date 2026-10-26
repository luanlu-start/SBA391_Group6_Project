package com.fptpost;

import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.model.*;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CourierHttpTest extends IdentityFeatureHttpTest {
    Map<String,Object> input() { return new HashMap<>(Map.of("vehicleType","MOTORBIKE","areas",List.of("Ninh Kiều", "Bình Thủy"),
            "availableHours",List.of(Map.of("day","MONDAY","start","08:00","end","12:00")),"commitmentAccepted",true,"commitmentVersion","courier-v1")); }
    KycRequest approve() {
        return kycRequests.saveAndFlush(KycRequest.builder().publicId(UUID.randomUUID()).user(user).idNumberHash("fixture-"+user.getPublicId()).idNumberEnc("fixture")
                .idLastFour("9012").fullName("Người Dùng").dob(LocalDate.of(1990,1,1)).issuedDate(LocalDate.of(2022,1,1)).expiryDate(LocalDate.of(2036,1,1))
                .status(KycStatus.APPROVED).submittedAt(now.get()).reviewedAt(now.get()).consentVersion("kyc-v1").consentIp(ip).build());
    }
    MvcResult register(Map<String,Object> input,String bearer,int status) throws Exception { return write("POST","/api/v1/me/courier-registration",input,bearer,status); }
    @Test void approvedUserGetsProfileRoleNewJwtAndRotatedCookieAtomically() throws Exception {
        approve();var before=decoder.decode(token);var result=register(input(),token,201);var data=data(result);
        assertThat(data.path("profile").path("userId").asText()).isEqualTo(user.getPublicId().toString());
        assertThat(data.path("profile").path("tier").asText()).isEqualTo("NEW");
        assertThat(data.path("profile").path("trustScore").decimalValue()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(data.path("profile").path("completedOrders").asInt()).isZero();
        assertThat(data.path("authentication").path("user").path("roles").toString()).contains("SENDER","COURIER").doesNotContain("ADMIN");
        String next=data.path("authentication").path("accessToken").asText();assertThat(decoder.decode(next).getClaimAsStringList("roles")).containsExactlyInAnyOrder("SENDER","COURIER");
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(value->value.contains("HttpOnly")&&value.contains("fptpost_refresh="));
        send(get("/api/v1/me").header("Authorization","Bearer "+token),401);send(get("/api/v1/me").header("Authorization","Bearer "+next),200);
        var old=sessions.findByPublicId(UUID.fromString(before.getClaimAsString("sid"))).orElseThrow();assertThat(old.getRevokedAt()).isNotNull();assertThat(old.getReplacedBy()).isNotNull();
        assertThat(consents.existsByUserIdAndPolicyTypeAndPolicyVersion(user.getId(),"COURIER_COMMITMENT","courier-v1")).isTrue();
        var status=data(send(get("/api/v1/me/courier-registration").header("Authorization","Bearer "+next),200));assertThat(status.path("profile").path("areas")).hasSize(2);
        assertThat(body(register(input(),next,409)).path("code").asText()).isEqualTo("COURIER_ALREADY_REGISTERED");
    }
    @Test void notSubmittedPendingRejectedAndExpiredKycCannotRegister() throws Exception {
        assertThat(data(send(get("/api/v1/me/courier-registration").header("Authorization","Bearer "+token),200)).path("eligible").asBoolean()).isFalse();
        register(input(),token,403);var request=approve();
        for(var status:List.of(KycStatus.PENDING,KycStatus.REJECTED)){request.setStatus(status);request=kycRequests.saveAndFlush(request);register(input(),token,403);}
        request.setStatus(KycStatus.APPROVED);request.setExpiryDate(LocalDate.of(2026,10,19));kycRequests.saveAndFlush(request);
        assertThat(body(register(input(),token,403)).path("code").asText()).isEqualTo("COURIER_DOCUMENT_EXPIRED");assertThat(couriers.count()).isZero();
        assertThat(data(send(get("/api/v1/me").header("Authorization","Bearer "+token),200)).path("roles").toString()).doesNotContain("COURIER");
    }
    @Test void validatesConsentVersionVehicleAreasAndAvailabilityWithoutChangingRoles() throws Exception {
        approve();for(String key:List.of("commitmentAccepted","commitmentVersion","vehicleType","areas","availableHours")){
            var input=input();input.put(key,switch(key){case "commitmentAccepted"->false;case "commitmentVersion"->"old";case "vehicleType"->"FLYING";default->List.of();});
            register(input,token,key.equals("commitmentVersion")?409:400);
        }
        var input=input();input.put("availableHours",List.of(Map.of("day","MONDAY","start","12:00","end","08:00")));register(input,token,400);
        input.put("availableHours",List.of(Map.of("day","MONDAY","start","08:00","end","12:00"),Map.of("day","MONDAY","start","10:00","end","13:00")));register(input,token,400);
        input=input();input.put("areas",List.of(" Ninh Kiều ","ninh kiều"));register(input,token,400);assertThat(couriers.count()).isZero();
        assertThat(consents.existsByUserIdAndPolicyTypeAndPolicyVersion(user.getId(),"COURIER_COMMITMENT","courier-v1")).isFalse();
    }
    @Test void ignoresClientSuppliedUserTierTrustAndRoles() throws Exception {
        approve();var input=input();input.put("userId",UUID.randomUUID().toString());input.put("roles",List.of("ADMIN"));input.put("trustScore",100);input.put("tier","DIAMOND");
        var result=data(register(input,token,201));assertThat(result.path("profile").path("trustScore").asInt()).isEqualTo(50);
        assertThat(result.path("authentication").path("user").path("roles").toString()).doesNotContain("ADMIN");
    }
    @Test void otherDevicesCanRefreshWithNewRolesAndRememberMeSurvivesRoleChange() throws Exception {
        approve();var initialCsrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();var initial=data(initialCsrf);
        var remembered=send(post("/api/v1/auth/login").cookie(AuthenticationHttpTest.cookie(initialCsrf,"XSRF-TOKEN"))
                .header(initial.path("headerName").asText(),initial.path("token").asText()).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("identifier",user.getEmail(),"password","OldPassword123!","rememberMe",true))),200);
        token=access(remembered);var other=login(user.getEmail(),"OldPassword123!",200);Cookie refresh=AuthenticationHttpTest.cookie(other,"fptpost_refresh");
        var result=register(input(),token,201);String next=data(result).path("authentication").path("accessToken").asText();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(value->value.startsWith("fptpost_refresh=") && value.contains("Max-Age="));
        var csrf=mvc.perform(get("/api/v1/auth/csrf")).andReturn();var c=data(csrf);
        var renewed=send(post("/api/v1/auth/refresh").cookie(refresh,AuthenticationHttpTest.cookie(csrf,"XSRF-TOKEN"))
                .header(c.path("headerName").asText(),c.path("token").asText()),200);
        assertThat(data(renewed).path("user").path("roles").toString()).contains("COURIER");send(get("/api/v1/me").header("Authorization","Bearer "+next),200);
    }
    @Test void concurrentRegistrationCreatesOneProfileAndOneCommitment() throws Exception {
        approve();var pool=Executors.newFixedThreadPool(2);try{
            var tasks=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->mvc.perform(post("/api/v1/me/courier-registration")
                    .header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(input()))).andReturn().getResponse().getStatus()));
            var statuses=new ArrayList<Integer>();for(var task:tasks)statuses.add(task.get(30,TimeUnit.SECONDS));
            assertThat(statuses).contains(201);assertThat(statuses.stream().filter(value->value==201).count()).isEqualTo(1);
            assertThat(statuses).allMatch(value->value==201||value==409||value==401);
        }finally{pool.shutdownNow();}assertThat(couriers.count()).isEqualTo(1);
    }
    @Test void requiresAuthenticationAndOnlyShowsOwnProfile() throws Exception {
        send(get("/api/v1/me/courier-registration"),401);register(input(),null,401);approve();register(input(),token,201);
        var other=createUser("separate@example.com","+84888888881");var otherToken=access(login(other.getEmail(),"OldPassword123!",200));
        assertThat(data(send(get("/api/v1/me/courier-registration").header("Authorization","Bearer "+otherToken),200)).path("profile").isNull()).isTrue();
    }
}
