package com.example.bankcards.repository;

import com.example.bankcards.entity.Card;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    boolean existsByCardNumberHash(String cardNumberHash);

    Optional<Card> findByIdAndDeletedFalse(UUID id);

    Optional<Card> findByIdAndOwnerIdAndDeletedFalse(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c where c.id = :id and c.owner.id = :ownerId and c.deleted = false")
    Optional<Card> findByIdAndOwnerIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("""
            select c from Card c
            where c.owner.id = :ownerId and c.deleted = false
              and (:status is null or c.status = :status)
              and (:q is null or lower(c.maskedNumber) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Card> searchOwned(@Param("ownerId") UUID ownerId,
                           @Param("status") Card.Status status,
                           @Param("q") String q,
                           Pageable pageable);

    @Query("""
            select c from Card c
            where c.deleted = false
              and (:status is null or c.status = :status)
              and (:q is null or lower(c.maskedNumber) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Card> searchAll(@Param("status") Card.Status status,
                         @Param("q") String q,
                         Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Card c set c.status = :expired
            where c.deleted = false and c.status = :active and c.expiryDate < :today
            """)
    int markExpired(@Param("today") LocalDate today,
                    @Param("active") Card.Status active,
                    @Param("expired") Card.Status expired);
}
