package com.build4all.appbuild.service;

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
        // ensure owner bucket exists
        String ownerBucket = uploadsRoot + File.separator + "apk"
                + File.separator + req.ownerId()
                + File.separator + req.projectId();
        Files.createDirectories(new File(ownerBucket).toPath());

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
        cmd.add("-OWNER_BUCKET");          cmd.add(ownerBucket);
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

        // last line printed by the script = absolute file path
        String[] lines = log.toString().trim().split("\n");
        String apkAbsPath = lines[lines.length - 1].trim();

        // compute relative path under /uploads
        String rel = apkAbsPath.replace("\\","/")
                .replaceFirst("^" + uploadsRoot.replace("\\","/"), "")
                .replaceAll("^/+", "");  // remove leading slashes
        String relUrl = "/" + rel;       // ensure it starts with "/"

        // keep public URL for convenience (response)
        String publicUrl = publicBaseUrl.replaceAll("/+$","") + "/" + rel;

        return new BuildResult(apkAbsPath, relUrl, publicUrl, LocalDateTime.now());
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
