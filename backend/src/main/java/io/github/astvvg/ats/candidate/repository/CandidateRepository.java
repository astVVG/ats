package io.github.astvvg.ats.candidate.repository;

import io.github.astvvg.ats.candidate.domain.Candidate;
import io.github.astvvg.ats.common.LikePatterns;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

public interface CandidateRepository
        extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    // Emails are stored normalized; lookups normalize the argument the same way.
    @Query("select count(c) > 0 from Candidate c where c.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);

    @Query(
            "select count(c) > 0 from Candidate c"
                    + " where c.email = lower(trim(:email)) and c.id <> :id")
    boolean existsByEmailForOtherCandidate(@Param("email") String email, @Param("id") Long id);

    /**
     * Case-insensitive "contains" on full name or email where % and _ are literal. Null or blank
     * means no filter.
     */
    static Specification<Candidate> matching(String q) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(q)) {
                return cb.conjunction();
            }
            String pattern = LikePatterns.containsIgnoreCase(q.trim());
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern, LikePatterns.ESCAPE),
                    cb.like(cb.lower(root.get("email")), pattern, LikePatterns.ESCAPE));
        };
    }
}
