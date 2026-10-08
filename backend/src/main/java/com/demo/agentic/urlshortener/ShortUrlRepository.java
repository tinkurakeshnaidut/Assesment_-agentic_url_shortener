package com.demo.agentic.urlshortener;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    @Transactional
    @Modifying
    @Query("update ShortUrl s set s.clickCount = s.clickCount + 1 "
            + "where s.code = :code and (s.maxUses is null or s.clickCount < s.maxUses)")
    int tryRecordClick(@Param("code") String code);
}
