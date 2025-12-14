package com.build4all.admin.dto;

/**
 * Simple request/response DTO used to set (or update) the APK URL for an app assignment.
 *
 * Implemented as a Java record:
 * - Immutable
 * - Auto-generates constructor + accessor method apkUrl()
 *
 * Typical usage:
 * - Client sends: { "apkUrl": "https://..." }
 * - Backend updates AdminUserProject.apkUrl with the provided value.
 */
public record SetApkUrlDto(String apkUrl) {}
