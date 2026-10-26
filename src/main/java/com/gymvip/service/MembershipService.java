package com.gymvip.service;

import com.gymvip.dto.request.*;
import com.gymvip.dto.response.*;
import java.util.*;

public interface MembershipService {
    MembershipQuoteResponse quote(MembershipQuoteRequest request);
    MembershipSaleResponse sell(MembershipSaleRequest request, String key, String actor);
    List<MembershipSaleResponse> history(UUID client);
    List<String> paymentMethods();
}
