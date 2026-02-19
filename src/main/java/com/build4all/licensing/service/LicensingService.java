package com.build4all.licensing.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.licensing.domain.AppInfrastructure;
import com.build4all.licensing.domain.InfraType;
import com.build4all.licensing.domain.PlanCatalog;
import com.build4all.licensing.domain.PlanCode;
import com.build4all.licensing.domain.PlanUpgradeRequest;
import com.build4all.licensing.domain.PlanUpgradeRequestStatus;
import com.build4all.licensing.domain.Subscription;
import com.build4all.licensing.domain.SubscriptionStatus;
import com.build4all.licensing.repository.AppInfrastructureRepository;
import com.build4all.licensing.repository.PlanCatalogRepository;
import com.build4all.licensing.repository.PlanUpgradeRequestRepository;
import com.build4all.licensing.repository.SubscriptionRepository;
import com.build4all.user.repository.UsersRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.dto.UpdatePlanUsersAllowedRequest;
import com.build4all.licensing.dto.UpgradePlanRequest;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.time.LocalDate;

@Service
@Transactional
public class LicensingService {

    private final SubscriptionRepository subscriptionRepo;
    private final PlanCatalogRepository planRepo;
    private final AppInfrastructureRepository infraRepo;
    private final UsersRepository usersRepository;
    private final AdminUserProjectRepository aupRepo;
    private final PlanUpgradeRequestRepository upgradeReqRepo;


    public LicensingService(
    	    SubscriptionRepository subscriptionRepo,
    	    PlanCatalogRepository planRepo,
    	    AppInfrastructureRepository infraRepo,
    	    UsersRepository usersRepository,
    	    AdminUserProjectRepository aupRepo,
    	    PlanUpgradeRequestRepository upgradeReqRepo
    	) {
    	    this.subscriptionRepo = subscriptionRepo;
    	    this.planRepo = planRepo;
    	    this.infraRepo = infraRepo;
    	    this.usersRepository = usersRepository;
    	    this.aupRepo = aupRepo;
    	    this.upgradeReqRepo=upgradeReqRepo;
    	}


    /** Call this right after you create/save a new AdminUserProject (AUP). */
    public Subscription bootstrapFreeSubscription(AdminUserProject app) {
        PlanCatalog freePlan = planRepo.findById(PlanCode.FREE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PLAN_FREE_MISSING"));

        Subscription sub = new Subscription();
        sub.setApp(app);
        sub.setPlan(freePlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);

        LocalDate today = LocalDate.now();
        sub.setPeriodStart(today);
        sub.setPeriodEnd(today.plusMonths(freePlan.getBillingCycleMonths()));
        sub.setAutoRenew(true);

        return subscriptionRepo.save(sub);
    }

    /** ✅ new: Ensure app has at least one subscription (FREE by default). */
    public Subscription ensureSubscriptionExists(AdminUserProject app) {
        if (subscriptionRepo.existsByApp_Id(app.getId())) {
            return subscriptionRepo.findTopByApp_IdOrderByPeriodEndDesc(app.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SUB_LOOKUP_FAILED"));
        }
        return bootstrapFreeSubscription(app);
    }

    /** Returns ACTIVE subscription OR throws. Also auto-marks expired. */
    public Subscription requireActiveSubscription(Long aupId) {
        Subscription sub = subscriptionRepo
                .findTopByApp_IdAndStatusOrderByPeriodEndDesc(aupId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "NO_ACTIVE_SUBSCRIPTION"));

        if (LocalDate.now().isAfter(sub.getPeriodEnd())) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepo.save(sub);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "LICENSE_EXPIRED");
        }

        return sub;
    }

    /** ✅ new: One call to gate build/rebuild operations */
    public void requireBuildAllowed(Long aupId) {
        Subscription sub = requireActiveSubscription(aupId);

        // If dedicated plan => infra must be ready
        if (sub.getPlan().isRequiresDedicatedServer()) {
            assertDedicatedInfraReady(aupId);
        }
    }

    public void assertDedicatedInfraReady(Long aupId) {
        var infra = infraRepo.findById(aupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "DEDICATED_INFRA_MISSING"));

        if (infra.getDedicatedServer() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DEDICATED_SERVER_NOT_ASSIGNED");
        }

        var server = infra.getDedicatedServer();
        if (server.getStatus() != com.build4all.licensing.domain.ServerStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DEDICATED_SERVER_NOT_ACTIVE");
        }
    }

    public void requireUserSlotAvailable(Long aupId) {

        // ✅ re-use the canonical "active + expiry check"
        Subscription sub = requireActiveSubscription(aupId);

        Integer allowed = (sub.getUsersAllowedOverride() != null)
                ? sub.getUsersAllowedOverride()
                : sub.getPlan().getUsersAllowed();

        // ✅ unlimited plan
        if (allowed == null) return;

        long activeUsers = usersRepository
                .countByOwnerProject_IdAndStatus_NameIgnoreCase(aupId, "ACTIVE");

        if (activeUsers >= allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "USER_LIMIT_REACHED");
        }
    }
    
    public OwnerAppAccessResponse getOwnerDashboardAccess(Long aupId) {

        // 1) fetch latest subscription (even if not ACTIVE)
        var subOpt = subscriptionRepo.findTopByApp_IdOrderByPeriodEndDesc(aupId);

        OwnerAppAccessResponse res = new OwnerAppAccessResponse();

        if (subOpt.isEmpty()) {
            res.setCanAccessDashboard(false);
            res.setBlockingReason("NO_SUBSCRIPTION");
            return res;
        }

        Subscription sub = subOpt.get();

        LocalDate today = LocalDate.now();
        boolean expiredByDate = today.isAfter(sub.getPeriodEnd());

        // auto mark expired if needed
        if (expiredByDate && sub.getStatus() == SubscriptionStatus.ACTIVE) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepo.save(sub);
        }

        // plan info
        res.setPlanCode(sub.getPlan().getCode());
        res.setPlanName(sub.getPlan().getDisplayName());
        res.setSubscriptionStatus(sub.getStatus());
        res.setPeriodEnd(sub.getPeriodEnd());

        long daysLeft = ChronoUnit.DAYS.between(today, sub.getPeriodEnd());
        res.setDaysLeft(Math.max(daysLeft, 0));

        // 2) user limit calc
        Integer allowed = (sub.getUsersAllowedOverride() != null)
                ? sub.getUsersAllowedOverride()
                : sub.getPlan().getUsersAllowed();

        long activeUsers = usersRepository.countByOwnerProject_IdAndStatus_NameIgnoreCase(aupId, "ACTIVE");

        res.setUsersAllowed(allowed);
        res.setActiveUsers(activeUsers);

        boolean limitReached = false;
        if (allowed != null) {
       
            limitReached = activeUsers >= allowed;
            res.setUsersRemaining(Math.max((long) allowed - activeUsers, 0L));

        } else {
            res.setUsersRemaining(null); // unlimited
        }

        // 3) dedicated infra check
        boolean requiresDedicated = sub.getPlan().isRequiresDedicatedServer();
        res.setRequiresDedicatedServer(requiresDedicated);

        boolean infraReady = true;
        if (requiresDedicated) {
            infraReady = infraRepo.findById(aupId)
                    .map(i -> i.getDedicatedServer() != null)
                    .orElse(false);
        }
        res.setDedicatedInfraReady(infraReady);

        // 4) final decision
        boolean subscriptionOk = (sub.getStatus() == SubscriptionStatus.ACTIVE) && !expiredByDate;

        boolean canAccess = subscriptionOk && infraReady && !limitReached;
        res.setCanAccessDashboard(canAccess);

        if (!canAccess) {
            if (!subscriptionOk) {
                res.setBlockingReason(expiredByDate ? "LICENSE_EXPIRED" : "NO_ACTIVE_SUBSCRIPTION");
            } else if (!infraReady) {
                res.setBlockingReason("DEDICATED_SERVER_NOT_ASSIGNED");
            } else if (limitReached) {
                res.setBlockingReason("USER_LIMIT_REACHED");
            } else {
                res.setBlockingReason("ACCESS_BLOCKED");
            }
        }

        return res;
    }
    
    public Subscription upgradeSubscription(Long aupId, UpgradePlanRequest req) {

        if (req == null || req.getPlanCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planCode is required");
        }

        AdminUserProject app = aupRepo.findById(aupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND"));

        PlanCatalog newPlan = planRepo.findById(req.getPlanCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAN_NOT_FOUND"));

        // 1) Expire current ACTIVE subscription (if any)
        Optional<Subscription> currentOpt = subscriptionRepo
                .findTopByApp_IdAndStatusOrderByPeriodEndDesc(aupId, SubscriptionStatus.ACTIVE);

        if (currentOpt.isPresent()) {
            Subscription current = currentOpt.get();
            boolean ended = LocalDate.now().isAfter(current.getPeriodEnd());
            current.setStatus(ended ? SubscriptionStatus.EXPIRED : SubscriptionStatus.CANCELED);
            subscriptionRepo.save(current);
        }


        // 2) Create new subscription
        Subscription sub = new Subscription();
        sub.setApp(app);
        sub.setPlan(newPlan);
        sub.setStatus(SubscriptionStatus.ACTIVE);

        LocalDate today = LocalDate.now();
        sub.setPeriodStart(today);
        sub.setPeriodEnd(today.plusMonths(newPlan.getBillingCycleMonths()));
        sub.setAutoRenew(true);

        // optional override
        if (req.getUsersAllowedOverride() != null) {
            sub.setUsersAllowedOverride(req.getUsersAllowedOverride());
        }

        return subscriptionRepo.save(sub);
    }




 public PlanCatalog updatePlanUsersAllowed(PlanCode planCode, UpdatePlanUsersAllowedRequest req) {

     if (planCode == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planCode is required");
     }
     if (req == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
     }

     Integer allowed = req.getUsersAllowed();

     // ✅ null = unlimited (allowed)
     // ✅ disallow negative or zero (unless you really want "0 users" plan)
     if (allowed != null && allowed < 1) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersAllowed must be null (unlimited) or >= 1");
     }

     PlanCatalog plan = planRepo.findById(planCode)
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND"));

     plan.setUsersAllowed(allowed);
     return planRepo.save(plan);
 }


 @Transactional
 public PlanUpgradeRequest createUpgradeRequest(Long aupId, UpgradePlanRequest req, Long ownerUserId) {

     if (req == null || req.getPlanCode() == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "planCode is required");
     }

     // ensure app exists
     aupRepo.findById(aupId)
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AUP_NOT_FOUND"));

     // ensure plan exists
     planRepo.findById(req.getPlanCode())
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAN_NOT_FOUND"));

     // block duplicate pending
     if (upgradeReqRepo.findTopByAupIdAndStatusOrderByRequestedAtDesc(aupId, PlanUpgradeRequestStatus.PENDING).isPresent()) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "UPGRADE_REQUEST_ALREADY_PENDING");
     }

     PlanUpgradeRequest r = new PlanUpgradeRequest();
     r.setAupId(aupId);
     r.setRequestedPlanCode(req.getPlanCode());
     r.setUsersAllowedOverride(req.getUsersAllowedOverride());
     r.setStatus(PlanUpgradeRequestStatus.PENDING);
     r.setRequestedByUserId(ownerUserId);

     return upgradeReqRepo.save(r);
 }

 @Transactional
 public OwnerAppAccessResponse approveUpgradeRequest(Long requestId, Long superAdminUserId) {

     PlanUpgradeRequest r = upgradeReqRepo.findById(requestId)
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UPGRADE_REQUEST_NOT_FOUND"));

     if (r.getStatus() != PlanUpgradeRequestStatus.PENDING) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "REQUEST_NOT_PENDING");
     }

     // ✅ perform the actual upgrade using your existing method
     UpgradePlanRequest req = new UpgradePlanRequest();
     req.setPlanCode(r.getRequestedPlanCode());
     req.setUsersAllowedOverride(r.getUsersAllowedOverride());

     upgradeSubscription(r.getAupId(), req);

     // ✅ if dedicated plan, ensure infra row exists (optional but recommended)
     PlanCatalog plan = planRepo.findById(r.getRequestedPlanCode())
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAN_NOT_FOUND"));

     if (plan.isRequiresDedicatedServer()) {
         infraRepo.findById(r.getAupId()).orElseGet(() -> {
             AppInfrastructure infra = new AppInfrastructure();
             infra.setAupId(r.getAupId());
             infra.setInfraType(InfraType.DEDICATED);
             return infraRepo.save(infra);
         });
     }

     // mark approved
     r.setStatus(PlanUpgradeRequestStatus.APPROVED);
     r.setDecidedByUserId(superAdminUserId);
     r.setDecidedAt(java.time.LocalDateTime.now());
     upgradeReqRepo.save(r);

     return getOwnerDashboardAccess(r.getAupId());
 }

 @Transactional
 public PlanUpgradeRequest rejectUpgradeRequest(Long requestId, Long superAdminUserId, String note) {

     PlanUpgradeRequest r = upgradeReqRepo.findById(requestId)
             .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UPGRADE_REQUEST_NOT_FOUND"));

     if (r.getStatus() != PlanUpgradeRequestStatus.PENDING) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "REQUEST_NOT_PENDING");
     }

     r.setStatus(PlanUpgradeRequestStatus.REJECTED);
     r.setDecidedByUserId(superAdminUserId);
     r.setDecidedAt(java.time.LocalDateTime.now());
     r.setDecisionNote(note);

     return upgradeReqRepo.save(r);
 }

}
