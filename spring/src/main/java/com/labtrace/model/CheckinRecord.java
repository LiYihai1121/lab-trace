package com.labtrace.model;

import com.labtrace.model.CheckinRecord.Status;
import jakarta.persistence.*;

@Entity
@Table(name = "checkin_records")
public class CheckinRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String checkinTime;

  private String checkoutTime;

  private Integer durationMinutes;

  @Column(nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private Status status;

  @Column(name = "code_id")
  private Long codeId;

  public enum Status {
    checked_in,
    completed
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getCheckinTime() {
    return checkinTime;
  }

  public void setCheckinTime(String checkinTime) {
    this.checkinTime = checkinTime;
  }

  public String getCheckoutTime() {
    return checkoutTime;
  }

  public void setCheckoutTime(String checkoutTime) {
    this.checkoutTime = checkoutTime;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Long getCodeId() {
    return codeId;
  }

  public void setCodeId(Long codeId) {
    this.codeId = codeId;
  }
}
