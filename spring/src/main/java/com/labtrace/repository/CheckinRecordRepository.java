package com.labtrace.repository;

import com.labtrace.model.CheckinRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

  Page<CheckinRecord> findByUserId(Long userId, Pageable pageable);

  Optional<CheckinRecord> findTopByUserIdAndStatusOrderByIdDesc(Long userId, CheckinRecord.Status status);

  @Query("""
      SELECT r FROM CheckinRecord r
      JOIN r.user u
      WHERE
        (:keyword IS NULL OR :keyword = '' OR
         LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
         LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (r.checkinTime >= :start OR :start IS NULL)
        AND (r.checkinTime <= :end OR :end IS NULL)
      """)
  Page<CheckinRecord> searchAll(@Param("keyword") String keyword,
                                @Param("start") String start,
                                @Param("end") String end,
                                Pageable pageable);

  @Query("""
      SELECT COUNT(r) FROM CheckinRecord r
      WHERE (:userId IS NULL OR r.user.id = :userId) AND r.checkinTime >= :start
      """)
  long countByUserIdAndCheckinTimeAfter(@Param("userId") Long userId, @Param("start") String start);

  @Query("""
      SELECT COALESCE(SUM(r.durationMinutes), 0) FROM CheckinRecord r
      WHERE (:userId IS NULL OR r.user.id = :userId) AND r.checkinTime >= :start
      """)
  Long sumDurationMinutesByUserIdAndCheckinTimeAfter(@Param("userId") Long userId, @Param("start") String start);

  void deleteByUserId(Long userId);
}
