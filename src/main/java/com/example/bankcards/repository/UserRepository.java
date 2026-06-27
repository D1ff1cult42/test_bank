package com.example.bankcards.repository;

import com.example.bankcards.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where :q is null
               or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
               or lower(u.username) like lower(concat('%', cast(:q as string), '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
