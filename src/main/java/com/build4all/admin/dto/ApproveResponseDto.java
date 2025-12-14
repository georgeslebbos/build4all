package com.build4all.admin.dto;

/**
 * Small response DTO returned after an "approve" operation.
 *
 * Implemented as a Java record:
 * - Immutable (fields cannot be changed after creation)
 * - Automatically generates constructor, getters (adminId(), projectId(), slug()), equals/hashCode/toString
 *
 * Fields:
 * - adminId: the admin/owner identifier
 * - projectId: the project identifier
 * - slug: the app/tenant slug that was approved
 */
public record ApproveResponseDto(Long adminId, Long projectId, String slug) {}
