package com.subin.submanager.notification.domain;

public enum SendStatus {
    PENDING,
    SENT,
    FAILED,
    /** 푸시를 끈 회원 등 발송하지 않고 알림 센터에만 남긴 경우 */
    SKIPPED
}
