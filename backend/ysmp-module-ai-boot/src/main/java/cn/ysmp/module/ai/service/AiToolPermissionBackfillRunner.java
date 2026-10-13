package cn.ysmp.module.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动后回填已注册 OPENAPI Tool 的 required_permissions（MVC 就绪后）。
 */
@Slf4j
@RequiredArgsConstructor
public class AiToolPermissionBackfillRunner implements ApplicationRunner {

    private final AiToolPermissionSyncService syncService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = syncService.resyncAllOpenApiTools();
            log.info("[ai] startup permission backfill done: {} tools", n);
        } catch (Exception e) {
            log.warn("[ai] startup permission backfill skipped: {}", e.getMessage());
        }
    }
}
