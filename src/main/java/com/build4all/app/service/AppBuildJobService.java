package com.build4all.app.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.app.domain.AppBuildJob;
import com.build4all.app.domain.BuildJobStatus;
import com.build4all.app.domain.BuildPlatform;
import com.build4all.app.repository.AppBuildJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AppBuildJobService {

    private static final Logger log = LoggerFactory.getLogger(AppBuildJobService.class);

    private final AppBuildJobRepository repo;

    public AppBuildJobService(AppBuildJobRepository repo) {
        this.repo = repo;
    }

    // -------- Dispatch record (called from AppRequestService) --------

    @Transactional
    public void recordAndroidDispatch(AdminUserProject link, String ciBuildId) {
        AppBuildJob job = new AppBuildJob();
        job.setApp(link);
        job.setPlatform(BuildPlatform.ANDROID);
        job.setCiBuildId(ciBuildId);
        job.setStatus(BuildJobStatus.QUEUED);

        job.setAndroidVersionCode(link.getAndroidVersionCode());
        job.setAndroidVersionName(link.getAndroidVersionName());
        job.setAndroidPackageName(link.getAndroidPackageName());

        repo.save(job);
    }

    @Transactional
    public void recordIosDispatch(AdminUserProject link, String ciBuildId) {
        AppBuildJob job = new AppBuildJob();
        job.setApp(link);
        job.setPlatform(BuildPlatform.IOS);
        job.setCiBuildId(ciBuildId);
        job.setStatus(BuildJobStatus.QUEUED);

        job.setIosBuildNumber(link.getIosBuildNumber());
        job.setIosVersionName(link.getIosVersionName());
        job.setIosBundleId(link.getIosBundleId());

        repo.save(job);
    }

    // -------- Status updates from CI (by buildId) --------

    @Transactional
    public void markRunningByBuildId(String buildId) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            job.setStatus(BuildJobStatus.RUNNING);
            repo.save(job);
        });
    }

    @Transactional
    public void markFailedByBuildId(String buildId, String error) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            job.setStatus(BuildJobStatus.FAILED);
            job.setError((error == null || error.isBlank()) ? "CI build failed" : error);
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            job.setFinishedAt(LocalDateTime.now());
            repo.save(job);
        });
    }

    @Transactional
    public void markSucceededByBuildId(String buildId) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            job.setFinishedAt(LocalDateTime.now());
            job.setStatus(BuildJobStatus.SUCCESS);
            repo.save(job);
        });
    }

    // -------- Fallback updates (latest job by linkId + platform) --------

    @Transactional
    public void markLatestRunning(Long linkId, BuildPlatform platform) {
        AppBuildJob job = findLatest(linkId, platform);
        if (job == null) return;

        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        job.setStatus(BuildJobStatus.RUNNING);
        repo.save(job);
    }

    @Transactional
    public void markLatestFailed(Long linkId, BuildPlatform platform, String error) {
        AppBuildJob job = findLatest(linkId, platform);
        if (job == null) return;

        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        job.setStatus(BuildJobStatus.FAILED);
        job.setError((error == null || error.isBlank()) ? "CI build failed" : error);
        repo.save(job);
    }

    @Transactional
    public void markLatestSucceeded(Long linkId, BuildPlatform platform) {
        AppBuildJob job = findLatest(linkId, platform);
        if (job == null) return;

        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        job.setStatus(BuildJobStatus.SUCCESS);
        repo.save(job);
    }

    private AppBuildJob findLatest(Long linkId, BuildPlatform platform) {
        if (linkId == null || platform == null) return null;
        return repo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, platform).orElse(null);
    }

    // -------- Artifact updates (Android: APK then AAB, iOS: IPA) --------

    @Transactional
    public void recordAndroidApkByBuildId(String buildId, String apkUrl) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            job.setApkUrl(apkUrl);
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            if (job.getStatus() == BuildJobStatus.QUEUED) job.setStatus(BuildJobStatus.RUNNING);
            repo.save(job);
        });
    }

    @Transactional
    public void recordLatestAndroidApk(Long linkId, String apkUrl) {
        if (linkId == null) return;

        AppBuildJob job = repo.findTop1ByApp_IdAndPlatformAndStatusOrderByCreatedAtDesc(
                        linkId, BuildPlatform.ANDROID, BuildJobStatus.RUNNING
                )
                .or(() -> repo.findTop1ByApp_IdAndPlatformAndStatusOrderByCreatedAtDesc(
                        linkId, BuildPlatform.ANDROID, BuildJobStatus.QUEUED
                ))
                .or(() -> repo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, BuildPlatform.ANDROID))
                .orElse(null);

        if (job == null) return;

        job.setApkUrl(apkUrl);
        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        if (job.getStatus() == BuildJobStatus.QUEUED) job.setStatus(BuildJobStatus.RUNNING);
        repo.save(job);
    }

    @Transactional
    public void markAndroidAabSucceededByBuildId(String buildId, String aabUrl) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            job.setBundleUrl(aabUrl);
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            job.setFinishedAt(LocalDateTime.now());
            job.setStatus(BuildJobStatus.SUCCESS);
            repo.save(job);
        });
    }

    @Transactional
    public void markLatestAndroidAabSucceeded(Long linkId, String aabUrl) {
        if (linkId == null) return;

        AppBuildJob job = repo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, BuildPlatform.ANDROID).orElse(null);
        if (job == null) return;

        job.setBundleUrl(aabUrl);
        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        job.setStatus(BuildJobStatus.SUCCESS);
        repo.save(job);
    }

    @Transactional
    public void markIosIpaSucceededByBuildId(String buildId, String ipaUrl) {
        if (buildId == null || buildId.isBlank()) return;

        repo.findByCiBuildId(buildId.trim()).ifPresent(job -> {
            job.setIpaUrl(ipaUrl);
            if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
            job.setFinishedAt(LocalDateTime.now());
            job.setStatus(BuildJobStatus.SUCCESS);
            repo.save(job);
        });
    }

    @Transactional
    public void markLatestIosIpaSucceeded(Long linkId, String ipaUrl) {
        if (linkId == null) return;

        AppBuildJob job = repo.findTop1ByApp_IdAndPlatformOrderByCreatedAtDesc(linkId, BuildPlatform.IOS).orElse(null);
        if (job == null) return;

        job.setIpaUrl(ipaUrl);
        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        job.setStatus(BuildJobStatus.SUCCESS);
        repo.save(job);
    }
}
