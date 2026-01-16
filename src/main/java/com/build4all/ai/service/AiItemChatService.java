package com.build4all.ai.service;

import com.build4all.ai.dto.AiItemChatRequest;
import com.build4all.ai.dto.AiItemContextDTO;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.features.activity.domain.Activity;
import com.build4all.features.activity.repository.ActivitiesRepository;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.security.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AiItemChatService {

    private final ItemRepository itemRepo;
    private final ProductRepository productRepo;
    private final ActivitiesRepository activitiesRepo;
    private final AiProviderService ai;


    public AiItemChatService(ItemRepository itemRepo,
                             ProductRepository productRepo,
                             ActivitiesRepository activitiesRepo,
                             AiProviderService ai) {
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.activitiesRepo = activitiesRepo;
        this.ai=ai;
        
    }

    public String handle(AiItemChatRequest req) {

        // 1) Tenant
        Long aupId = TenantContext.getOwnerProjectId();
        if (aupId == null) return "❌ X-Tenant not found";

        // 2) itemId
        Long itemId = req.getItemId();
        if (itemId == null) return "❌ itemId is required";

        // 3) Load base item with joins (tenant-safe)
        Item item = itemRepo.findByTenantWithJoins(aupId, itemId).orElse(null);
        if (item == null) return "❌ Item not found for this tenant";



        // 4) Build base DTO
        AiItemContextDTO ctx = AiItemContextDTO.fromItem(item);

        // 5) Fill subtype (safe)
        productRepo.findByIdAndTenant(itemId, aupId).ifPresent(ctx::applyProduct);
        activitiesRepo.findByIdAndTenant(itemId, aupId).ifPresent(ctx::applyActivity);

        
     // 6) Build context string
        String itemContext = buildAiContext(ctx);

        // 7) Get user message
        String userMsg = req.getMessage();
        if (userMsg == null || userMsg.isBlank()) {
            return "❌ message is required";
        }

        // 8) Build prompt
        String prompt = buildPrompt(userMsg, itemContext);

        // 9) Ask AI provider (dummy حاليا)
        return ai.ask(prompt);

    }

    private String v(Object x) { return x == null ? "N/A" : String.valueOf(x); }

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
