package io.github.astvvg.ats.user.repository;

import io.github.astvvg.ats.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // Emails are stored normalized; lookups normalize the argument the same way.
    @Query("select u from User u where u.email = lower(trim(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.email = lower(trim(:email))")
    boolean existsByEmail(@Param("email") String email);
}
