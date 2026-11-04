package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.repository.KycReviewSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor
public class KycReviewSlaScanner {
    private final KycReviewSearchRepository search;

    @Transactional(readOnly = true)
    public List<KycReviewSearchRepository.Candidate> candidates(Instant cutoff, long hours) {
        return search.overdueCandidates(cutoff, hours, 50);
    }
}
