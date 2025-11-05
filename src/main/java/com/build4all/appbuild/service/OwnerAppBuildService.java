package com.build4all.appbuild.service;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OwnerAppBuildService {

    @Value("${uploads.root}") private String uploadsRoot;
    @Value("${uploads.public-base-url}") private String publicBaseUrl;
    @Value("${builder.script}") private String builderScript;
    @Value("${builder.project-dir}") private String projectDir;

    // optional: pass a specific flutter.bat
    @Value("${builder.flutter-bin:}") private String builderFlutterBin;

    public BuildResult buildOwnerApk(BuildRequest req) throws Exception {
        // --- use absolute, normalized uploads root ---
        Path root = Paths.get(uploadsRoot).toAbsolutePath().normalize();

        // owner bucket: <root>/apk/<owner>/<project>
        Path ownerBucketPath = root
                .resolve("apk")
                .resolve(String.valueOf(req.ownerId()))
                .resolve(String.valueOf(req.projectId()));
        Files.createDirectories(ownerBucketPath);

        String script = projectDir + File.separator + "build_owner_apk.ps1";

        List<String> cmd = new ArrayList<>();
        cmd.add(builderScript);
        cmd.add("-NoProfile");
        cmd.add("-ExecutionPolicy"); cmd.add("Bypass");
        cmd.add("-File");            cmd.add(script);

        cmd.add("-APP_NAME");              cmd.add(req.appName());
        cmd.add("-API_BASE_URL");          cmd.add(req.apiBaseUrl());
        cmd.add("-OWNER_PROJECT_LINK_ID"); cmd.add(String.valueOf(req.ownerProjectLinkId()));
        cmd.add("-PROJECT_ID");            cmd.add(String.valueOf(req.projectId()));
        cmd.add("-WS_PATH");               cmd.add(req.wsPath());
        cmd.add("-APP_ROLE");              cmd.add(req.appRole());
        cmd.add("-OWNER_ATTACH_MODE");     cmd.add(req.ownerAttachMode());
        cmd.add("-APP_LOGO_URL");          cmd.add(req.appLogoUrl() == null ? "" : req.appLogoUrl());
        cmd.add("-OWNER_BUCKET");          cmd.add(ownerBucketPath.toString()); // <-- pass absolute bucket
        cmd.add("-PROJECT_DIR");           cmd.add(projectDir);

        if (builderFlutterBin != null && !builderFlutterBin.isBlank()) {
            cmd.add("-FLUTTER_BIN"); cmd.add(builderFlutterBin);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder log = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
            String line; while ((line = br.readLine()) != null) log.append(line).append("\n");
        }
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("Builder failed ("+code+"):\n"+log);

        // --- Extract absolute apk path from log (last existing .apk line) ---
        String[] lines = log.toString().trim().split("\n");
        String apkAbsPathStr = null;
        for (int i = lines.length - 1; i >= 0; i--) {
            String candidate = lines[i].trim().replace("\"","").replace("\\\\","\\");
            if (candidate.toLowerCase().endsWith(".apk") && new File(candidate).exists()) {
                apkAbsPathStr = candidate;
                break;
            }
        }
        if (apkAbsPathStr == null) {
            throw new RuntimeException("Could not find built APK path in builder output:\n" + log);
        }

        // --- Normalize / compute relative URL under /uploads/ ---
        Path apkAbs = Paths.get(apkAbsPathStr).toAbsolutePath().normalize();
        String relUrl;

        if (apkAbs.startsWith(root)) {
            // path is inside uploadsRoot -> /uploads/<subpath>
            String sub = root.relativize(apkAbs).toString().replace("\\","/");
            relUrl = "/uploads/" + sub;
        } else {
            // Fallback: find /uploads/ segment anywhere in the absolute path
            String s = apkAbs.toString().replace("\\","/");
            int idx = s.toLowerCase().lastIndexOf("/uploads/");
            if (idx >= 0) {
                relUrl = s.substring(idx); // starts with /uploads/...
            } else {
                throw new IllegalStateException(
                        "Built APK is not under uploads root.\napk=" + apkAbs + "\nroot=" + root);
            }
        }

        // public URL only for convenience (not stored in DB)
        String publicUrl = publicBaseUrl.replaceAll("/+$","") + relUrl;

        return new BuildResult(
                apkAbs.toString(),
                relUrl,         // <== this starts with /uploads/  ✅
                publicUrl,
                LocalDateTime.now()
        );
    }

    /* records */

    public record BuildRequest(
            long ownerId,
            long ownerProjectLinkId,
            long projectId,
            String appName,
            String apiBaseUrl,
            String wsPath,
            String appRole,
            String ownerAttachMode,
            String appLogoUrl
    ) {}

    public record BuildResult(
            String apkPath,   // absolute file path on disk
            String relUrl,    // e.g. /uploads/apk/1/1/test_20251104_234142.apk  <-- SAVE THIS IN DB
            String publicUrl, // e.g. http://localhost:8080/uploads/apk/1/1/...
            LocalDateTime builtAt
    ) {}
}
