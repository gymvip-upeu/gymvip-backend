package com.gymvip.repository;

import com.gymvip.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByMemberCode(String memberCode);

    Optional<Client> findByMemberCode(String memberCode);

    @Query("SELECT DISTINCT c FROM Client c LEFT JOIN c.documents d WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.memberCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(d.documentNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Client> searchClients(@Param("query") String query, Pageable pageable);
}
