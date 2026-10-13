package com.subin.submanager.global.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
