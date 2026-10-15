package com.gymvip.service;

import com.gymvip.dto.request.CreatePlanRequest;
import com.gymvip.dto.request.UpdatePlanRequest;
import com.gymvip.dto.request.UpdatePlanStatusRequest;
import com.gymvip.dto.response.PlanResponse;
import com.gymvip.entity.PlanVersion;

import java.util.List;
import java.util.UUID;

public interface PlanService {
    PlanResponse createPlan(CreatePlanRequest request, String actorEmail);
    PlanResponse updatePlan(UUID id, UpdatePlanRequest request, String actorEmail);
    PlanResponse setStatus(UUID id, UpdatePlanStatusRequest request, String actorEmail);
    PlanResponse getPlanById(UUID id);
    List<PlanResponse> listPlans(String status);
    PlanVersion getLatestVersion(UUID planId);
}
