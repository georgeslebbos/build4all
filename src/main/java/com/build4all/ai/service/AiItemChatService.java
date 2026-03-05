package com.build4all.ai.service;

import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.dto.AiItemContextDTO;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.features.activity.repository.ActivitiesRepository;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.*;

@Service
public class AiItemChatService {

    private static final Logger log = LoggerFactory.getLogger(AiItemChatService.class);

    // ✅ Keep this < Dio receiveTimeout (60s) so frontend doesn't hang forever
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(45);

    // ✅ Prevent huge prompts (tune if needed)
    private static final int MAX_CONTEXT_CHARS = 7000;
    private static final int MAX_USER_MSG_CHARS = 800;

    private final ItemRepository itemRepo;
    private final ProductRepository productRepo;
    private final ActivitiesRepository activitiesRepo;
    private final AiProviderService ai;

    
    
    public AiItemChatService(
            ItemRepository itemRepo,
            ProductRepository productRepo,
            ActivitiesRepository activitiesRepo,
            AiProviderService ai
    ) {
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.activitiesRepo = activitiesRepo;
        this.ai = ai;
    }
    
    

    public String handle(AiItemChatRequest req) {

        long t0 = System.currentTimeMillis();

        // 1) Tenant (AUP id)
        Long aupId = TenantContext.getOwnerProjectId();
        if (aupId == null) {
            // If this happens, your controller/filter is not setting TenantContext
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant missing (ownerProjectId not set)");
        }

        // 2) Validate input
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        Long itemId = req.getItemId();
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required");
        }

        String userMsg = req.getMessage();
        if (userMsg == null || userMsg.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        userMsg = limit(userMsg.trim(), MAX_USER_MSG_CHARS);

        // 3) Load base item (tenant-safe)
        Item item = itemRepo.findByTenantWithJoins(aupId, itemId).orElse(null);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found for this tenant");
        }

        long tItemLoaded = System.currentTimeMillis();

        // 4) Build context DTO
        AiItemContextDTO ctx = AiItemContextDTO.fromItem(item);

        // 5) Fill subtype safely (ignore if not found)
        // NOTE: keep your repo arg order as you defined it
        productRepo.findByIdAndTenant(itemId, aupId).ifPresent(ctx::applyProduct);
        activitiesRepo.findByIdAndTenant(itemId, aupId).ifPresent(ctx::applyActivity);

        // 6) Build context string (truncate)
        String itemContext = limit(buildAiContext(ctx), MAX_CONTEXT_CHARS);

        // 7) Build prompt
        String prompt = buildPrompt(userMsg, itemContext);

        long tPromptReady = System.currentTimeMillis();

        // 8) Ask AI provider with timeout + clean errors
        try {
            String answer = askWithTimeout(prompt, AI_TIMEOUT);

            long tDone = System.currentTimeMillis();
            log.info(
                    "AI item-chat OK (aupId={}, itemId={}) timings: itemLoad={}ms, prompt={}ms, aiCall={}ms, total={}ms",
                    aupId,
                    itemId,
                    (tItemLoaded - t0),
                    (tPromptReady - tItemLoaded),
                    (tDone - tPromptReady),
                    (tDone - t0)
            );

            if (answer == null) answer = "";
            answer = answer.trim();
            return answer.isEmpty() ? "I couldn't generate a response. Try rephrasing 😅" : answer;

        } catch (TimeoutException te) {
            long tNow = System.currentTimeMillis();
            log.warn(
                    "AI item-chat TIMEOUT (aupId={}, itemId={}) after {}ms total={}ms",
                    aupId, itemId, AI_TIMEOUT.toMillis(), (tNow - t0)
            );
            // ✅ 504 = backend took too long
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI provider took too long. Try again.");

        } catch (ExecutionException ee) {
            long tNow = System.currentTimeMillis();
            Throwable root = ee.getCause() != null ? ee.getCause() : ee;
            log.error(
                    "AI item-chat FAILED (aupId={}, itemId={}) total={}ms error={}",
                    aupId, itemId, (tNow - t0), root.toString(), root
            );
            // ✅ 502 = upstream AI provider failed
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider failed. Try again.");

        } catch (Exception ex) {
            long tNow = System.currentTimeMillis();
            log.error(
                    "AI item-chat ERROR (aupId={}, itemId={}) total={}ms error={}",
                    aupId, itemId, (tNow - t0), ex.toString(), ex
            );
            // ✅ avoid raw 500 with mystery
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI chat error. Try again.");
        }
    }

    /**
     * Runs ai.ask(prompt) on another thread and enforces a hard timeout.
     * This prevents hanging requests that keep the client "loading".
     */
    private String askWithTimeout(String prompt, Duration timeout)
            throws TimeoutException, ExecutionException, InterruptedException {

        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> ai.ask(prompt));

        try {
            return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true);
            throw te;
        }
    }

    private String v(Object x) {
        return x == null ? "N/A" : String.valueOf(x);
    }

    private String limit(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n...(truncated)";
    }

    private String buildAiContext(AiItemContextDTO x) {
        StringBuilder sb = new StringBuilder();

        sb.append("Item Context:\n");
        sb.append("- ID: ").append(v(x.id)).append("\n");
        sb.append("- AUP (Tenant): ").append(v(x.aupId)).append("\n");

        sb.append("- Name: ").append(v(x.name)).append("\n");
        sb.append("- Description: ").append(v(x.description)).append("\n");
        sb.append("- Type: ").append(v(x.itemTypeName)).append("\n");
        sb.append("- Status: ").append(v(x.status)).append("\n");

        sb.append("- Price: ").append(v(x.price)).append(" ").append(v(x.currencyCode)).append("\n");
        sb.append("- Effective Price: ").append(v(x.effectivePriceNow())).append(" ").append(v(x.currencyCode)).append("\n");

        sb.append("- Sale Price: ").append(v(x.salePrice)).append(" ").append(v(x.currencyCode)).append("\n");
        sb.append("- Sale Start: ").append(v(x.saleStart)).append("\n");
        sb.append("- Sale End: ").append(v(x.saleEnd)).append("\n");

        sb.append("- Taxable: ").append(v(x.taxable)).append("\n");
        sb.append("- Tax Class: ").append(v(x.taxClass)).append("\n");

        sb.append("- Stock: ").append(v(x.stock)).append("\n");
        sb.append("- Image URL: ").append(v(x.imageUrl)).append("\n");

        sb.append("- Currency: ").append(
                x.currencyCode != null ? x.currencyCode + " (" + v(x.currencySymbol) + ")" : "Not set (currency_id is null)"
        ).append("\n");

        sb.append("- Business: ").append(
                x.businessName != null ? x.businessName : "Not linked (business_id is null)"
        ).append("\n");

        if (Boolean.TRUE.equals(x.isProduct)) {
            sb.append("\nProduct Details:\n");
            sb.append("- SKU: ").append(v(x.sku)).append("\n");
            sb.append("- Product Type: ").append(v(x.productType)).append("\n");
            sb.append("- Virtual: ").append(v(x.virtualProduct)).append("\n");
            sb.append("- Downloadable: ").append(v(x.downloadable)).append("\n");
            sb.append("- Download URL: ").append(v(x.downloadUrl)).append("\n");
            sb.append("- External URL: ").append(v(x.externalUrl)).append("\n");
            sb.append("- Button Text: ").append(v(x.buttonText)).append("\n");
            sb.append("- Weight (kg): ").append(v(x.weightKg)).append("\n");
            sb.append("- Width (cm): ").append(v(x.widthCm)).append("\n");
            sb.append("- Height (cm): ").append(v(x.heightCm)).append("\n");
            sb.append("- Length (cm): ").append(v(x.lengthCm)).append("\n");
        }

        if (Boolean.TRUE.equals(x.isActivity)) {
            sb.append("\nActivity Details:\n");
            sb.append("- Location: ").append(v(x.location)).append("\n");
            sb.append("- Latitude: ").append(v(x.latitude)).append("\n");
            sb.append("- Longitude: ").append(v(x.longitude)).append("\n");
            sb.append("- Starts: ").append(v(x.startDatetime)).append("\n");
            sb.append("- Ends: ").append(v(x.endDatetime)).append("\n");
            sb.append("- Max Participants: ").append(v(x.maxParticipants)).append("\n");
        }

        return sb.toString();
    }

    private String buildPrompt(String userMessage, String itemContext) {
        return """
        You are a helpful shopping assistant for an e-commerce app.

        You have two knowledge sources:
        1) Item Context (database facts): name, price, SKU, stock, category, etc.
        2) General knowledge: how to use a product, typical care instructions, general advice.

        Rules:
        - Use Item Context for any factual fields (price, stock, SKU, availability, tax, business, etc).
        - For questions NOT answered by Item Context (e.g., "how to use", "what does it do", "tips", "routine"),
          answer using general best practices for that product type.
        - If the user has typos, infer intent and respond normally.
        - Never invent missing DB facts (e.g., don’t invent ingredients list if not provided).
        - Be friendly, clear, well-formatted with bullets.
        - Reply in the same language as the user message.

        Item Context:
        %s

        User message:
        %s
        """.formatted(itemContext, userMessage);
    }
}