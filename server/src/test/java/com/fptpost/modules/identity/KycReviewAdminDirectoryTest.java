package com.fptpost.modules.identity;

import com.fptpost.modules.identity.enums.UserStatus;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.KycReviewAdminDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KycReviewAdminDirectoryTest {
    final EntityManager entities = mock(EntityManager.class);
    @SuppressWarnings("unchecked") final TypedQuery<User> participants = mock(TypedQuery.class);
    @SuppressWarnings("unchecked") final TypedQuery<UUID> administrators = mock(TypedQuery.class);
    final KycReviewAdminDirectory directory = new KycReviewAdminDirectory(entities);
    final User applicant = person(30), reviewer = person(20), administrator = person(10);

    @Test void decisionsRefreshApplicantAndReviewerInGlobalPrimaryKeyOrder() {
        when(entities.createQuery(contains("where u.publicId in :ids order by u.id"), eq(User.class))).thenReturn(participants);
        when(participants.setParameter(eq("ids"), anySet())).thenReturn(participants);
        when(participants.getResultList()).thenReturn(List.of(applicant, reviewer));

        directory.lockApplicantAndReviewer(applicant.getPublicId(), reviewer.getPublicId());

        verify(participants).setParameter("ids", Set.of(applicant.getPublicId(), reviewer.getPublicId()));
        var order = inOrder(entities);
        order.verify(entities).refresh(reviewer, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entities).refresh(applicant, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void escalationLocksModeratorAsWellAsEveryNotificationRecipientBeforeApplicant() {
        stubAdministrators();
        when(participants.getResultList()).thenReturn(List.of(applicant, reviewer, administrator));

        assertThat(directory.lockApplicantAndAdministrators(applicant.getPublicId(), reviewer.getPublicId()))
                .containsExactly(administrator.getPublicId());

        verify(participants).setParameter("ids", Set.of(applicant.getPublicId(), reviewer.getPublicId(), administrator.getPublicId()));
        var order = inOrder(entities);
        order.verify(entities).refresh(administrator, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entities).refresh(reviewer, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entities).refresh(applicant, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void scheduledAlertsUseTheSameLockOrderWithoutInventingAReviewer() {
        stubAdministrators();
        when(participants.getResultList()).thenReturn(List.of(applicant, administrator));

        directory.lockApplicantAndAdministrators(applicant.getPublicId());

        verify(participants).setParameter("ids", Set.of(applicant.getPublicId(), administrator.getPublicId()));
        var order = inOrder(entities);
        order.verify(entities).refresh(administrator, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entities).refresh(applicant, LockModeType.PESSIMISTIC_WRITE);
    }

    private void stubAdministrators() {
        when(entities.createQuery(startsWith("select u.publicId from User u"), eq(UUID.class))).thenReturn(administrators);
        when(administrators.setParameter("active", UserStatus.ACTIVE)).thenReturn(administrators);
        when(administrators.setMaxResults(101)).thenReturn(administrators);
        when(administrators.getResultList()).thenReturn(List.of(administrator.getPublicId()));
        when(entities.createQuery(contains("where u.publicId in :ids order by u.id"), eq(User.class))).thenReturn(participants);
        when(participants.setParameter(eq("ids"), anySet())).thenReturn(participants);
    }

    private User person(long id) { return User.builder().id(id).publicId(UUID.randomUUID()).build(); }
}
