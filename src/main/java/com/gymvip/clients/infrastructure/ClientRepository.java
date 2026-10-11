package com.gymvip.clients.infrastructure;

import com.gymvip.clients.domain.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    @Query("SELECT c FROM Client c JOIN c.documents d " +
           "WHERE (:query IS NULL OR :query = '' " +
           "OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR d.documentNumber LIKE CONCAT('%', :query, '%'))")
    Page<Client> searchClients(@Param("query") String query, Pageable pageable);

    @Query("SELECT c FROM Client c JOIN c.documents d WHERE d.countryCode = :countryCode AND d.documentType = :docType AND d.documentNumber = :docNum")
    Optional<Client> findByDocument(@Param("countryCode") String countryCode,
                                    @Param("docType") String docType,
                                    @Param("docNum") String docNum);
}
