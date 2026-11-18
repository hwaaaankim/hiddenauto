package com.dev.HiddenBATHAuto.storefront.checkout;

import java.util.Arrays;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;

@Component
public class StorePreviewAccess {
    private final String companies;
    public StorePreviewAccess(@Value("${hiddenbath.storefront.preview-companies:}") String companies) {
        this.companies = companies;
    }

    public boolean allowed(Object principal) {
        if (!(principal instanceof PrincipalDetails account) || account.getCompany() == null
                || account.getCompany().getId() == null) return false;
        String id = account.getCompany().getId().toString();
        return Arrays.stream(companies.split(",")).map(String::strip).anyMatch(id::equals);
    }

    public long company(PrincipalDetails principal) {
        if (!allowed(principal)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return principal.getCompany().getId();
    }

    public String token(HttpSession session) {
        synchronized (session) {
            String key = StorePreviewAccess.class.getName();
            if (session.getAttribute(key) == null) session.setAttribute(key, UUID.randomUUID().toString());
            return (String) session.getAttribute(key);
        }
    }

    public void check(HttpSession session, String presented) {
        if (!token(session).equals(presented)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
