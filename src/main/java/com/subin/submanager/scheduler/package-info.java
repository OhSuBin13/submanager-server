/**
 * 예약 작업 (모두 한국 시간 기준, cron zone = Asia/Seoul).
 * <ul>
 *     <li>매시 정각: 결제일 리마인더 발송 (COD-10)</li>
 *     <li>매일 00:00: 지난 결제일을 다음 주기로 갱신 (Subscription#rollBillingDate)</li>
 *     <li>매일: 한국수출입은행 환율 갱신, 탈퇴 30일 경과 회원 삭제</li>
 * </ul>
 * 서버를 2대 이상으로 늘리면 ShedLock을 붙인다.
 */
package com.subin.submanager.scheduler;
